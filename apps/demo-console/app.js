const DEFAULT_CONFIG = {
  apiBase: "https://auri-agent-api.onrender.com",
  stream: true
};

const PUBLIC_AGENT_API = "https://auri-agent-api.onrender.com";
const LEGACY_AGENT_API = "https://auri-langchain-agent-api.onrender.com";
const LOCAL_AGENT_API = "http://127.0.0.1:8000";
const DEMO_PRESET_TASK_TEXT = "今天18:10接孩子，之后去超市";
const DEMO_TRAFFIC_DELAY_MINUTES = 18;
const API_TIMEOUT_MS = 45_000;
const GET_RETRY_DELAYS_MS = [0, 900, 2200];
const APP_CONFIG_KEY = "auri-demo-console-config";
const SHARED_CONFIG_KEY = "auri-shared-agent-config-v1";

function readStoredConfig(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || "{}");
  } catch (_error) {
    return {};
  }
}

const storedConfigRaw = readStoredConfig(APP_CONFIG_KEY);
const storedConfig = storedConfigRaw.apiBase === LEGACY_AGENT_API && storedConfigRaw.configVersion !== 2
  ? { ...storedConfigRaw, apiBase: PUBLIC_AGENT_API }
  : storedConfigRaw;
const sharedConfig = readStoredConfig(SHARED_CONFIG_KEY);
const CONFIG = {
  ...DEFAULT_CONFIG,
  ...storedConfig,
  ...(sharedConfig.apiBase ? { apiBase: sharedConfig.apiBase, token: sharedConfig.token || "" } : {}),
  ...(window.AURI_CONFIG || {})
};

const ACTIONS = {
  presetTask: ["task.created", "mobile", null],
  meeting: ["meeting.overrun", "demo_console", { delay_minutes: 20 }],
  approach: ["scene.approaching", "demo_console", {}],
  vehicle: ["scene.vehicle_entered", "demo_console", {}],
  traffic: ["traffic.updated", "demo_console", null],
  stress: ["wearable.signal", "wearable", { heart_rate: 120, confidence: 0.9 }],
  hardBrake: ["driving.signal", "vehicle_hmi", { harsh_brake: true, acceleration_variance: "high", confidence: 0.8 }],
  utterance: ["user.utterance", "mobile", { text: "我还来得及吗？帮我处理", input_mode: "voice" }],
  serviceSuccess: ["service.mock.config", "demo_console", { mode: "success" }],
  serviceStock: ["service.mock.config", "demo_console", { mode: "out_of_stock" }],
  serviceBudget: ["service.mock.config", "demo_console", { mode: "over_budget" }],
  cooldown: ["cooldown.elapsed", "demo_console", {}],
  parked: ["scene.parked", "demo_console", {}]
};

function shanghaiDate() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function presetTaskPayload() {
  const date = shanghaiDate();
  return {
    text: DEMO_PRESET_TASK_TEXT,
    tasks: [
      {
        task_id: "task_pickup_child",
        title: "接孩子",
        scheduled_at: `${date}T18:10:00+08:00`,
        location: "阳光小学",
        task_type: "rigid",
        priority: "high",
        adjustable: false,
        status: "pending",
        waiting_party: ["王老师", "孩子妈妈"],
        capability_tags: []
      },
      {
        task_id: "task_grocery",
        title: "超市采购",
        scheduled_at: `${date}T19:30:00+08:00`,
        location: null,
        task_type: "flexible",
        priority: "low",
        adjustable: true,
        status: "pending",
        waiting_party: [],
        capability_tags: ["grocery_delivery"]
      }
    ]
  };
}

function trafficPayload(state) {
  const tasks = Array.isArray(state?.tasks) ? state.tasks : [];
  const referenceTask = tasks.find((task) => task.task_type === "rigid" && task.scheduled_at)
    || tasks.find((task) => task.scheduled_at);
  const scheduledAt = Date.parse(referenceTask?.scheduled_at || "");

  // ETA is anchored to the current Agent task, never a fixed demo clock value.
  if (Number.isFinite(scheduledAt)) {
    return {
      eta: new Date(scheduledAt + DEMO_TRAFFIC_DELAY_MINUTES * 60_000).toISOString(),
      late_minutes: DEMO_TRAFFIC_DELAY_MINUTES
    };
  }
  return { late_minutes: DEMO_TRAFFIC_DELAY_MINUTES };
}

