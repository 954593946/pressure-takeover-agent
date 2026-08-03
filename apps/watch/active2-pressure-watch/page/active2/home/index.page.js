import * as hmUI from "@zos/ui";
import { log as Logger } from "@zos/utils";
import { BasePage } from "@zeppos/zml/base-page";
import { enableDemoKeepAwake, disableDemoKeepAwake } from "../../../utils/display-control";
import { playHaptic, stopHaptics } from "../../../utils/haptics";
import { collectHealthSnapshot } from "../../../utils/health-sensors";
import { createHello } from "../../../utils/protocol";
import { normalizeWearableCommand } from "../../../utils/state-map";
import {
  BACKGROUND_STYLE,
  CORE_STYLE,
  FOOTER_STYLE,
  HALO_STYLE,
  ICON_STYLE,
  STATUS_DOT_STYLE,
  SUBTITLE_STYLE,
  TITLE_STYLE
} from "./index.page.r.layout.js";

const logger = Logger.getLogger("auri-watch-home");
const MAX_PROCESSED_COMMANDS = 50;
const OFFLINE_TIMEOUT_MS = 45000;
const COMPLETED_AUTO_IDLE_MS = 5000;
const VALID_MODES = {
  idle: true,
  warning: true,
  handover: true,
  processing: true,
  completed: true,
  error: true
};

const INITIAL_STATE = {
  command_id: "initial-idle",
  mode: "idle",
  icon: "A",
  title: "AURI 已就绪",
  text: "等待手机同步",
  color: 0x2f6bff,
  dimColor: 0x132c66,
  haptic: "none"
};

let stateWidgets = null;
let lastCommandId = "";
let lastHapticCommandId = "";
let processedCommandIds = [];
let offlineTimer = null;
let visualEffectTimer = null;
let completedResetTimer = null;
let offlineShown = false;

function getGlobalData() {
  return getApp()._options.globalData || {};
}

function updateSubtitle(text) {
  if (!stateWidgets || !stateWidgets.subtitle) {
    return;
  }

  stateWidgets.subtitle.setProperty(hmUI.prop.MORE, {
    ...SUBTITLE_STYLE,
    text
  });
}

function sendToSide(method, params) {
  try {
    const globalData = getGlobalData();
    if (globalData && typeof globalData.notifySide === "function") {
      globalData.notifySide(method, params);
    }
  } catch (error) {
    logger.debug(`side send failed: ${method}`);
  }
}

function sendAck(commandId, result, reason = "") {
  const ack = {
    type: "ACK",
    command_id: commandId || "",
    result,
    reason,
    timestamp: Date.now()
  };
  getGlobalData().lastAck = ack;
  sendToSide("watch.ack", ack);
  return ack;
}

function hasProcessed(commandId) {
  return processedCommandIds.indexOf(commandId) >= 0;
}

function rememberCommand(commandId) {
  processedCommandIds.push(commandId);
  if (processedCommandIds.length > MAX_PROCESSED_COMMANDS) {
    processedCommandIds.shift();
  }
}

function clearVisualEffect() {
  if (visualEffectTimer) {
    clearInterval(visualEffectTimer);
    visualEffectTimer = null;
  }
}

function cancelCompletedAutoIdle() {
  if (completedResetTimer) {
    clearTimeout(completedResetTimer);
    completedResetTimer = null;
  }
}

function visualFrameFor(command, bright = false) {
  const mode = command.mode || "idle";

  if (mode === "handover") {
    return {
      dotColor: 0x65f4ff,
      haloColor: 0x0b4b57,
      coreColor: 0x20c7d9,
      haloRadius: HALO_STYLE.radius + 10,
      coreRadius: CORE_STYLE.radius - 4
    };
  }

  if (mode === "processing") {
    return {
      dotColor: bright ? 0xb7abff : 0x7a5cff,
      haloColor: bright ? 0x3b2f82 : 0x272052,
      coreColor: bright ? 0x967fff : 0x7a5cff,
      haloRadius: bright ? HALO_STYLE.radius + 8 : HALO_STYLE.radius - 4,
      coreRadius: bright ? CORE_STYLE.radius + 2 : CORE_STYLE.radius - 5
    };
  }

  return {
    dotColor: command.color,
    haloColor: command.dimColor,
    coreColor: command.color,
    haloRadius: HALO_STYLE.radius,
    coreRadius: CORE_STYLE.radius
  };
}

