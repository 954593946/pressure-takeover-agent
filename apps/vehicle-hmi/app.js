const DEFAULT_CONFIG = {
  apiBase: "http://127.0.0.1:8000",
  token: "",
  stream: true,
  pollIntervalMs: 3000
};

const PUBLIC_AGENT_API = "https://auri-agent-api.onrender.com";
const storedConfig = JSON.parse(localStorage.getItem("auri-hmi-config") || "{}");
const queryParams = new URLSearchParams(window.location.search);
const queryConfig = {
  ...(queryParams.get("apiBase") ? { apiBase: queryParams.get("apiBase") } : {}),
  ...(queryParams.get("streamUrl") ? { streamUrl: queryParams.get("streamUrl") } : {})
};
const windowConfig = window.AURI_CONFIG || {};
const hasExplicitStreamUrl = Boolean(windowConfig.streamUrl || queryConfig.streamUrl);
let CONFIG = normalizeConfig({ ...DEFAULT_CONFIG, ...storedConfig, ...windowConfig, ...queryConfig }, hasExplicitStreamUrl);

const DRAFTS = {
  teacher: "老师您好，我这边路况拥堵，预计会晚到约 18 分钟。请您帮忙照看一下孩子，我到达后会立即联系您。（模拟消息）",
  family: "我这边会议延迟加上路况拥堵，接孩子可能晚一点。AURI 已把超市任务转为配送方案，我会按当前路线安全驾驶。（模拟消息）"
};

const STAGE_VIEW = {
  off_vehicle_idle: ["idle", "等待任务创建", "手机端创建任务后，AURI 会识别刚性责任和弹性任务。", "暂无风险结论。", "手机端可语音创建任务", "无需确认", "等待风险成立", "待机"],
  pre_departure_warning: ["delayed", "最晚出发窗口被压缩", "会议延迟已写入 World State，腕上设备进入黄色提醒。", "仍可能准时，但容错时间明显减少。", "腕上已提醒，车机保持低干扰", "无需确认", "继续观察 ETA", "监控中"],
  handover_to_vehicle: ["warning", "正在交接到车机", "用户接近车辆，车机预加载驾驶页。", "准备进入驾驶模式，手机确认入口将失效。", "准备进入车辆", "无需确认", "等待车辆状态", "交接中"],
  vehicle_observation: ["vehicle", "已进入驾驶模式", "车机 HMI 展示学校路线和当前 ETA。", "当前按路线观察，驾驶中只展示必要信息。", "可语音询问：“我还来得及吗？”", "无需确认", "车机保持低干扰", "观察中"],
  takeover_L2: ["risk", "预计晚到 18 分钟", "刚性责任窗口被突破，等待用户明确求助后生成方案。", "继续加速无法明显缩短时间。", "你可以说：“我还来得及吗？”", "等待方案", "Agent 尚未生成确认项", "分析中"],
  takeover_L3: ["risk", "高负荷保护", "多源辅助信号显示驾驶负荷升高，非必要内容已暂停。", "车机只保留必要判断和安全确认。", "保持驾驶，AURI 正在处理", "等待方案", "高负荷保护中", "保护中"],
  planning: ["takeover", "压力源接管中", "Agent 正在保护接孩子任务，并准备消息与服务方案。", "继续加速无法明显缩短时间，正在处理现实后果。", "AURI 正在准备方案", "准备中", "等待确认项生成", "规划中"],
  service_prepared: ["takeover", "方案已准备", "消息和生活服务方案已准备，等待确认。", "消息与服务方案已备好。", "可说：“确认处理”", "确认处理", "等待车机确认", "待确认"],
  waiting_confirmation: ["takeover", "方案等待确认", "已后置超市任务，并生成老师、家人消息和模拟配送方案。", "继续加速无法明显缩短时间；消息和采购方案已备好。", "可说：“确认处理”", "确认处理", "执行消息和模拟订单", "待确认"],
  executing: ["takeover", "正在执行", "AURI 正在执行已确认的动作组。", "请继续安全驾驶，动作正在处理。", "正在处理", "执行中", "请勿重复操作", "执行中"],
  action_completed: ["done", "问题已处理", "消息已模拟发送，服务订单已模拟提交，三端同步已处理。", "已处理，按当前速度驾驶即可。", "AURI 已降低打扰", "已完成", "三端绿态同步", "完成"],
  cooldown: ["done", "低干扰恢复", "压力源已处理，AURI 进入冷却状态。", "后续详情停车后在手机端复盘。", "AURI 保持安静", "已完成", "等待停车复盘", "恢复"],
  parked_review: ["done", "停车后复盘", "主交互端回到手机，车机结束本次处理。", "请在手机端查看消息、订单和 Action Ledger。", "手机端复盘", "车机结束", "手机为主端", "复盘"],
  error: ["risk", "连接或状态异常", "请检查 Agent 服务或控制台事件。", "当前无法确认动作，请使用控制台或手机兜底。", "连接异常", "不可确认", "等待状态恢复", "错误"]
};

