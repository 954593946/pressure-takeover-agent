import { createCommandRuntime } from "./utils/command-runtime";
import { createHello, PROTOCOL_METHODS } from "./utils/protocol";
import { createRawBridge } from "./utils/raw-bridge";

let bridge = null;

App({
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
    bridge = createRawBridge({
      onStatus: (connected) => {
        this.globalData.bridgeReady = connected;
        console.log("AURI bridge status", connected);
      },
      onMessage: (message) => {
        this.globalData.lastMessageAt = Date.now();
        this.handleBridgeMessage(message);
      }
    });
    bridge.start();
    this.notifySide(PROTOCOL_METHODS.WATCH_HELLO, createHello());
  },

  onDestroy() {
    console.log("AURI watch app destroyed");
    if (bridge) {
      bridge.stop();
      bridge = null;
    }
  },

  notifySide(method, params = {}) {
    const message = { method, params, timestamp: Date.now() };
    this.globalData.pendingSideMessage = message;

    if (bridge) {
      bridge.send(message);
    }
  },

  handleBridgeMessage(message) {
    const handler = this.globalData.handleBridgeMessage;
    if (handler) {
      return handler(message);
    }
    return null;
  }
});
