const DEFAULT_CONFIG = {
  apiBase: "http://127.0.0.1:8000",
  stream: true
};

const storedConfig = JSON.parse(localStorage.getItem("auri-demo-console-config") || "{}");
const CONFIG = { ...DEFAULT_CONFIG, ...storedConfig, ...(window.AURI_CONFIG || {}) };

const ACTIONS = {
  task: ["task.created", "mobile", { text: "今天18:10接孩子，之后去超市" }],
  meeting: ["meeting.overrun", "demo_console", { delay_minutes: 20 }],
  approach: ["scene.approaching", "demo_console", {}],
  vehicle: ["scene.vehicle_entered", "demo_console", {}],
  traffic: ["traffic.updated", "demo_console", { eta: "2026-07-15T18:28:00+08:00", late_minutes: 18 }],
  stress: ["wearable.signal", "wearable", { heart_rate: 120, confidence: 0.9 }],
  hardBrake: ["driving.signal", "vehicle_hmi", { hard_brake: true, acceleration_variance: "high", confidence: 0.8 }],
  utterance: ["user.utterance", "vehicle_hmi", { text: "我还来得及吗？帮我处理" }],
  serviceSuccess: ["service.mock.config", "demo_console", { mode: "success" }],
  serviceStock: ["service.mock.config", "demo_console", { mode: "out_of_stock" }],
  serviceBudget: ["service.mock.config", "demo_console", { mode: "over_budget" }],
  cooldown: ["cooldown.elapsed", "demo_console", {}],
  parked: ["scene.parked", "demo_console", {}]
};

const $ = (selector) => document.querySelector(selector);
const ui = {
  apiBase: $("#apiBase"),
  token: $("#token"),
  saveConfig: $("#saveConfig"),
  connectBtn: $("#connectBtn"),
  resetBtn: $("#resetBtn"),
  clearLog: $("#clearLog"),
  sessionId: $("#sessionId"),
  revision: $("#revision"),
  stage: $("#stage"),
  scene: $("#scene"),
  pressure: $("#pressure"),
  late: $("#late"),
  surface: $("#surface"),
  confirmOwner: $("#confirmOwner"),
  tasks: $("#tasks"),
  actions: $("#actions"),
  eventLog: $("#eventLog")
};

let worldState = null;
let lastRevision = -1;
let eventSeq = 0;
let streamAbort = null;

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
  row.innerHTML = `<span>${new Date().toLocaleTimeString("zh-CN", { hour12: false })}</span><strong>${kind}</strong><code>${message}${detail ? ` · ${detail}` : ""}</code>`;
  ui.eventLog.prepend(row);
  while (ui.eventLog.children.length > 80) ui.eventLog.lastElementChild.remove();
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

function eventId(type) {
  eventSeq += 1;
  return `console_${type.replaceAll(".", "_")}_${Date.now()}_${eventSeq}`;
}

async function loadState(reason = "load") {
  const state = await apiFetch("/v1/state");
  consumeState(state, reason);
}

function consumeState(next, reason = "state") {
  if (!next || next.schema_version !== "0.2.0") return;
  if (worldState && next.session_id === worldState.session_id && next.revision < lastRevision) return;
  worldState = next;
  lastRevision = next.revision;
  render();
  log(reason, `${next.stage}`, `r${next.revision}`);
}

async function submitEvent(actionKey) {
  if (!worldState) await loadState("before-event");
  const [type, source, payload] = ACTIONS[actionKey];
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
  consumeState(accepted.state, accepted.duplicate ? "duplicate" : type);
}

async function confirm(inputMode = "button") {
  if (!worldState?.confirmation) {
    log("skip", "当前没有 pending confirmation");
    return;
  }
  const state = await apiFetch("/v1/confirm", {
    method: "POST",
    body: JSON.stringify({
      confirmation_id: worldState.confirmation.confirmation_id,
      decision: "accept",
      confirmed_by: "vehicle_hmi",
      input_mode: inputMode
    })
  });
  consumeState(state, `confirm.${inputMode}`);
}