const EVENT_BUTTONS = {
  create_task: ["task.created", "mobile", { text: "今天18:10接孩子，之后去超市" }],
  meeting_delayed: ["meeting.overrun", "demo_console", { delay_minutes: 20 }],
  departure_warning: ["scene.approaching", "demo_console", {}],
  enter_vehicle: ["scene.vehicle_entered", "demo_console", {}],
  traffic_jam: ["traffic.updated", "demo_console", { eta: "2026-07-15T18:28:00+08:00", late_minutes: 18 }],
  stress_signal: ["wearable.signal", "wearable", { heart_rate: 120, confidence: 0.9 }],
  agent_takeover: ["user.utterance", "vehicle_hmi", { text: "我还来得及吗？帮我处理" }],
  restore: ["cooldown.elapsed", "demo_console", {}]
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
  amapRemain: $("#amapRemain"), amapDuration: $("#amapDuration"), amapArrival: $("#amapArrival"), configBtn: $("#configBtn"),
  configPanel: $("#configPanel"), configForm: $("#configForm"), closeConfig: $("#closeConfig"), configApiBase: $("#configApiBase"),
  configToken: $("#configToken"), usePublicAgent: $("#usePublicAgent"), useLocalAgent: $("#useLocalAgent")
};

let worldState = null;
let activeDraft = "teacher";
let lastRevision = -1;
let eventSeq = 0;
let pollTimer = null;
const timeline = [];

function normalizeConfig(config, useProvidedStreamUrl = false) {
  const apiBase = (config.apiBase || DEFAULT_CONFIG.apiBase).replace(/\/$/, "");
  return {
    ...config,
    apiBase,
    streamUrl: useProvidedStreamUrl && config.streamUrl ? config.streamUrl : `${apiBase}/v1/stream`,
    token: config.token || "",
    pollIntervalMs: Number(config.pollIntervalMs || DEFAULT_CONFIG.pollIntervalMs)
  };
}

function authHeaders(extra = {}) {
  return CONFIG.token ? { ...extra, "X-Agent-Token": CONFIG.token } : extra;
}

function eventId(type) {
  eventSeq += 1;
  return `hmi_${type.replaceAll(".", "_")}_${Date.now()}_${eventSeq}`;
}

function log(type, detail = "") {
  timeline.unshift({ time: new Date().toLocaleTimeString("zh-CN", { hour12: false }), type, detail });
  timeline.splice(6);
  ui.timeline.innerHTML = timeline.map((item) => `<div>${item.time} · ${item.type}${item.detail ? ` · ${item.detail}` : ""}</div>`).join("");
}

function setConnection(text) {
  ui.consoleStatus.textContent = text;
  log("connection", text);
}

function initConfigPanel() {
  ui.configApiBase.value = CONFIG.apiBase;
  ui.configToken.value = CONFIG.token;
}

function openConfig() {
  initConfigPanel();
  ui.configPanel.hidden = false;
}

function closeConfig() {
  ui.configPanel.hidden = true;
}

function saveConfig(apiBase, token) {
  const next = normalizeConfig({ apiBase: apiBase.trim(), token: token.trim(), stream: true });
  localStorage.setItem("auri-hmi-config", JSON.stringify({
    apiBase: next.apiBase,
    token: next.token,
    stream: true,
    pollIntervalMs: next.pollIntervalMs
  }));
  CONFIG = next;
  log("config", `saved ${CONFIG.apiBase}`);
  window.location.reload();
}

