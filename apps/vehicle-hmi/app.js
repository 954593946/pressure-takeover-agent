const DRAFTS = {
  teacher: "老师您好，我可能会晚到约 18 分钟，正在赶往学校，请您帮忙照看一下孩子。我到达后会立即联系您。",
  family: "我这边会议延迟加上路况拥堵，接孩子可能晚一点。AURI 已把超市任务后置，我会按当前路线安全驾驶。"
};

const INITIAL = {
  stage: "idle",
  eta: "--:--",
  etaDelay: 0,
  duration: "--",
  distance: "--",
  windowState: "未建立",
  speed: 0,
  phone: "待创建任务",
  watch: "常态",
  console: "待机",
  kidTask: "等待创建",
  shopTask: "等待创建",
  draft: "teacher",
  draftReady: false,
  confirmationId: null,
  confirmed: [],
  events: []
};

const COPY = {
  idle: ["等待任务创建", "创建任务后，AURI 会识别刚性责任和弹性任务。", "暂无风险结论。", "手机端可语音创建任务", "无需确认", "等待风险成立"],
  task_created: ["任务已结构化", "接孩子为刚性责任，超市为弹性任务。", "已建立 18:10 接孩子责任窗口。", "手机端已完成语音任务创建", "无需确认", "继续监控出发窗口"],
  delayed: ["最晚出发窗口被压缩", "会议延迟 20 分钟，腕上设备进入黄色提醒。", "仍可能准时，但容错时间明显减少。", "AURI 已同步会议延迟", "无需确认", "继续观察 ETA"],
  warning: ["需要尽快出发", "系统计算最晚出发时间被压缩，腕上短震提醒。", "17:38 前需出发，否则接孩子存在迟到风险。", "腕上已提醒：尽快出发", "无需确认", "准备进入车辆"],
  vehicle: ["已进入驾驶模式", "车机 HMI 展示学校路线和当前 ETA。", "当前 ETA 正常，驾驶中只展示必要信息。", "可语音询问：“我还来得及吗？”", "无需确认", "车机保持低干扰"],
  risk: ["预计晚到 18 分钟", "ETA 变为 18:28，刚性责任窗口被突破。", "继续加速无法明显缩短时间。", "你可以说：“我还来得及吗？”", "等待方案生成", "Agent 正在分析"],
  takeover: ["压力源接管中", "已后置超市任务，并生成老师和家人消息草稿。", "继续加速无法明显缩短时间；超市已后置；消息已备好。", "可说：“确认发送”", "确认发送", "发送老师/家人消息"],
  done: ["问题已处理", "消息已模拟发送，手机、车机、腕上设备显示已处理。", "已处理，按当前速度开即可。", "AURI 已降低打扰", "已完成", "三端绿态同步"]
};

const ACTIONS = {
  idle: [["", "等待用户在手机端创建任务"]],
  task_created: [["done", "识别 18:10 接孩子为刚性责任"], ["done", "识别超市为弹性任务，可后置"], ["", "等待外部风险信号"]],
  delayed: [["done", "会议延迟写入 World State"], ["done", "重新计算最晚出发窗口"], ["pending", "腕上设备黄色短震提醒"]],
  warning: [["done", "生成 17:38 前需出发提醒"], ["done", "腕上设备短震"], ["pending", "等待进入车辆"]],
  vehicle: [["done", "vehicle_mode=true"], ["done", "车机展示阳光小学路线"], ["", "等待路况/ETA 变化"]],
  risk: [["done", "ETA 变为 18:28"], ["done", "判断预计晚到 18 分钟"], ["pending", "等待用户求助触发接管"]],
  takeover: [["done", "后置超市任务"], ["done", "生成老师消息草稿"], ["done", "生成家人消息草稿"], ["pending", "等待用户确认发送"]],
  done: [["done", "confirmation_id 校验通过"], ["done", "消息已模拟发送"], ["done", "手机、车机、腕上设备同步已处理"], ["done", "Agent 降低打扰"]]
};

