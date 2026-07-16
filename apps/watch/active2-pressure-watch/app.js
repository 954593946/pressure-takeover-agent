App({
  globalData: {
    currentState: null,
    bridgeReady: false
  },

  onCreate() {
    console.log("pressure watch app created");
    // Future hook: initialize ZML/BLE bridge to the Side Service here.
  },

  onDestroy() {
    console.log("pressure watch app destroyed");
    // Future hook: disconnect Side Service bridge and stop sensor listeners here.
  }
});
