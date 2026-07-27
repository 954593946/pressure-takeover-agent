import * as hmUI from "@zos/ui";
import { log as Logger } from "@zos/utils";
import { playHaptic, stopHaptics } from "../../../utils/haptics";
import { collectHealthSnapshot } from "../../../utils/health-sensors";
import {
  BACKGROUND_STYLE,
  CORE_STYLE,
  DEBUG_BUTTON_STYLE,
  DEBUG_STYLE,
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
const VALID_MODES = {
  idle: true,
  warning: true,
  handover: true,
  processing: true,
  completed: true,
  error: true
};

const LOCAL_STATES = [
  {
    command_id: "local-idle-001",
    mode: "idle",
    icon: "A",
    title: "AURI 已就绪",
    text: "等待手机同步",
    color: 0x2f6bff,
    dimColor: 0x132c66,
    haptic: "none"
  },
  {
    command_id: "local-warning-001",
    mode: "warning",
    icon: "!",
    title: "风险提醒",
    text: "请关注接管准备",
    color: 0xe6a700,
    dimColor: 0x4d3b0b,
    haptic: "double_short"
  },
  {
    command_id: "local-handover-001",
    mode: "handover",
    icon: ">",
    title: "进入驾驶模式",
    text: "车机负责确认",
    color: 0x2f6bff,
    dimColor: 0x132c66,
    haptic: "single_short"
  },
  {
    command_id: "local-processing-001",
    mode: "processing",
    icon: "...",
    title: "接管处理中",
    text: "AURI 正在协调",
    color: 0x2f6bff,
    dimColor: 0x132c66,
    haptic: "triple"
  },
  {
    command_id: "local-completed-001",
    mode: "completed",
    icon: "OK",
    title: "已完成",
    text: "保持当前节奏",
    color: 0x2e9d6f,
    dimColor: 0x123d2d,
    haptic: "gentle_short"
  },
  {
    command_id: "local-error-001",
    mode: "error",
    icon: "X",
    title: "请看手机",
    text: "连接或数据异常",
    color: 0xd1495b,
    dimColor: 0x4d1821,
    haptic: "error_combo"
  }
];

let stateWidgets = null;
let stateIndex = 0;
let lastCommandId = "";
let lastHapticCommandId = "";
let processedCommandIds = [];
let offlineTimer = null;
let offlineShown = false;
let localCommandSeq = 0;

function getGlobalData() {
  return getApp()._options.globalData || {};
}

function updateDebug(text) {
  if (!stateWidgets || !stateWidgets.debug) {
    return;
  }

  stateWidgets.debug.setProperty(hmUI.prop.MORE, {
    ...DEBUG_STYLE,
    text
  });
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
    const app = getApp()._options;
    if (app && typeof app.notifySide === "function") {
      app.notifySide(method, params);
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

function renderWearableState(command) {
  if (!stateWidgets || !command) {
    return;
  }

  stateWidgets.dot.setProperty(hmUI.prop.MORE, {
    ...STATUS_DOT_STYLE,
    color: command.color
  });
  stateWidgets.halo.setProperty(hmUI.prop.MORE, {
    ...HALO_STYLE,
    color: command.dimColor
  });
  stateWidgets.core.setProperty(hmUI.prop.MORE, {
    ...CORE_STYLE,
    color: command.color
  });
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
    text: "AURI Active 2"
  });

  getGlobalData().currentState = command;
}

function handleSetState(command) {
  if (!command || !command.command_id) {
    updateDebug("ack: error / missing id");
    return { ack: sendAck("", "error", "missing command_id") };
  }

  if (hasProcessed(command.command_id)) {
    updateDebug(`ack: duplicate / ${command.command_id}`);
    return { ack: sendAck(command.command_id, "duplicate") };
  }

  if (!VALID_MODES[command.mode]) {
    rememberCommand(command.command_id);
    updateDebug(`ack: unsupported / ${command.mode || "none"}`);
    return { ack: sendAck(command.command_id, "unsupported", "unsupported mode") };
  }

  lastCommandId = command.command_id;
  rememberCommand(command.command_id);
  renderWearableState(command);

  if (lastHapticCommandId !== command.command_id) {
    playHaptic(command.haptic || "none");
    lastHapticCommandId = command.command_id;
  }

  updateDebug(`ack: ok / ${command.command_id}`);
  return { ack: sendAck(command.command_id, "ok") };
}

function showNextLocalState() {
  const baseCommand = LOCAL_STATES[stateIndex];
  localCommandSeq += 1;
  const command = {
    ...baseCommand,
    command_id: `local-${baseCommand.mode}-${localCommandSeq}`
  };
  stateIndex = (stateIndex + 1) % LOCAL_STATES.length;
  handleSetState(command);
}

function formatHealthSummary(snapshot) {
  const heartRate = snapshot.heart_rate || "--";
  const spo2 = snapshot.spo2 || "--";
  const sleep = snapshot.sleep_minutes_yesterday || "--";
  return `HR ${heartRate} / O2 ${spo2} / S ${sleep}`;
}

function collectLocalHealth() {
  updateDebug("sensor: reading...");

  const snapshot = collectHealthSnapshot();
  getGlobalData().lastSensor = snapshot;
  updateSubtitle(formatHealthSummary(snapshot));
  updateDebug(`sensor: ${snapshot.result}`);
  sendToSide("watch.sensor", snapshot);

  logger.log("health snapshot", JSON.stringify(snapshot));
  return snapshot;
}

function handleBridgeMessage(message = {}) {
  offlineShown = false;
  getGlobalData().lastMessageAt = Date.now();

  if (message.method === "watch.setState" || message.type === "SET_STATE") {
    return handleSetState(message.params || message);
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
    updateDebug("pong: local");
    return { type: "PONG", timestamp: Date.now() };
  }

  updateDebug("ack: unsupported");
  return { result: "unsupported" };
}

function checkOffline() {
  const lastMessageAt = getGlobalData().lastMessageAt || 0;
  if (!lastMessageAt || offlineShown || Date.now() - lastMessageAt < OFFLINE_TIMEOUT_MS) {
    return;
  }

  offlineShown = true;
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
  updateDebug("offline: no heartbeat");
}

Page({
  onInit() {
    logger.debug("home onInit");
  },

  build() {
    logger.debug("home build");
    this.createStaticLayout();

    const globalData = getGlobalData();
    globalData.handleBridgeMessage = handleBridgeMessage;
    globalData.renderWearableState = renderWearableState;
    globalData.sendSensor = collectLocalHealth;
    globalData.lastMessageAt = Date.now();

    renderWearableState(LOCAL_STATES[0]);
    updateDebug("短按状态 / 长按健康");
    offlineTimer = setInterval(checkOffline, 15000);
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
      debug: hmUI.createWidget(hmUI.widget.TEXT, DEBUG_STYLE),
      footer: hmUI.createWidget(hmUI.widget.TEXT, FOOTER_STYLE),
      debugButton: hmUI.createWidget(hmUI.widget.BUTTON, {
        ...DEBUG_BUTTON_STYLE,
        text: "调试",
        click_func: showNextLocalState,
        longpress_func: collectLocalHealth
      })
    };
  },

  onDestroy() {
    logger.debug("home onDestroy");
    if (offlineTimer) {
      clearInterval(offlineTimer);
      offlineTimer = null;
    }
    stopHaptics();
  }
});