const $ = (id) => document.querySelector(id);
const ui = {
  root: $(".screen"), speed: $("#speed"), headline: $("#headline"), eta: $("#eta"), etaNote: $("#etaNote"),
  windowState: $("#windowState"), modeChip: $("#modeChip"), phoneStatus: $("#phoneStatus"), phoneDetail: $("#phoneDetail"),
  watchStatus: $("#watchStatus"), watchDetail: $("#watchDetail"), consoleStatus: $("#consoleStatus"), kidTask: $("#kidTask"),
  shopTask: $("#shopTask"), kidTaskState: $("#kidTaskState"), shopTaskState: $("#shopTaskState"), agentTitle: $("#agentTitle"),
  agentText: $("#agentText"), realConclusion: $("#realConclusion"), riskBadge: $("#riskBadge"), actionState: $("#actionState"),
  actionList: $("#actionList"), draftState: $("#draftState"), draftBody: $("#draftBody"), tabs: $(".tabs"), syncPhone: $("#syncPhone"),
  syncWatch: $("#syncWatch"), syncWatchDot: $("#syncWatchDot"), syncCar: $("#syncCar"), voiceHint: $("#voiceHint"),
  confirmBtn: $("#confirmBtn"), confirmLabel: $("#confirmLabel"), confirmSub: $("#confirmSub"), timeline: $("#timeline"),
  speedLimit: $("#speedLimit"), lightCountdown: $("#lightCountdown"), turnDistance: $("#turnDistance"), routeProgress: $("#routeProgress"),
  amapRemain: $("#amapRemain"), amapDuration: $("#amapDuration"), amapArrival: $("#amapArrival")
};

let state = structuredClone(INITIAL);

function log(type) {
  state.events.unshift({ type, time: new Date().toLocaleTimeString("zh-CN", { hour12: false }) });
  state.events = state.events.slice(0, 6);
}

function setStage(stage) {
  state.stage = stage;
  log(stage);
  render();
  window.dispatchEvent(new CustomEvent("auri:web3-state", { detail: structuredClone(state) }));
}

function applyEvent(type) {
  if (type === "reset") {
    state = structuredClone(INITIAL);
    log("reset");
    render();
    return;
  }
  if (type === "create_task") {
    Object.assign(state, { eta: "18:10", etaDelay: 0, duration: "18", distance: "7.8", windowState: "安全", speed: 0, phone: "任务已创建", kidTask: "不可后置", shopTask: "可调整" });
    setStage("task_created");
  }
  if (type === "meeting_delayed") {
    Object.assign(state, { windowState: "压缩", watch: "黄灯短震", console: "会议延迟+20min" });
    setStage("delayed");
  }
  if (type === "departure_warning") {
    Object.assign(state, { windowState: "需出发", watch: "黄色提醒" });
    setStage("warning");
  }
  if (type === "enter_vehicle") {
    Object.assign(state, { speed: 42, eta: "18:10", duration: "18", distance: "7.8", console: "已进入车辆" });
    setStage("vehicle");
  }
  if (type === "traffic_jam") {
    Object.assign(state, { eta: "18:28", etaDelay: 18, duration: "36", distance: "7.8", windowState: "突破", watch: "红色风险", console: "拥堵加剧" });
    setStage("risk");
  }
  if (type === "stress_signal") {
    state.watch = "压力辅助";
    log("stress_signal");
    render();
  }
  if (type === "agent_takeover") {
    Object.assign(state, { draftReady: true, confirmationId: state.confirmationId || `confirm-${Date.now()}`, shopTask: "已后置" });
    setStage("takeover");
  }
  if (type === "confirm_send") confirmSend();
  if (type === "restore") setStage("done");
}

function confirmSend() {
  if (state.stage !== "takeover" || !state.confirmationId) return;
  if (!state.confirmed.includes(state.confirmationId)) state.confirmed.push(state.confirmationId);
  Object.assign(state, { phone: "草稿已发送", watch: "绿色已处理", console: "确认完成" });
  setStage("done");
}

function renderActions() {
  ui.actionList.innerHTML = "";
  (ACTIONS[state.stage] || ACTIONS.idle).forEach(([cls, text]) => {
    const li = document.createElement("li");
    li.textContent = text;
    if (cls) li.className = cls;
    ui.actionList.appendChild(li);
  });
}

