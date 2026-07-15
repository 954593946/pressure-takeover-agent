import json
import logging
from datetime import datetime, timedelta, timezone

from openai import AsyncOpenAI
from .config import Settings
from .models import Task


logger = logging.getLogger(__name__)
TZ = timezone(timedelta(hours=8), name="Asia/Shanghai")


class TaskParser:
    """LLM-backed task parser with deterministic demo fallback."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = (
            AsyncOpenAI(
                api_key=settings.openai_api_key,
                base_url=settings.openai_base_url.rstrip("/"),
                timeout=settings.openai_timeout_seconds,
                max_retries=1,
            )
            if settings.llm_configured
            else None
        )
        self.last_mode = "fallback"

    async def parse(self, text: str) -> list[Task]:
        if self.client is not None:
            try:
                tasks = await self._parse_with_provider(text)
                self.last_mode = "provider"
                return tasks
            except Exception as exc:  # provider compatibility and network failures must not break the demo
                logger.warning("LLM task parsing fell back after %s", type(exc).__name__)
        self.last_mode = "fallback"
        return self._fallback(text)

    async def _parse_with_provider(self, text: str) -> list[Task]:
        today = datetime.now(TZ).date().isoformat()
        messages = [
            {
                "role": "system",
                "content": (
                    "You extract Chinese commute responsibilities into JSON. Return only an object with a tasks array. "
                    "Each task requires task_id,title,scheduled_at,location,task_type,priority,adjustable,status,waiting_party,capability_tags. "
                    "task_type is rigid or flexible. Grocery errands must include grocery_delivery. "
                    "Never decide pressure levels, permissions, money, or execution. Today is " + today + "."
                ),
            },
            {"role": "user", "content": text},
        ]
        try:
            response = await self.client.chat.completions.create(
                model=self.settings.openai_model,
                messages=messages,
                response_format={"type": "json_object"},
            )
        except Exception:
            response = await self.client.chat.completions.create(
                model=self.settings.openai_model,
                messages=messages,
            )
        content = (response.choices[0].message.content or "").strip()
        if content.startswith("```"):
            content = content.removeprefix("```json").removeprefix("```").removesuffix("```").strip()
        raw = json.loads(content)
        raw_tasks = raw if isinstance(raw, list) else raw.get("tasks", [])
        if not isinstance(raw_tasks, list) or not raw_tasks:
            raise ValueError("provider returned no tasks")
        tasks = self._normalise_provider_tasks(text, raw_tasks)
        if "孩子" in text and not any(task.task_type == "rigid" for task in tasks):
            raise ValueError("provider omitted the rigid responsibility")
        if ("超市" in text or "采购" in text) and not any("grocery_delivery" in task.capability_tags for task in tasks):
            raise ValueError("provider omitted the grocery capability")
        return tasks

    def _normalise_provider_tasks(self, text: str, raw_tasks: list[object]) -> list[Task]:
        """Accept minor provider field differences, then enforce the public Task contract."""
        fallback = self._fallback(text)
        normalised: list[Task] = []
        aliases = {
            "id": "task_id",
            "type": "task_type",
            "time": "scheduled_at",
            "waiting_parties": "waiting_party",
            "capabilities": "capability_tags",
        }
        allowed = set(Task.model_fields)
        for index, raw_item in enumerate(raw_tasks):
            if not isinstance(raw_item, dict):
                continue
            title = str(raw_item.get("title", ""))
            base = next((task for task in fallback if task.title and task.title in title), fallback[min(index, len(fallback) - 1)])
            candidate = base.model_dump()
            for raw_key, value in raw_item.items():
                key = aliases.get(raw_key, raw_key)
                if key in allowed and value is not None:
                    candidate[key] = value
            try:
                task = Task.model_validate(candidate)
            except Exception:
                task = base
            if "超市" in task.title or "采购" in task.title or "买菜" in task.title:
                task.task_type = "flexible"
                task.adjustable = True
                if "grocery_delivery" not in task.capability_tags:
                    task.capability_tags.append("grocery_delivery")
            if "孩子" in task.title:
                task.task_type = "rigid"
                task.priority = "high"
                task.adjustable = False
            normalised.append(task)
        if not normalised:
            raise ValueError("provider tasks could not be normalised")
        return normalised

    def _fallback(self, text: str) -> list[Task]:
        today = datetime.now(TZ).date()
        pickup_time = datetime.combine(today, datetime.min.time(), TZ).replace(hour=18, minute=10)
        grocery_time = pickup_time.replace(hour=19, minute=30)
        tasks: list[Task] = []
        if "孩子" in text or "接" in text:
            tasks.append(
                Task(
                    task_id="task_pickup_child",
                    title="接孩子",
                    scheduled_at=pickup_time,
                    location="阳光小学",
                    task_type="rigid",
                    priority="high",
                    adjustable=False,
                    waiting_party=["王老师", "家人"],
                    capability_tags=[],
                )
            )
        if "超市" in text or "买菜" in text or "采购" in text:
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
                    title=text[:40] or "待确认任务",
                    task_type="flexible",
                    priority="medium",
                    adjustable=True,
                    capability_tags=[],
                )
            )
        return tasks