function applyVisualFrame(command, bright = false) {
  if (!stateWidgets || !command) {
    return;
  }

  const frame = visualFrameFor(command, bright);
  stateWidgets.dot.setProperty(hmUI.prop.MORE, {
    ...STATUS_DOT_STYLE,
    color: frame.dotColor
  });
  stateWidgets.halo.setProperty(hmUI.prop.MORE, {
    ...HALO_STYLE,
    radius: frame.haloRadius,
    color: frame.haloColor
  });
  stateWidgets.core.setProperty(hmUI.prop.MORE, {
    ...CORE_STYLE,
    radius: frame.coreRadius,
    color: frame.coreColor
  });
}

function startVisualEffect(command) {
  clearVisualEffect();
  if (!command || command.mode !== "processing") {
    return;
  }

  let bright = false;
  visualEffectTimer = setInterval(() => {
    bright = !bright;
    applyVisualFrame(command, bright);
  }, 900);
}

function renderWearableState(command) {
  if (!stateWidgets || !command) {
    return;
  }

  clearVisualEffect();
  applyVisualFrame(command);
  stateWidgets.icon.setProperty(hmUI.prop.MORE, {
    ...ICON_STYLE,
    text: command.icon
  });
  stateWidgets.title.setProperty(hmUI.prop.MORE, {
    ...TITLE_STYLE,
    text: command.title
  });
  stateWidgets.subtitle.setProperty(hmUI.prop.MORE, {
    ...SUBTITLE_STYLE,
    text: command.text
  });
  stateWidgets.footer.setProperty(hmUI.prop.MORE, {
    ...FOOTER_STYLE,
    text: "AURI"
  });

  getGlobalData().currentState = command;
  startVisualEffect(command);
}

function executeStateChange(command, options = {}) {
  const playFeedback = options.playFeedback !== false;

  lastCommandId = command.command_id;
  renderWearableState(command);

  if (playFeedback && lastHapticCommandId !== command.command_id) {
    playHaptic(command.haptic || "none");
    lastHapticCommandId = command.command_id;
  }
}

function scheduleCompletedAutoIdle(command) {
  cancelCompletedAutoIdle();
  if (!command || command.mode !== "completed") {
    return;
  }

  completedResetTimer = setTimeout(() => {
    completedResetTimer = null;
    const currentState = getGlobalData().currentState || {};
    if (currentState.command_id !== command.command_id || currentState.mode !== "completed") {
      return;
    }

    executeStateChange({
      ...INITIAL_STATE,
      command_id: `auto-idle-${Date.now()}`,
      source: "local-auto-idle"
    }, { playFeedback: false });
  }, COMPLETED_AUTO_IDLE_MS);
}

function handleRemoteSetState(rawCommand) {
  if (!rawCommand || !rawCommand.command_id) {
    return { ack: sendAck("", "error", "missing command_id") };
  }

  if (hasProcessed(rawCommand.command_id)) {
    return { ack: sendAck(rawCommand.command_id, "duplicate") };
  }

  if (!rawCommand.mode && !rawCommand.state) {
    rememberCommand(rawCommand.command_id);
    return { ack: sendAck(rawCommand.command_id, "unsupported", "missing mode") };
  }

  if (rawCommand.mode && !VALID_MODES[rawCommand.mode]) {
    rememberCommand(rawCommand.command_id);
    return { ack: sendAck(rawCommand.command_id, "unsupported", "unsupported mode") };
  }

  const command = normalizeWearableCommand({
    ...rawCommand,
    source: "remote"
  });

  cancelCompletedAutoIdle();
  rememberCommand(command.command_id);
  executeStateChange(command);
  const ack = sendAck(command.command_id, "ok");
  scheduleCompletedAutoIdle(command);

  return { ack };
}