function eventDefinition(actionKey) {
  const [type, source, payload] = ACTIONS[actionKey];
  if (actionKey === "traffic") return [type, source, trafficPayload(worldState)];
  if (actionKey === "presetTask") return [type, source, presetTaskPayload()];
  return [type, source, payload];
}

const $ = (selector) => document.querySelector(selector);
const ui = {
  apiBase: $("#apiBase"),
  token: $("#token"),
  usePublicAgent: $("#usePublicAgent"),
  useLegacyAgent: $("#useLegacyAgent"),
  useLocalAgent: $("#useLocalAgent"),
  saveConfig: $("#saveConfig"),
  preflightBtn: $("#preflightBtn"),
  connectBtn: $("#connectBtn"),
  runCurrentStep: $("#runCurrentStep"),
  resetBtn: $("#resetBtn"),
  toggleTech: $("#toggleTech"),
  technicalActions: $("#technicalActions"),
  directorStage: $("#directorStage"),
  nextStepHint: $("#nextStepHint"),
  hostCue: $("#hostCue"),
  vehicleState: $("#vehicleState"),
  ledger: $("#ledger"),
  copyLog: $("#copyLog"),
  clearLog: $("#clearLog"),
  sessionId: $("#sessionId"),
  revision: $("#revision"),
  stage: $("#stage"),
  scene: $("#scene"),
  pressure: $("#pressure"),
  late: $("#late"),
  surface: $("#surface"),
  confirmOwner: $("#confirmOwner"),
  agentHealth: $("#agentHealth"),
  agentTools: $("#agentTools"),
  syncMode: $("#syncMode"),
  syncDetail: $("#syncDetail"),
  tasks: $("#tasks"),
  actions: $("#actions"),
  eventLog: $("#eventLog")
};

let worldState = null;
let lastRevision = -1;
let eventSeq = 0;
let streamAbort = null;
let streamRetryTimer = null;
let pollTimer = null;
let lastHealth = null;
let syncMode = "disconnected";
let mobileTaskSyncAcknowledged = false;
const stableEventIds = new Map();

const SCRIPT_STEPS = [
  { key: "waitTask", stage: "同步手机任务", cue: "先展示空任务状态，再由手机语音创建任务并同步到共享 World State。" },
  { key: "meeting", stage: "会议延迟", cue: "会议延迟压缩出发窗口，腕上进入黄色提醒。" },
  { key: "approach", stage: "接近车辆", cue: "用户离开办公室靠近车辆，准备交接到车机。" },
  { key: "vehicle", stage: "进入车辆", cue: "主交互端切到车机，手机进入只读 Companion。" },
  { key: "traffic", stage: "拥堵加剧", cue: "按当前刚性任务时间计算 ETA，并展示预计晚到分钟数。" },
  { key: "stress", stage: "压力辅助信号", cue: "辅助信号只提升解释性，不直接决定情绪。" },
  { key: "utterance", stage: "手机语音求助", cue: "用户在手机端说我还来得及吗，转写同步到车机后 Agent 准备动作组。" },
  { key: "confirm", stage: "确认发送", cue: "车机一次确认，消息和模拟订单幂等执行。" },
  { key: "cooldown", stage: "低干扰恢复", cue: "处理完成后降低打扰。" },
  { key: "parked", stage: "停车复盘", cue: "停车后主端回到手机查看完整复盘。" }
];

function initConfig() {
  ui.apiBase.value = CONFIG.apiBase;
  ui.token.value = CONFIG.token || "";
}

function authHeaders(extra = {}) {
  return CONFIG.token ? { ...extra, "X-Agent-Token": CONFIG.token } : extra;
}

