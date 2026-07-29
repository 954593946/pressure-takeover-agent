import { BaseApp } from "@zeppos/zml/base-app";
import { createCommandRuntime } from "./utils/command-runtime";

App(BaseApp({
  globalData: {
    currentState: null,
    commandRuntime: createCommandRuntime(),
    lastAck: null,
    lastSensor: null,
    lastMessageAt: 0,
    bridgeReady: false,
    handleBridgeMessage: null,
    renderWearableState: null,
    sendSensor: null,
    pendingSideMessage: null
  },

  onCreate() {
    console.log("AURI watch app created");
  },

  onDestroy() {
    console.log("AURI watch app destroyed");
  },

  handleBridgeMessage(message) {
    const handler = this.globalData.handleBridgeMessage;
    if (handler) {
      return handler(message);
    }
    return null;
  }
}));