function formatHealthSummary(snapshot) {
  const heartRate = snapshot.heart_rate || "--";
  const spo2 = snapshot.spo2 || "--";
  const sleep = snapshot.sleep_minutes_yesterday || "--";
  return `HR ${heartRate} / O2 ${spo2} / S ${sleep}`;
}

function collectLocalHealth() {
  const snapshot = collectHealthSnapshot();
  getGlobalData().lastSensor = snapshot;
  updateSubtitle(formatHealthSummary(snapshot));
  sendToSide("watch.sensor", snapshot);

  logger.log("health snapshot", JSON.stringify(snapshot));
  return snapshot;
}

function handleBridgeMessage(message = {}) {
  offlineShown = false;
  getGlobalData().lastMessageAt = Date.now();

  if (message.method === "watch.setState" || message.type === "SET_STATE") {
    return handleRemoteSetState(message.params || message);
  }

  if (message.method === "watch.sensorRequest" || message.type === "SENSOR_REQUEST") {
    return collectLocalHealth();
  }

  if (message.method === "watch.ping" || message.type === "PING") {
    sendToSide("watch.pong", {
      type: "PONG",
      ping_id: message.ping_id || (message.params && message.params.ping_id) || "",
      timestamp: Date.now()
    });
    return { type: "PONG", timestamp: Date.now() };
  }

  return { result: "unsupported" };
}

function checkOffline() {
  const lastMessageAt = getGlobalData().lastMessageAt || 0;
  if (!lastMessageAt || offlineShown || Date.now() - lastMessageAt < OFFLINE_TIMEOUT_MS) {
    return;
  }

  offlineShown = true;
  cancelCompletedAutoIdle();
  renderWearableState({
    command_id: `offline-${Date.now()}`,
    mode: "error",
    icon: "X",
    title: "请看手机",
    text: "连接已中断",
    color: 0xd1495b,
    dimColor: 0x4d1821,
    haptic: "none"
  });
}

Page(BasePage({
  name: "auri-watch-home",

  onInit() {
    logger.debug("home onInit");
  },

  build() {
    logger.debug("home build");
    enableDemoKeepAwake();
    this.createStaticLayout();

    const globalData = getGlobalData();
    globalData.handleBridgeMessage = handleBridgeMessage;
    globalData.renderWearableState = renderWearableState;
    globalData.sendSensor = collectLocalHealth;
    globalData.notifySide = (method, params = {}) => {
      const message = { method, params, timestamp: Date.now() };
      globalData.pendingSideMessage = message;
      this.call(message);
    };
    globalData.lastMessageAt = Date.now();

    renderWearableState(INITIAL_STATE);
    sendToSide("watch.hello", createHello());
    offlineTimer = setInterval(checkOffline, 15000);
  },

  onCall(message) {
    return handleBridgeMessage(message);
  },

  onRequest(message, response) {
    const result = handleBridgeMessage(message);
    if (response) {
      response(null, result || { result: "ok", timestamp: Date.now() });
    }
  },

  createStaticLayout() {
    hmUI.createWidget(hmUI.widget.FILL_RECT, BACKGROUND_STYLE);

    stateWidgets = {
      dot: hmUI.createWidget(hmUI.widget.CIRCLE, STATUS_DOT_STYLE),
      halo: hmUI.createWidget(hmUI.widget.CIRCLE, HALO_STYLE),
      core: hmUI.createWidget(hmUI.widget.CIRCLE, CORE_STYLE),
      icon: hmUI.createWidget(hmUI.widget.TEXT, ICON_STYLE),
      title: hmUI.createWidget(hmUI.widget.TEXT, TITLE_STYLE),
      subtitle: hmUI.createWidget(hmUI.widget.TEXT, SUBTITLE_STYLE),
      footer: hmUI.createWidget(hmUI.widget.TEXT, FOOTER_STYLE)
    };
  },

  onDestroy() {
    logger.debug("home onDestroy");
    if (offlineTimer) {
      clearInterval(offlineTimer);
      offlineTimer = null;
    }
    clearVisualEffect();
    cancelCompletedAutoIdle();
    stopHaptics();
    disableDemoKeepAwake();
  }
}));
