import { BaseApp } from "@zeppos/zml/base-app";
import { createCommandRuntime } from "./utils/command-runtime";
import { createHello, PROTOCOL_METHODS } from "./utils/protocol";

const BUSINESS_LOG_METHODS = {
  "watch.ack": true,
  "watch.sensor": true,
  "watch.pong": true
};

App(BaseApp({
  globalData: {
    currentState: null,
    commandRuntime: createCommandRuntime(),
    lastAck: null,
    lastSensor: null,
    lastMessageAt: 0,
    sideReady: false,
    lastSideMessageAt: 0,
    handleBridgeMessage: null,
    renderWearableState: null,
    sendSensor: null,
    notifySide: null,
    pendingSideMessage: null,
    pendingIncomingMessages: [],
    processedSideMessageKeys: {}
  },

  onCreate() {
    this.globalData.notifySide = this.notifySide.bind(this);
    this.notifySide(PROTOCOL_METHODS.WATCH_HELLO, createHello());
  },

  onDestroy() {
  },

  notifySide(method, params = {}) {
    const message = { method, params, timestamp: Date.now() };
    this.globalData.pendingSideMessage = message;

    if (typeof this.call === "function") {
      try {
        this.call(message);
        this.globalData.sideReady = true;
        logBusiness("AURI_DEVICE_SEND", method, params);
        return true;
      } catch (error) {
        logBusiness("AURI_DEVICE_SEND_FAILED", method, error && error.message ? error.message : error);
      }
    }

    return false;
  },

  handleBridgeMessage(message) {
    const messageKey = getMessageKey(message);
    if (messageKey && this.globalData.processedSideMessageKeys[messageKey]) {
      return null;
    }

    if (messageKey) {
      this.globalData.processedSideMessageKeys[messageKey] = true;
    }

    this.globalData.lastSideMessageAt = Date.now();
    this.globalData.lastMessageAt = this.globalData.lastSideMessageAt;
    this.globalData.sideReady = true;

    const handler = this.globalData.handleBridgeMessage;
    if (handler) {
      return handler(message);
    }

    this.globalData.pendingIncomingMessages.push(message);
    return null;
  },

  onCall(message) {
    return this.handleBridgeMessage(message);
  },

  onRequest(message, response) {
    const result = this.handleBridgeMessage(message);
    if (response) {
      response(null, result || { result: "ok" });
    }
  }
}));

function getMessageKey(message = {}) {
  const method = message.method || message.type || "";
  const pingId = message.ping_id || (message.params && message.params.ping_id) || "";
  const commandId = message.command_id || (message.params && message.params.command_id) || "";

  if (method === "watch.ping" || method === "PING") {
    return `${method}:${pingId || message.timestamp || ""}`;
  }

  if (method === "watch.setState" || method === "SET_STATE") {
    return `${method}:${commandId}`;
  }

  if (method === "watch.sensorRequest" || method === "SENSOR_REQUEST") {
    return `${method}:${message.request_id || message.timestamp || ""}`;
  }

  if (method === "watch.hello" || method === "HELLO") {
    return `${method}:${message.timestamp || ""}`;
  }

  return "";
}

function logBusiness(label, method, payload) {
  if (!BUSINESS_LOG_METHODS[method]) {
    return;
  }

  if (payload === undefined) {
    console.log(label, method);
    return;
  }

  try {
    console.log(label, method, JSON.stringify(payload));
  } catch (error) {
    console.log(label, method);
  }
}
