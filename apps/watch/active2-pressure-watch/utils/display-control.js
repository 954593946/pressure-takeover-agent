import {
  pauseDropWristScreenOff,
  pausePalmScreenOff,
  resetDropWristScreenOff,
  resetPageBrightTime,
  resetPalmScreenOff,
  setPageBrightTime,
  setWakeUpRelaunch
} from "@zos/display";

const DEMO_BRIGHT_TIME_MS = 2147483000;

function safeDisplayCall(label, action) {
  try {
    action();
  } catch (error) {
    console.log(`AURI display ${label} failed`, error);
  }
}

export function enableDemoKeepAwake() {
  safeDisplayCall("setPageBrightTime", () => {
    setPageBrightTime({ brightTime: DEMO_BRIGHT_TIME_MS });
  });

  safeDisplayCall("pauseDropWristScreenOff", () => {
    pauseDropWristScreenOff({ duration: 0 });
  });

  safeDisplayCall("pausePalmScreenOff", () => {
    pausePalmScreenOff({ duration: 0 });
  });

  safeDisplayCall("setWakeUpRelaunch", () => {
    setWakeUpRelaunch({ relaunch: true });
  });
}

export function disableDemoKeepAwake() {
  safeDisplayCall("resetPageBrightTime", () => {
    resetPageBrightTime();
  });

  safeDisplayCall("resetDropWristScreenOff", () => {
    resetDropWristScreenOff();
  });

  safeDisplayCall("resetPalmScreenOff", () => {
    resetPalmScreenOff();
  });

  safeDisplayCall("clearWakeUpRelaunch", () => {
    setWakeUpRelaunch({ relaunch: false });
  });
}