function friendlyError(error) {
  const message = error?.message || String(error);
  if (message.includes("NetworkError") || message.includes("Failed to fetch") || message.includes("Load failed")) {
    return `${message}；请确认 Agent 后端已启动、Agent API 地址正确，且后端 CORS 放行当前页面端口。`;
  }
  if (message.includes("UNAUTHORIZED") || message.includes("401")) {
    return `${message}；请填写正确 Team Token，或确认本地后端未开启共享访问。`;
  }
  return message;
}

async function apiFetch(path, options = {}) {
  const response = await fetch(`${CONFIG.apiBase}${path}`, {
    ...options,
    headers: authHeaders({
      Accept: "application/json",
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...(options.headers || {})
    })
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const code = data?.detail?.code || response.status;
    throw new Error(`${code}: ${data?.detail?.message || response.statusText}`);
  }
  return data;
}

async function loadState(reason = "load") {
  const state = await apiFetch("/v1/state");
  consumeWorldState(state, reason);
}

function consumeWorldState(next, reason = "state") {
  if (!next || next.schema_version !== "0.2.0") return;
  if (worldState && next.session_id === worldState.session_id && next.revision <= lastRevision) return;
  worldState = next;
  lastRevision = next.revision;
  log(reason, `${next.stage} r${next.revision}`);
  render();
}

async function submitEvent(definition) {
  if (!worldState) await loadState("before-event");
  const [type, source, payload] = definition;
  const accepted = await apiFetch("/v1/event", {
    method: "POST",
    body: JSON.stringify({
      schema_version: "0.2.0",
      event_id: eventId(type),
      session_id: worldState.session_id,
      type,
      source,
      timestamp: new Date().toISOString(),
      payload
    })
  });
  consumeWorldState(accepted.state, accepted.duplicate ? "duplicate-event" : "event");
}

async function confirmAction(inputMode = "button") {
  if (!worldState?.confirmation) return;
  const state = await apiFetch("/v1/confirm", {
    method: "POST",
    body: JSON.stringify({
      confirmation_id: worldState.confirmation.confirmation_id,
      decision: "accept",
      confirmed_by: "vehicle_hmi",
      input_mode: inputMode
    })
  });
  consumeWorldState(state, "confirm");
}

async function resetSession() {
  const state = await apiFetch("/v1/session/reset", {
    method: "POST",
    body: JSON.stringify({ scenario_id: "happy-path" })
  });
  consumeWorldState(state, "reset");
}

async function connectStream() {
  if (!CONFIG.stream) return;
  try {
    const response = await fetch(CONFIG.streamUrl, {
      headers: authHeaders({ Accept: "text/event-stream" })
    });
    if (!response.ok || !response.body) throw new Error(`stream ${response.status}`);
    setConnection("状态流已连接");
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const chunks = buffer.split("\n\n");
      buffer = chunks.pop() || "";
      chunks.forEach(parseStreamChunk);
    }
  } catch (error) {
    setConnection(`状态流断开：${friendlyError(error)}`);
    setTimeout(connectStream, 2500);
  }
}

function startPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(() => {
    loadState("poll").catch((error) => log("poll-error", friendlyError(error)));
  }, CONFIG.pollIntervalMs);
}

function parseStreamChunk(chunk) {
  const dataLine = chunk.split("\n").find((line) => line.startsWith("data: "));
  if (!dataLine) return;
  try {
    consumeWorldState(JSON.parse(dataLine.slice(6)), "stream");
  } catch (error) {
    log("stream-parse-error", friendlyError(error));
  }
}

function stageView() {
  return STAGE_VIEW[worldState?.stage] || STAGE_VIEW.error;
}

function formatTime(value) {
  if (!value) return "--:--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--:--";
  return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", hour12: false });
}

function pickupTask() {
  return (worldState?.tasks || []).find((task) => task.task_type === "rigid" || task.task_id.includes("pickup"));
}