function render() {
  const c = COPY[state.stage] || COPY.idle;
  ui.root.className = `screen state-${state.stage}`;
  ui.speed.textContent = state.speed || "--";
  ui.headline.textContent = c[1];
  ui.eta.textContent = state.eta;
  ui.etaNote.textContent = state.etaDelay ? `晚到 ${state.etaDelay} 分钟` : state.eta === "--:--" ? "等待路线" : "准时";
  ui.windowState.textContent = state.windowState;
  ui.modeChip.textContent = state.stage === "idle" || state.stage === "task_created" || state.stage === "delayed" || state.stage === "warning" ? "待进入车辆" : "驾驶模式";
  ui.phoneStatus.textContent = state.phone;
  ui.phoneDetail.textContent = state.stage === "done" ? "已发送" : "PWA 已连接";
  ui.watchStatus.textContent = state.watch;
  ui.watchDetail.textContent = state.stage === "done" ? "绿色反馈" : state.watch === "常态" ? "低干扰" : "震动/颜色";
  ui.consoleStatus.textContent = state.console;
  ui.kidTask.classList.toggle("active", state.kidTask !== "等待创建");
  ui.shopTask.classList.toggle("active", state.shopTask !== "等待创建");
  ui.kidTaskState.textContent = state.kidTask;
  ui.shopTaskState.textContent = state.shopTask;
  ui.agentTitle.textContent = c[0];
  ui.agentText.textContent = c[1];
  ui.realConclusion.textContent = c[2];
  ui.riskBadge.textContent = state.stage === "done" ? "已处理" : ["risk","takeover"].includes(state.stage) ? "高风险" : "低干扰";
  ui.actionState.textContent = state.stage === "takeover" ? "待确认" : state.stage === "done" ? "完成" : "进行中";
  ui.draftState.textContent = state.stage === "done" ? "已发送" : state.draftReady ? "等待确认" : "未生成";
  ui.draftBody.textContent = state.draftReady || state.stage === "done" ? DRAFTS[state.draft] : "风险成立后生成老师和家人的消息草稿。";
  ui.syncPhone.textContent = state.phone;
  ui.syncWatch.textContent = state.watch;
  ui.syncCar.textContent = state.stage === "done" ? "已处理" : state.stage === "idle" ? "待机" : "驾驶页同步";
  ui.syncWatchDot.className = state.stage === "done" ? "done" : state.watch === "常态" ? "ok" : "warn";
  ui.voiceHint.textContent = c[3];
  const canConfirm = state.stage === "takeover";
  ui.confirmBtn.disabled = !canConfirm;
  ui.confirmBtn.classList.toggle("enabled", canConfirm);
  ui.confirmLabel.textContent = c[4];
  ui.confirmSub.textContent = c[5];
  ui.speedLimit.textContent = ["vehicle","risk","takeover","done"].includes(state.stage) ? "40" : "--";
  ui.lightCountdown.textContent = ["risk","takeover","done"].includes(state.stage) ? "21" : "65";
  ui.turnDistance.textContent = ["vehicle","risk","takeover","done"].includes(state.stage) ? "1.5" : "--";
  ui.routeProgress.style.height = state.stage === "done" ? "76%" : ["risk","takeover"].includes(state.stage) ? "58%" : "35%";
  ui.amapRemain.textContent = state.distance === "--" ? "--" : `${state.distance} 公里`;
  ui.amapDuration.textContent = state.duration === "--" ? "--" : `${state.duration} 分钟`;
  ui.amapArrival.textContent = state.eta;
  renderActions();
  ui.timeline.innerHTML = state.events.map(e => `<div>${e.time} · ${e.type}</div>`).join("");
}

document.querySelector(".demo").addEventListener("click", (e) => {
  const btn = e.target.closest("button[data-event]");
  if (btn) applyEvent(btn.dataset.event);
});

ui.confirmBtn.addEventListener("click", confirmSend);

ui.tabs.addEventListener("click", (e) => {
  const btn = e.target.closest("button[data-draft]");
  if (!btn) return;
  state.draft = btn.dataset.draft;
  ui.tabs.querySelectorAll("button").forEach(b => b.classList.toggle("active", b === btn));
  render();
});

window.AURI_HMI_WEB3 = {
  dispatchEvent: applyEvent,
  getState: () => structuredClone(state),
  confirm: confirmSend,
  consumeWorldState(next) {
    Object.assign(state, next || {});
    render();
  }
};

render();
