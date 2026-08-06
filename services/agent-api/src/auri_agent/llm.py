import logging
from datetime import datetime, timedelta, timezone
from typing import Literal

from langchain.agents import create_agent
from langchain.agents.structured_output import ToolStrategy
from langchain_openai import ChatOpenAI
from pydantic import BaseModel, Field

from .config import Settings
from .models import Task
from .observability import classify_provider_error, utc_timestamp
from .prompts import TASK_RIGIDITY_POLICY
from .tools import ground_waiting_parties, restore_explicit_waiting_parties


logger = logging.getLogger(__name__)
TZ = timezone(timedelta(hours=8), name="Asia/Shanghai")


class ExtractedTask(BaseModel):
    """LLM output only; it is normalised into the frozen public Task contract."""

    title: str = Field(min_length=1, max_length=80)
    scheduled_at: datetime | None = None
    location: str | None = Field(default=None, max_length=120)
    task_type: Literal["rigid", "flexible"] = Field(
        description="按系统责任后果规则判断；具体时间本身不能作为 rigid 依据"
    )
    priority: Literal["low", "medium", "high"]
    adjustable: bool = Field(description="通常 rigid=false、flexible=true；以任务真实可调整性为准")
    waiting_party: list[str] = Field(
        default_factory=list,
        max_length=10,
        description="只填写用户明确提到或上下文已经存在的等待方，不得臆造",
    )
    capability_tags: list[str] = Field(default_factory=list, max_length=10)


class TaskExtraction(BaseModel):
    tasks: list[ExtractedTask] = Field(min_length=1, max_length=8)


class TaskParser:
    """LangChain agent task parser with a deterministic, non-destructive fallback."""

    framework = "langchain"

    def __init__(self, settings: Settings):
        self.settings = settings
        self.agent = None
        if settings.llm_configured:
            model = ChatOpenAI(
                model=settings.openai_model,
                api_key=settings.openai_api_key,
                base_url=settings.openai_base_url.rstrip("/"),
                timeout=settings.openai_timeout_seconds,
                max_retries=1,
                temperature=0,
            )
            self.agent = create_agent(
                model=model,
                tools=[],
                system_prompt=self._system_prompt(),
                response_format=ToolStrategy(TaskExtraction),
                name="auri_task_parser",
            )
        self.last_mode = "fallback"
        self.last_success_at: str | None = None
        self.last_fallback_reason: str | None = None if self.agent is not None else "not_configured"
        self.last_error_code: str | None = None if self.agent is not None else "LLM_NOT_CONFIGURED"

    async def parse(self, text: str) -> list[Task]:
        if self.agent is not None:
            try:
                tasks = await self._parse_with_agent(text)
                self.last_mode = "langchain_agent"
                self.last_success_at = utc_timestamp()
                self.last_fallback_reason = None
                self.last_error_code = None
                return tasks
            except Exception as exc:  # network/provider failures must not break the demo
                logger.warning("LangChain task parsing fell back after %s", type(exc).__name__)
                self.last_error_code, self.last_fallback_reason = classify_provider_error(exc)
        self.last_mode = "fallback"
        return self._fallback(text)

    async def _parse_with_agent(self, text: str) -> list[Task]:
        state = await self.agent.ainvoke({"messages": [{"role": "user", "content": text}]})
        extraction = state.get("structured_response")
        if not isinstance(extraction, TaskExtraction):
            extraction = TaskExtraction.model_validate(extraction)
        tasks = self._normalise_agent_tasks(extraction.tasks, text)
        if "孩子" in text and not any(task.task_type == "rigid" for task in tasks):
            raise ValueError("agent omitted the rigid responsibility")
        if any(word in text for word in ("超市", "采购", "买菜")) and not any(
            "grocery_delivery" in task.capability_tags for task in tasks
        ):
            raise ValueError("agent omitted the grocery capability")
        restore_explicit_waiting_parties(tasks, text)
        return tasks

    def _normalise_agent_tasks(self, extracted: list[ExtractedTask], source_text: str) -> list[Task]:
        tasks: list[Task] = []
        for index, raw in enumerate(extracted):
            title = raw.title.strip()
            tags = list(dict.fromkeys(raw.capability_tags))
            task_type = raw.task_type
            priority = raw.priority
            adjustable = raw.adjustable

            if any(word in title for word in ("超市", "采购", "买菜")):
                task_type = "flexible"
                adjustable = True
                if "grocery_delivery" not in tags:
                    tags.append("grocery_delivery")
            if "孩子" in title:
                task_type = "rigid"
                priority = "high"
                adjustable = False

            tasks.append(
                Task(
                    task_id=f"task_agent_{index + 1}",
                    title=title,
                    scheduled_at=raw.scheduled_at,
                    location=raw.location,
                    task_type=task_type,
                    priority=priority,
                    adjustable=adjustable,
                    waiting_party=ground_waiting_parties(raw.waiting_party, source_text),
                    capability_tags=tags,
                )
            )
        return tasks

    def _system_prompt(self) -> str:
        today = datetime.now(TZ).date().isoformat()
        return (
            "你是 AURI 的任务理解 Agent。把用户输入拆成独立的通勤或生活责任，并按给定结构返回。"
            f"{TASK_RIGIDITY_POLICY}"
            "超市、买菜或采购任务必须包含 capability tag grocery_delivery。"
            "不要创建用户没有提到的孩子、地点、联系人或任务。"
            "你只负责理解任务，绝不能决定 L0-L3、权限、金额、确认归属或执行动作。"
            f"当前日期为 {today}，时区为 Asia/Shanghai。"
        )

    def _fallback(self, text: str) -> list[Task]:
        return fallback_tasks(text)


def fallback_tasks(text: str) -> list[Task]:
    """Deterministic task extraction shared by direct events and agent fallback."""
    today = datetime.now(TZ).date()
    pickup_time = datetime.combine(today, datetime.min.time(), TZ).replace(hour=18, minute=10)
    grocery_time = pickup_time.replace(hour=19, minute=30)
    tasks: list[Task] = []
    if "孩子" in text:
        tasks.append(
            Task(
                task_id="task_pickup_child",
                title="接孩子",
                scheduled_at=pickup_time,
                location="阳光小学",
                task_type="rigid",
                priority="high",
                adjustable=False,
                # Contact resolution belongs to an explicit task payload, an
                # authorised profile/contact adapter, or the LLM extraction.
                # A parser fallback must never fabricate message recipients.
                waiting_party=[],
                capability_tags=[],
            )
        )
    if any(word in text for word in ("超市", "买菜", "采购")):
        tasks.append(
            Task(
                task_id="task_grocery",
                title="超市采购",
                scheduled_at=grocery_time,
                task_type="flexible",
                priority="low",
                adjustable=True,
                waiting_party=[],
                capability_tags=["grocery_delivery"],
            )
        )
    if not tasks:
        tasks.append(
            Task(
                task_id="task_manual_review",
                title=text[:40].strip() or "待确认任务",
                task_type="flexible",
                priority="medium",
                adjustable=True,
                capability_tags=[],
            )
        )
    return tasks