function groceryTask() {
  return (worldState?.tasks || []).find((task) => task.capability_tags?.includes("grocery_delivery") || task.task_type === "flexible");
}

function actionText(action) {
  const prefix = action.type === "message" ? "消息" : action.type === "service_order" ? "模拟订单" : "任务调整";
  const status = {
    awaiting_confirmation: "待确认",
    completed: "已完成",
    blocked: "已阻断",
    failed: "失败",
    planned: "已规划",
    ready: "已准备"
  }[action.status] || action.status;
  return `${prefix} · ${action.target} · ${status}：${action.summary}`;
}

function renderActions() {
  const actions = worldState?.actions || [];
  if (!actions.length) {
    ui.actionList.innerHTML = "<li>等待 Agent 生成动作组</li>";
    return;
  }
  ui.actionList.innerHTML = actions.map((action) => {
    const cls = action.status === "completed" ? "done" : action.status === "awaiting_confirmation" ? "pending" : "";
    return `<li class="${cls}">${actionText(action)}</li>`;
  }).join("");
}

function renderDraft() {
  const messageActions = (worldState?.actions || []).filter((action) => action.type === "message");
  if (!messageActions.length) {
    ui.draftState.textContent = "未生成";
    ui.draftBody.textContent = "风险成立后生成老师和家人的模拟消息草稿。";
    return;
  }
  const current = activeDraft === "family"
    ? messageActions.find((action) => action.target.includes("家")) || messageActions[1] || messageActions[0]
    : messageActions.find((action) => action.target.includes("老师")) || messageActions[0];
  ui.draftState.textContent = current.status === "completed" ? "已模拟发送" : "等待确认";
  ui.draftBody.textContent = `${current.summary}。${activeDraft === "family" ? DRAFTS.family : DRAFTS.teacher}`;
}

function render() {
  const view = stageView();
  const [className, title, text, conclusion, voice, confirmLabel, confirmSub, actionState] = view;
  const risk = worldState?.risk || { pressure_level: "L0", late_minutes: 0 };
  const eta = formatTime(worldState?.eta);
  const pickup = pickupTask();
  const grocery = groceryTask();
  const canConfirm = worldState?.primary_surface === "vehicle_hmi"
    && worldState?.confirmation?.owner_surface === "vehicle_hmi"
    && worldState?.confirmation?.status === "pending";
  const driving = ["driving", "high_load_driving"].includes(worldState?.scene);
  const order = worldState?.service_orders?.[0];

  ui.root.className = `screen state-${className}`;
  ui.speed.textContent = driving ? "42" : "--";
  ui.headline.textContent = worldState?.output?.conclusion || text;
  ui.eta.textContent = eta;
  ui.etaNote.textContent = risk.late_minutes > 0 ? `晚到 ${risk.late_minutes} 分钟` : eta === "--:--" ? "等待路线" : "准时";
  ui.windowState.textContent = risk.late_minutes > 0 ? "突破" : worldState?.stage === "pre_departure_warning" ? "压缩" : pickup ? "已建立" : "未建立";
  ui.modeChip.textContent = worldState?.primary_surface === "vehicle_hmi" ? "驾驶模式" : "手机为主端";
  ui.phoneStatus.textContent = worldState?.primary_surface === "mobile" ? "主端" : worldState?.stage === "action_completed" ? "已同步" : "只读同步";
  ui.phoneDetail.textContent = pickup ? "任务已创建" : "等待任务";
  ui.watchStatus.textContent = worldState?.wearable?.text || "AURI 就绪";
  ui.watchDetail.textContent = `${worldState?.wearable?.mode || "idle"} · ${worldState?.wearable?.haptic || "none"}`;
  ui.consoleStatus.textContent = `r${worldState?.revision ?? 0} · ${worldState?.stage || "未连接"}`;
  ui.kidTask.classList.toggle("active", Boolean(pickup));
  ui.shopTask.classList.toggle("active", Boolean(grocery));
  ui.kidTaskState.textContent = pickup ? (pickup.adjustable ? "可调整" : "不可后置") : "等待创建";
  ui.shopTaskState.textContent = grocery ? (grocery.status === "rescheduled" ? "已后置" : "可调整") : "等待创建";
  ui.agentTitle.textContent = title;
  ui.agentText.textContent = text;
  ui.realConclusion.textContent = worldState?.output?.conclusion || (order?.error_code ? `服务异常：${order.error_code}，驾驶中不展开复杂选择。` : conclusion);
  ui.riskBadge.textContent = ["action_completed", "cooldown"].includes(worldState?.stage) ? "已处理" : risk.pressure_level || "L0";
  ui.actionState.textContent = actionState;
  renderActions();
  renderDraft();
  ui.syncPhone.textContent = worldState?.primary_surface === "mobile" ? "主端" : "同步";
  ui.syncWatch.textContent = worldState?.wearable?.mode || "idle";
  ui.syncCar.textContent = worldState?.primary_surface === "vehicle_hmi" ? "主端" : "只读";
  ui.syncWatchDot.className = worldState?.wearable?.mode === "completed" ? "done" : worldState?.wearable?.mode === "warning" ? "warn" : "ok";
  ui.voiceHint.textContent = voice;
  ui.confirmBtn.disabled = !canConfirm;
  ui.confirmBtn.classList.toggle("enabled", canConfirm);
  ui.confirmLabel.textContent = confirmLabel;
  ui.confirmSub.textContent = canConfirm ? confirmSub : (worldState?.confirmation?.owner_surface && worldState.confirmation.owner_surface !== "vehicle_hmi" ? "确认入口不在车机" : confirmSub);
  ui.speedLimit.textContent = driving ? "40" : "--";
  ui.lightCountdown.textContent = risk.late_minutes > 0 ? "21" : "65";
  ui.turnDistance.textContent = driving ? "1.5" : "--";
  ui.routeProgress.style.height = worldState?.stage === "action_completed" ? "76%" : risk.late_minutes > 0 ? "58%" : "35%";
  ui.amapRemain.textContent = driving ? "7.8 公里" : "--";
  ui.amapDuration.textContent = risk.late_minutes > 0 ? "36 分钟" : driving ? "18 分钟" : "--";
  ui.amapArrival.textContent = eta;
}