function log(kind, message, detail = "") {
  const row = document.createElement("div");
  row.className = `log-row ${kind === "error" ? "error" : ""}`;
  row.dataset.raw = `${kind} ${message} ${detail}`;
  const time = document.createElement("span");
  const label = document.createElement("strong");
  const content = document.createElement("code");
  time.textContent = new Date().toLocaleTimeString("zh-CN", { hour12: false });
  label.textContent = kind;
  content.textContent = `${message}${detail ? ` · ${detail}` : ""}`;
  row.append(time, label, content);
  ui.eventLog.prepend(row);
  while (ui.eventLog.children.length > 80) ui.eventLog.lastElementChild.remove();
}

function friendlyError(error) {
  const message = error?.message || String(error);
  if (message.includes("请求超时")) {
    return `${message}；公网 Agent 可能正在冷启动，请稍后重试。`;
  }
  if (message.includes("NetworkError") || message.includes("Failed to fetch") || message.includes("Load failed")) {
    return `${message}；请确认 Agent 后端已启动、Agent API 地址正确，且后端 CORS 放行当前页面端口。`;
  }
  if (message.includes("UNAUTHORIZED") || message.includes("401")) {
    return `${message}；请填写正确 Team Token，或确认本地后端未开启共享访问。`;
  }
  return message;
}

function wait(milliseconds) {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}

async function fetchWithTimeout(url, options = {}, timeoutMs = API_TIMEOUT_MS) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } catch (error) {
    if (error?.name === "AbortError") throw new Error(`请求超时（${Math.round(timeoutMs / 1000)} 秒）`);
    throw error;
  } finally {
    window.clearTimeout(timer);
  }
}

async function fetchWithGetRetry(url, options = {}) {
  let lastError = null;
  for (let attempt = 0; attempt < GET_RETRY_DELAYS_MS.length; attempt += 1) {
    if (GET_RETRY_DELAYS_MS[attempt]) await wait(GET_RETRY_DELAYS_MS[attempt]);
    try {
      const response = await fetchWithTimeout(url, options);
      if ([502, 503, 504].includes(response.status) && attempt < GET_RETRY_DELAYS_MS.length - 1) {
        lastError = new Error(`Agent 暂不可用（HTTP ${response.status}）`);
        log("retry", new URL(url).pathname, `HTTP ${response.status} · 第 ${attempt + 2} 次连接`);
        continue;
      }
      return response;
    } catch (error) {
      lastError = error;
      if (attempt < GET_RETRY_DELAYS_MS.length - 1) {
        log("retry", new URL(url).pathname, `第 ${attempt + 2} 次连接`);
      }
    }
  }
  throw lastError;
}

async function apiFetch(path, options = {}) {
  const { returnMeta = false, ...fetchOptions } = options;
  const startedAt = performance.now();
  const requestOptions = {
    ...fetchOptions,
    headers: authHeaders({
      Accept: "application/json",
      ...(fetchOptions.body ? { "Content-Type": "application/json" } : {}),
      ...(fetchOptions.headers || {})
    })
  };
  const method = String(fetchOptions.method || "GET").toUpperCase();
  const response = method === "GET"
    ? await fetchWithGetRetry(`${CONFIG.apiBase}${path}`, requestOptions)
    : await fetchWithTimeout(`${CONFIG.apiBase}${path}`, requestOptions);
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const code = data?.detail?.code || response.status;
    throw new Error(`${code}: ${data?.detail?.message || response.statusText}`);
  }
  const result = {
    data,
    status: response.status,
    elapsedMs: Math.round(performance.now() - startedAt)
  };
  return returnMeta ? result : data;
}