async function reset() {
  const state = await apiFetch("/v1/session/reset", {
    method: "POST",
    body: JSON.stringify({ scenario_id: "happy-path" })
  });
  consumeState(state, "reset");
}

function render() {
  ui.sessionId.textContent = worldState?.session_id || "未连接";
  ui.revision.textContent = `revision ${worldState?.revision ?? "--"}`;
  ui.stage.textContent = worldState?.stage || "--";
  ui.scene.textContent = `scene ${worldState?.scene || "--"}`;
  ui.pressure.textContent = worldState?.risk?.pressure_level || "L0";
  ui.late.textContent = `late ${worldState?.risk?.late_minutes || 0} min`;
  ui.surface.textContent = worldState?.primary_surface || "--";
  ui.confirmOwner.textContent = worldState?.confirmation
    ? `${worldState.confirmation.owner_surface} · ${worldState.confirmation.status}`
    : "confirm --";
  renderTasks();
  renderActions();
}

function renderTasks() {
  const tasks = worldState?.tasks || [];
  ui.tasks.innerHTML = tasks.length
    ? tasks.map((task) => `<li><strong>${task.title}</strong><br>${task.task_type} · ${task.adjustable ? "可调整" : "不可后置"} · ${task.status}</li>`).join("")
    : "<li>等待手机端创建任务</li>";
}

function renderActions() {
  const actions = worldState?.actions || [];
  ui.actions.innerHTML = actions.length
    ? actions.map((action) => `<li><strong>${action.target}</strong><br>${action.type} · ${action.status} · ${action.summary}</li>`).join("")
    : "<li>等待 Agent 生成动作组</li>";
}

async function connectStream() {
  if (streamAbort) streamAbort.abort();
  streamAbort = new AbortController();
  try {
    const response = await fetch(`${CONFIG.apiBase}/v1/stream`, {
      headers: authHeaders({ Accept: "text/event-stream" }),
      signal: streamAbort.signal
    });
    if (!response.ok || !response.body) throw new Error(`stream ${response.status}`);
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
  } catch (error) {
    if (error.name === "AbortError") return;
    log("error", "stream disconnected", error.message);
  }
}

function parseStreamChunk(chunk) {
  const dataLine = chunk.split("\n").find((line) => line.startsWith("data: "));
  if (!dataLine) return;
  try {
    consumeState(JSON.parse(dataLine.slice(6)), "stream");
  } catch (error) {
    log("error", "stream parse failed", error.message);
  }
}

function saveConfig() {
  CONFIG.apiBase = ui.apiBase.value.trim().replace(/\/$/, "");
  CONFIG.token = ui.token.value.trim();
  localStorage.setItem("auri-demo-console-config", JSON.stringify({
    apiBase: CONFIG.apiBase,
    token: CONFIG.token
  }));
  log("config", "saved", CONFIG.apiBase);
}

document.addEventListener("click", async (event) => {
  const actionButton = event.target.closest("button[data-action]");
  if (!actionButton) return;
  actionButton.disabled = true;
  try {
    const action = actionButton.dataset.action;
    if (action === "confirm") await confirm("button");
    else if (action === "voiceConfirm") await confirm("voice");
    else if (action === "refresh") await loadState("refresh");
    else await submitEvent(action);
  } catch (error) {
    log("error", actionButton.dataset.action, error.message);
  } finally {
    actionButton.disabled = false;
  }
});

ui.saveConfig.addEventListener("click", saveConfig);
ui.connectBtn.addEventListener("click", async () => {
  saveConfig();
  try {
    await loadState("connect");
    connectStream();
  } catch (error) {
    log("error", "connect failed", error.message);
  }
});
ui.resetBtn.addEventListener("click", () => reset().catch((error) => log("error", "reset", error.message)));
ui.clearLog.addEventListener("click", () => {
  ui.eventLog.innerHTML = "";
});

initConfig();
render();
loadState("load").then(connectStream).catch((error) => log("error", "initial load", error.message));
