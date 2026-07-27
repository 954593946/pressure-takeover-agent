import { BaseSideService } from "@zeppos/zml/base-side";

const ANDROID_GATEWAY_BASE_URL = "http://127.0.0.1:8765";
const SIDE_MOCK_ENABLED = false;
const SIDE_MOCK_EDGE_CASES_ENABLED = false;
const HEARTBEAT_MS = 30000;
const GATEWAY_POLL_MS = 1000;

const MOCK_STATE = {
  command_id: "side-mock-warning-001",
  mode: "warning",
  icon: "!",
  title: "风险提醒",
  text: "请关注接管准备",
  color: 0xe6a700,
  dimColor: 0x4d3b0b,
  haptic: "double_short",
  duration_ms: 3000
};

const MOCK_DUPLICATE_STATE = {
  ...MOCK_STATE,
  title: "重复风险提醒",
  text: "应返回 duplicate"
};

const MOCK_UNSUPPORTED_STATE = {
  command_id: "side-mock-unsupported-001",
  mode: "unknown",
  icon: "?",
  title: "未知状态",
  text: "应返回 unsupported",
  color: 0xd1495b,
  dimColor: 0x4d1821,
  haptic: "none",
  duration_ms: 3000
};

let timers = [];
let heartbeatTimer = null;
let gatewayPollTimer = null;
let lastGatewayCommandId = "";
let lastGatewaySensorRequestId = "";
let gatewayReachable = null;

function getMethod(message = {}) {
  return message.method || message.type || "unknown";
}

function logSide(service, label, payload) {
  const text = payload === undefined ? label : `${label} ${payload}`;
  try {
    service.log(text);
  } catch (error) {}
}

function clearTimers() {
  timers.forEach((timer) => clearTimeout(timer));
  timers = [];

  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }

  if (gatewayPollTimer) {
    clearInterval(gatewayPollTimer);
    gatewayPollTimer = null;
  }
}

function sendToDevice(service, method, params = {}) {
  const message = { method, params, timestamp: Date.now() };

  try {
    service.call(message);
    return true;
  } catch (error) {
    return false;
  }
}

function scheduleMock(service) {
  if (!SIDE_MOCK_ENABLED) {
    return;
  }

  timers.push(setTimeout(() => {
    sendToDevice(service, "watch.setState", MOCK_STATE);
  }, 2000));

  if (SIDE_MOCK_EDGE_CASES_ENABLED) {
    timers.push(setTimeout(() => {
      sendToDevice(service, "watch.setState", MOCK_DUPLICATE_STATE);
    }, 6000));

    timers.push(setTimeout(() => {
      sendToDevice(service, "watch.setState", MOCK_UNSUPPORTED_STATE);
    }, 10000));

    timers.push(setTimeout(() => {
      sendToDevice(service, "watch.sensorRequest", {
        request_id: `side-sensor-${Date.now()}`,
        reason: "mock",
        timestamp: Date.now()
      });
    }, 14000));
  }
}

function startHeartbeat(service) {
  if (heartbeatTimer) {
    return;
  }

  heartbeatTimer = setInterval(() => {
    sendToDevice(service, "watch.ping", {
      ping_id: `side-ping-${Date.now()}`,
      timestamp: Date.now()
    });
  }, HEARTBEAT_MS);
}

function startGatewayPolling(service) {
  if (gatewayPollTimer) {
    return;
  }

  gatewayPollTimer = setInterval(() => {
    pollGateway(service);
  }, GATEWAY_POLL_MS);
  pollGateway(service);
}

async function pollGateway(service) {
  const path = `/v1/watch/outbox?last_command_id=${encodeURIComponent(lastGatewayCommandId)}&last_sensor_request_id=${encodeURIComponent(lastGatewaySensorRequestId)}`;
  const data = await gatewayRequest(service, path);
  if (!data || data.result !== "ok") {
    return;
  }

  if (data.set_state && data.set_state.params) {
    const commandId = data.set_state.params.command_id || "";
    if (commandId && commandId !== lastGatewayCommandId) {
      if (sendToDevice(service, "watch.setState", data.set_state.params)) {
        lastGatewayCommandId = commandId;
      }
    }
  }

  if (data.sensor_request && data.sensor_request.params) {
    const requestId = data.sensor_request.params.request_id || "";
    if (requestId && requestId !== lastGatewaySensorRequestId) {
      if (sendToDevice(service, "watch.sensorRequest", data.sensor_request.params)) {
        lastGatewaySensorRequestId = requestId;
      }
    }
  }
}

async function postInbox(service, message) {
  await gatewayRequest(service, "/v1/watch/inbox", {
    method: "POST",
    body: JSON.stringify(message)
  });
}

async function gatewayRequest(service, path, options = {}) {
  if (typeof fetch !== "function") {
    setGatewayReachable(service, false, "fetch unavailable");
    return null;
  }

  try {
    const request = {
      url: `${ANDROID_GATEWAY_BASE_URL}${path}`,
      method: options.method || "GET",
      headers: {
        "Content-Type": "application/json"
      }
    };

    if (options.body) {
      request.body = options.body;
    }

    const res = await fetch(request);
    const body = res && res.body !== undefined ? res.body : {};
    const data = typeof body === "string" ? JSON.parse(body || "{}") : body;
    setGatewayReachable(service, true);
    return data || {};
  } catch (error) {
    setGatewayReachable(service, false, error && error.message ? error.message : "gateway request failed");
    return null;
  }
}

function setGatewayReachable(service, reachable, reason = "") {
  if (gatewayReachable === reachable) {
    return;
  }

  gatewayReachable = reachable;
  logSide(service, reachable ? "AURI_GATEWAY_READY" : "AURI_GATEWAY_DOWN", reason);
}

AppSideService(BaseSideService({
  onInit() {
  },

  onRun() {
    scheduleMock(this);
    startHeartbeat(this);
    startGatewayPolling(this);
  },

  onDestroy() {
    clearTimers();
  },

  onCall(message) {
    const method = getMethod(message);
    if (method === "watch.hello" || method === "watch.ack" || method === "watch.sensor" || method === "watch.pong") {
      logSide(this, `AURI_SIDE_CALL ${method}`, JSON.stringify(message));
      postInbox(this, message);
    }
  },

  onRequest(message, response) {
    if (response) {
      response(null, { result: "ok", timestamp: Date.now() });
    }
  }
}));