async function loadHealth(reason = "health") {
  const response = await fetchWithGetRetry(`${CONFIG.apiBase}/health`, {
    headers: { Accept: "application/json" }
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(`${response.status}: ${data?.detail?.message || response.statusText}`);
  renderHealth(data);
  log(reason, data.llm_framework || "agent", healthSummary(data));
  return data;
}

function healthSummary(health) {
  const tools = Array.isArray(health?.agent_last_tools) && health.agent_last_tools.length
    ? health.agent_last_tools.join(",")
    : "none";
  return `mode ${health?.llm_last_mode || "--"} · tools ${tools}`;
}

function renderHealth(health) {
  lastHealth = health;
  const framework = health?.llm_framework || "unknown";
  const toolsEnabled = health?.agent_tools_enabled === true ? "tools on" : "tools off";
  ui.agentHealth.textContent = `${framework} · ${toolsEnabled}`;
  const build = health?.build_sha || health?.git_sha || health?.revision || "未提供";
  ui.agentTools.textContent = `${health?.llm_last_mode || "mode --"} · schema ${health?.schema_version || "--"} · build ${build}`;
}

function eventId(type) {
  eventSeq += 1;
  return `console_${type.replaceAll(".", "_")}_${Date.now()}_${eventSeq}`;
}

function stableEventId(actionKey, type) {
  const scope = `${worldState?.session_id || "no_session"}:${actionKey}`;
  if (!stableEventIds.has(scope)) stableEventIds.set(scope, eventId(type));
  return stableEventIds.get(scope);
}

async function loadState(reason = "load") {
  const state = await apiFetch("/v1/state");
  consumeState(state, reason);
}

function consumeState(next, reason = "state") {
  if (!next || next.schema_version !== "0.2.0") return;
  if (worldState && next.session_id === worldState.session_id && next.revision <= lastRevision) return false;
  if (worldState && next.session_id !== worldState.session_id) stableEventIds.clear();
  worldState = next;
  lastRevision = next.revision;
  render();
  log(reason, `${next.stage}`, `r${next.revision}`);
  return true;
}

async function submitEvent(actionKey) {
  if (!worldState) await loadState("before-event");
  const blockReason = blockedReason(actionKey);
  if (blockReason) {
    log("blocked", actionKey, blockReason);
    return;
  }
  const [type, source, payload] = eventDefinition(actionKey);
  const stableId = stableEventId(actionKey, type);
  const response = await apiFetch("/v1/event", {
    method: "POST",
    returnMeta: true,
    body: JSON.stringify({
      schema_version: "0.2.0",
      event_id: stableId,
      session_id: worldState.session_id,
      type,
      source,
      timestamp: new Date().toISOString(),
      payload
    })
  });
  const accepted = response.data;
  consumeState(accepted.state, accepted.duplicate ? "duplicate" : type);
  log(
    "event",
    type,
    `event_id ${stableId} · HTTP ${response.status} · duplicate ${Boolean(accepted.duplicate)} · r${accepted.state?.revision ?? "--"} · ${response.elapsedMs}ms`
  );
}

async function confirm(inputMode = "button") {
  if (!worldState?.confirmation) {
    log("skip", "当前没有 pending confirmation");
    return;
  }
  if (blockedReason("confirm")) {
    log("blocked", `confirm.${inputMode}`, blockedReason("confirm"));
    return;
  }
  const confirmationId = worldState.confirmation.confirmation_id;
  try {
    const response = await apiFetch("/v1/confirm", {
      method: "POST",
      returnMeta: true,
      body: JSON.stringify({
        confirmation_id: confirmationId,
        decision: "accept",
        confirmed_by: "vehicle_hmi",
        input_mode: inputMode
      })
    });
    consumeState(response.data, `confirm.${inputMode}`);
    log("confirm", inputMode, `confirmation_id ${confirmationId} · HTTP ${response.status} · r${response.data?.revision ?? "--"} · ${response.elapsedMs}ms`);
  } catch (error) {
    await loadState("confirm.reconcile");
    const stillPending = worldState?.confirmation?.confirmation_id === confirmationId
      && worldState?.confirmation?.status === "pending";
    if (stillPending) throw error;
    log("confirm", "reconciled", `confirmation_id ${confirmationId} · state ${worldState?.confirmation?.status || worldState?.stage}`);
  }
}

async function reset() {
  const ok = window.confirm("重置会清空共享 Demo Session，影响所有正在联调的端。确认重置？");
  if (!ok) {
    log("skip", "reset cancelled");
    return;
  }
  stableEventIds.clear();
  mobileTaskSyncAcknowledged = false;
  const state = await apiFetch("/v1/session/reset", {
    method: "POST",
    body: JSON.stringify({ scenario_id: "happy-path" })
  });
  consumeState(state, "reset");
}

async function preflight() {
  saveConfig();
  const started = performance.now();
  const health = await loadHealth("preflight.health");
  await loadState("preflight.state");
  connectStream();
  const elapsed = Math.round(performance.now() - started);
  const auth = CONFIG.token ? "token configured" : "no token";
  log("preflight", "ok", `${health?.status || "health"} · ${auth} · ${elapsed}ms`);
}

function render() {
  ui.sessionId.textContent = worldState?.session_id || "未连接";
  ui.revision.textContent = `revision ${worldState?.revision ?? "--"}`;
  ui.stage.textContent = worldState?.stage || "--";
  ui.scene.textContent = `scene ${worldState?.scene || "--"}`;
  ui.pressure.textContent = pressureLabel(worldState?.risk?.pressure_level || "L0");
  ui.late.textContent = `late ${worldState?.risk?.late_minutes || 0} min`;
  ui.surface.textContent = worldState?.primary_surface || "--";
  ui.confirmOwner.textContent = worldState?.confirmation
    ? `${worldState.confirmation.owner_surface} · ${worldState.confirmation.status}`
    : "confirm --";
  if (!ui.agentHealth.textContent || ui.agentHealth.textContent === "未检查") {
    ui.agentHealth.textContent = "未检查";
    ui.agentTools.textContent = "tools --";
  }
  renderSyncMode();
  renderTasks();
  renderActions();
  renderVehicleState();
  renderLedger();
  renderDirector();
  updateButtonStates();
}

function pressureLabel(level) {
  if (level === "L3") return "⚠ L3";
  if (level === "L2") return "⚠ L2";
  if (level === "L1") return "⏱ L1";
  if (level === "Recovery") return "✓ Recovery";
  return "○ L0";
}

function renderTasks() {
  const tasks = worldState?.tasks || [];
  renderStateList(ui.tasks, tasks.map((task) => ({
    title: task.title,
    detail: `${task.task_type} · ${task.adjustable ? "可调整" : "不可后置"} · ${task.status}`
  })), "等待手机端创建任务");
}

function renderActions() {
  const actions = worldState?.actions || [];
  renderStateList(ui.actions, actions.map((action) => ({
    title: action.target,
    detail: `${action.type} · ${action.status} · ${action.summary}`
  })), "等待 Agent 生成动作组");
}

function renderVehicleState() {
  const vehicle = worldState?.vehicle_state;
  if (!vehicle) {
    renderStateList(ui.vehicleState, [], "等待 Agent 写入 vehicle_state");
    return;
  }
  const mode = { auto: "自动", cool: "制冷", heat: "制热", fan: "送风" }[vehicle.ac_mode] || vehicle.ac_mode;
  const fan = { low: "低", medium: "中", high: "高" }[vehicle.fan_speed] || vehicle.fan_speed;
  renderStateList(ui.vehicleState, [{
    title: vehicle.ac_on ? "AC 已开启" : "AC 已关闭",
    detail: `${vehicle.ac_target_temp ?? 24}°C · ${mode} · 风量${fan}`
  }], "");
}

function renderLedger() {
  const ledger = worldState?.action_ledger || [];
  renderStateList(ui.ledger, ledger.slice(-4).reverse().map((item) => ({ detail: item })), "等待动作写入 Ledger");
}

function renderStateList(container, items, emptyText) {
  container.replaceChildren();
  const rows = items.length ? items : [{ detail: emptyText }];
  rows.forEach((item) => {
    const row = document.createElement("li");
    if (item.title) {
      const title = document.createElement("strong");
      title.textContent = item.title;
      row.append(title, document.createElement("br"));
    }
    row.append(document.createTextNode(item.detail || ""));
    container.appendChild(row);
  });
}

function nextStepIndex() {
  if (!worldState) return 0;
  if (!mobileTaskSyncAcknowledged) return 0;
  if (worldState.stage === "off_vehicle_idle" && worldState.tasks?.length) return 1;
  if (worldState.stage === "pre_departure_warning") return 2;
  if (worldState.stage === "handover_to_vehicle") return 3;
  if (worldState.stage === "vehicle_observation") return 4;
  if (worldState.stage === "takeover_L2" || worldState.stage === "takeover_L3") return 6;
  if (worldState.stage === "planning" || worldState.stage === "service_prepared") return 7;
  if (worldState.stage === "waiting_confirmation") return 7;
  if (worldState.stage === "action_completed") return 8;
  if (worldState.stage === "cooldown") return 9;
  if (worldState.stage === "parked_review") return 10;
  return worldState.tasks?.length ? 1 : 0;
}

function renderDirector() {
  const index = Math.min(nextStepIndex(), SCRIPT_STEPS.length);
  const step = SCRIPT_STEPS[index] || { stage: "主线完成", cue: "可进入技术证明或复盘。" };
  const waitingForMobileTask = mobileTaskSyncAcknowledged && !worldState?.tasks?.length;
  ui.directorStage.textContent = `阶段 ${waitingForMobileTask ? 1 : index} / ${SCRIPT_STEPS.length}`;
  ui.nextStepHint.textContent = waitingForMobileTask
    ? "等待手机语音创建任务"
    : step.stage === "主线完成" ? "主线完成" : `下一步：${step.stage}`;
  ui.hostCue.textContent = waitingForMobileTask
    ? "主持提示：当前任务为空；手机语音创建后将自动进入会议延迟步骤。"
    : `主持提示：${step.cue}`;
  document.querySelectorAll(".script-list button[data-action]").forEach((button) => {
    const action = button.dataset.action;
    const stepIndex = SCRIPT_STEPS.findIndex((item) => item.key === action);
    button.classList.toggle("current", stepIndex === index);
    button.classList.toggle("done", stepIndex >= 0 && stepIndex < index);
  });
}

function blockedReason(actionKey) {
  const stage = worldState?.stage;
  const hasTasks = Boolean(worldState?.tasks?.length);
  const hasConfirmation = worldState?.confirmation?.status === "pending";
  if (!worldState && !["refresh", "waitTask", "presetTask"].includes(actionKey)) return "未连接 Agent";
  if (actionKey === "presetTask" && hasTasks) return "当前已有手机任务，无需载入演示预置";
  if (["serviceSuccess", "serviceStock", "serviceBudget"].includes(actionKey) && hasTasks) return "主故事已开始，服务模拟配置已锁定";
  if (["meeting", "approach", "vehicle", "traffic", "stress", "utterance"].includes(actionKey) && !hasTasks) return "需要先创建任务";
  if (actionKey === "traffic" && !["vehicle_observation", "handover_to_vehicle", "takeover_L2", "takeover_L3"].includes(stage)) return "需要先进入车辆或完成交接";
  if (["stress", "hardBrake", "utterance"].includes(actionKey) && !["takeover_L2", "takeover_L3"].includes(stage)) return "需要先进入车辆并触发拥堵风险";
  if (actionKey === "confirm" || actionKey === "voiceConfirm") {
    if (!hasConfirmation) return "当前没有 pending confirmation";
    if (worldState.confirmation.owner_surface !== "vehicle_hmi") return "确认 owner 不在车机";
  }
  if (actionKey === "cooldown" && !["action_completed", "executing"].includes(stage)) return "需要先完成确认执行";
  if (actionKey === "parked" && !["cooldown", "action_completed"].includes(stage)) return "建议完成低干扰恢复后再停车复盘";
  return "";
}

function updateButtonStates() {
  document.querySelectorAll("button[data-action]").forEach((button) => {
    const action = button.dataset.action;
    const reason = blockedReason(action);
    button.disabled = Boolean(reason);
    button.title = reason || "";
  });
  ui.runCurrentStep.disabled = nextStepIndex() >= SCRIPT_STEPS.length;
}

function renderSyncMode(detail = "") {
  const labels = {
    disconnected: "未连接",
    connecting: "连接中",
    sse: "SSE 实时",
    polling: "轮询兜底"
  };
  ui.syncMode.textContent = labels[syncMode] || syncMode;
  ui.syncDetail.textContent = detail || (syncMode === "sse" ? `r${worldState?.revision ?? "--"} · 实时推送` : syncMode === "polling" ? "每 3 秒刷新" : "SSE --");
}

function stopPolling() {
  window.clearInterval(pollTimer);
  pollTimer = null;
}

function startPolling(reason = "SSE 暂不可用") {
  if (pollTimer) return;
  syncMode = "polling";
  renderSyncMode(`${reason} · 每 3 秒刷新`);
  pollTimer = window.setInterval(() => {
    loadState("poll").catch((error) => log("error", "poll failed", friendlyError(error)));
  }, 3000);
}

function scheduleStreamReconnect() {
  window.clearTimeout(streamRetryTimer);
  streamRetryTimer = window.setTimeout(() => connectStream(), 2500);
}

async function connectStream() {
  if (streamAbort) streamAbort.abort();
  window.clearTimeout(streamRetryTimer);
  streamAbort = new AbortController();
  syncMode = "connecting";
  renderSyncMode("正在建立实时状态流");
  try {
    const response = await fetch(`${CONFIG.apiBase}/v1/stream`, {
      headers: authHeaders({ Accept: "text/event-stream" }),
      signal: streamAbort.signal
    });
    if (!response.ok || !response.body) throw new Error(`stream ${response.status}`);
    stopPolling();
    syncMode = "sse";
    renderSyncMode(`r${worldState?.revision ?? "--"} · 实时推送`);
    log("stream", "connected");
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
    throw new Error("stream ended");
  } catch (error) {
    if (error.name === "AbortError") return;
    log("error", "stream disconnected", friendlyError(error));
    startPolling("SSE 已断开");
    scheduleStreamReconnect();
  }
}

function parseStreamChunk(chunk) {
  const dataLine = chunk.split("\n").find((line) => line.startsWith("data: "));
  if (!dataLine) return;
  try {
    consumeState(JSON.parse(dataLine.slice(6)), "stream");
    if (syncMode === "sse") renderSyncMode(`r${worldState?.revision ?? "--"} · 实时推送`);
  } catch (error) {
    log("error", "stream parse failed", friendlyError(error));
  }
}

function saveConfig() {
  CONFIG.apiBase = ui.apiBase.value.trim().replace(/\/$/, "");
  CONFIG.token = ui.token.value.trim();
  const savedAt = Date.now();
  localStorage.setItem(APP_CONFIG_KEY, JSON.stringify({
    configVersion: 2,
    apiBase: CONFIG.apiBase,
    token: CONFIG.token,
    updatedAt: savedAt
  }));
  localStorage.setItem(SHARED_CONFIG_KEY, JSON.stringify({
    configVersion: 1,
    apiBase: CONFIG.apiBase,
    token: CONFIG.token,
    updatedAt: savedAt
  }));
  syncMode = "disconnected";
  renderSyncMode("配置已更新，等待连接");
  log("config", "saved", CONFIG.apiBase);
}

document.addEventListener("click", async (event) => {
  const actionButton = event.target.closest("button[data-action]");
  if (!actionButton) return;
  const action = actionButton.dataset.action;
  const originalLabel = actionButton.textContent;
  actionButton.disabled = true;
  try {
    if (action === "confirm") await confirm("button");
    else if (action === "voiceConfirm") await confirm("voice");
    else if (action === "presetTask") {
      actionButton.textContent = "连接并载入中…";
      saveConfig();
      syncMode = "connecting";
      renderSyncMode("正在鉴权并读取 World State");
      log("preset", "开始载入", "先连接 State，再创建演示任务");
      await loadState("preset.state");
      connectStream();
      loadHealth("preset.health").catch((error) => log("error", "preset health", friendlyError(error)));
      await submitEvent(action);
    }
    else if (["refresh", "waitTask"].includes(action)) {
      if (action === "waitTask") {
        mobileTaskSyncAcknowledged = true;
        render();
      }
      await loadState(action === "waitTask" ? "mobile-task-sync" : "refresh");
    }
    else await submitEvent(action);
  } catch (error) {
    log("error", actionButton.dataset.action, friendlyError(error));
  } finally {
    if (action === "presetTask") actionButton.textContent = originalLabel;
    actionButton.disabled = false;
    updateButtonStates();
  }
});

ui.saveConfig.addEventListener("click", saveConfig);
ui.preflightBtn.addEventListener("click", () => preflight().catch((error) => log("error", "preflight", friendlyError(error))));
ui.runCurrentStep.addEventListener("click", async () => {
  const step = SCRIPT_STEPS[nextStepIndex()];
  if (!step) return;
  try {
    if (step.key === "confirm") await confirm("button");
    else if (step.key === "waitTask") {
      mobileTaskSyncAcknowledged = true;
      render();
      await loadState("mobile-task-sync");
    }
    else await submitEvent(step.key);
  } catch (error) {
    log("error", step.key, friendlyError(error));
  }
});
ui.usePublicAgent.addEventListener("click", () => {
  ui.apiBase.value = PUBLIC_AGENT_API;
});
ui.useLegacyAgent.addEventListener("click", () => {
  ui.apiBase.value = LEGACY_AGENT_API;
});
ui.useLocalAgent.addEventListener("click", () => {
  ui.apiBase.value = LOCAL_AGENT_API;
  ui.token.value = "";
});
ui.connectBtn.addEventListener("click", async () => {
  saveConfig();
  try {
    await loadHealth("health");
    await loadState("connect");
    connectStream();
  } catch (error) {
    log("error", "connect failed", friendlyError(error));
  }
});
ui.resetBtn.addEventListener("click", () => reset().catch((error) => log("error", "reset", friendlyError(error))));
ui.toggleTech.addEventListener("click", () => {
  ui.technicalActions.hidden = !ui.technicalActions.hidden;
  ui.toggleTech.textContent = ui.technicalActions.hidden ? "展开技术验证" : "收起技术验证";
});
ui.copyLog.addEventListener("click", async () => {
  const content = Array.from(ui.eventLog.querySelectorAll(".log-row"))
    .reverse()
    .map((row) => sanitizeLog(row.dataset.raw || row.textContent || ""))
    .join("\n");
  try {
    await navigator.clipboard.writeText(content);
    log("copy", "sanitized log copied");
  } catch (error) {
    log("error", "copy log", friendlyError(error));
  }
});
ui.clearLog.addEventListener("click", () => {
  ui.eventLog.replaceChildren();
});

window.addEventListener("storage", (event) => {
  if (event.key !== SHARED_CONFIG_KEY || !event.newValue) return;
  try {
    const next = JSON.parse(event.newValue);
    if (!next.apiBase || (next.apiBase === CONFIG.apiBase && (next.token || "") === CONFIG.token)) return;
    CONFIG.apiBase = String(next.apiBase).replace(/\/$/, "");
    CONFIG.token = String(next.token || "");
    ui.apiBase.value = CONFIG.apiBase;
    ui.token.value = CONFIG.token;
    streamAbort?.abort();
    stopPolling();
    loadState("shared-config").then(connectStream).catch((error) => log("error", "shared config", friendlyError(error)));
  } catch (_error) {
    log("error", "shared config", "配置格式无效");
  }
});

function sanitizeLog(text) {
  return text
    .replace(/auri-team-[a-z0-9]+/gi, "auri-team-***")
    .replace(/sk-[A-Za-z0-9_-]+/g, "sk-***")
    .replace(/X-Agent-Token:\s*\S+/gi, "X-Agent-Token: ***")
    .replace(/access_token=[^&\s]+/gi, "access_token=***");
}

initConfig();
render();
loadHealth("health").catch((error) => log("error", "health", friendlyError(error)));
loadState("load").then(connectStream).catch((error) => log("error", "initial load", friendlyError(error)));