document.querySelector(".demo")?.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-event]");
  if (!button) return;
  button.disabled = true;
  try {
    if (button.dataset.event === "confirm_send") await confirmAction("button");
    else if (button.dataset.event === "reset") await resetSession();
    else await submitEvent(EVENT_BUTTONS[button.dataset.event]);
  } catch (error) {
    log("error", friendlyError(error));
    setConnection(`错误：${friendlyError(error)}`);
  } finally {
    button.disabled = false;
  }
});

ui.confirmBtn.addEventListener("click", async () => {
  ui.confirmBtn.disabled = true;
  try {
    await confirmAction("button");
  } catch (error) {
    log("confirm-error", friendlyError(error));
  } finally {
    render();
  }
});

ui.tabs.addEventListener("click", (event) => {
  const button = event.target.closest("button[data-draft]");
  if (!button) return;
  activeDraft = button.dataset.draft;
  ui.tabs.querySelectorAll("button").forEach((item) => item.classList.toggle("active", item === button));
  renderDraft();
});

ui.configBtn.addEventListener("click", openConfig);
ui.closeConfig.addEventListener("click", closeConfig);
ui.configPanel.addEventListener("click", (event) => {
  if (event.target === ui.configPanel) closeConfig();
});
ui.usePublicAgent.addEventListener("click", () => {
  ui.configApiBase.value = PUBLIC_AGENT_API;
});
ui.useLocalAgent.addEventListener("click", () => {
  ui.configApiBase.value = DEFAULT_CONFIG.apiBase;
  ui.configToken.value = "";
});
ui.configForm.addEventListener("submit", (event) => {
  event.preventDefault();
  saveConfig(ui.configApiBase.value, ui.configToken.value);
});

window.AURI_HMI = {
  loadState,
  submitEvent,
  confirm: confirmAction,
  reset: resetSession,
  consumeWorldState,
  getState: () => structuredClone(worldState)
};

render();
loadState("load").then(() => {
  connectStream();
  startPolling();
}).catch((error) => {
  setConnection(`连接失败：${friendlyError(error)}`);
  startPolling();
  render();
});
