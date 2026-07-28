import {
  HAPTIC_PATTERNS
} from "./state-map";

import * as sensor from "@zos/sensor";

let vibrator = null;
let hapticTimers = [];

function clearHapticTimers() {
  hapticTimers.forEach((timerId) => clearTimeout(timerId));
  hapticTimers = [];
}

function getVibrator() {
  if (!vibrator) {
    if (!sensor.Vibrator) {
      return null;
    }

    vibrator = new sensor.Vibrator();
  }

  return vibrator;
}

function startScene(scene) {
  const current = getVibrator();
  if (!current || !scene) {
    return;
  }

  current.stop();
  current.start({ mode: scene });
}

function pulse(scene, delay) {
  const timerId = setTimeout(() => {
    startScene(scene);
  }, delay);
  hapticTimers.push(timerId);
}

function stopAfter(delay) {
  const timerId = setTimeout(() => {
    stopHaptics();
  }, delay);
  hapticTimers.push(timerId);
}

export function stopHaptics() {
  try {
    clearHapticTimers();
    if (vibrator) {
      vibrator.stop();
    }
  } catch (error) {
    console.log("AURI haptic stop failed", error);
  }
}

export function playHaptic(pattern) {
  try {
    stopHaptics();

    switch (pattern) {
      case HAPTIC_PATTERNS.DOUBLE_SHORT:
        startScene(sensor.VIBRATOR_SCENE_NOTIFICATION);
        stopAfter(700);
        break;
      case HAPTIC_PATTERNS.SINGLE_SHORT:
        startScene(sensor.VIBRATOR_SCENE_SHORT_MIDDLE);
        stopAfter(260);
        break;
      case HAPTIC_PATTERNS.TRIPLE:
        startScene(sensor.VIBRATOR_SCENE_SHORT_STRONG);
        pulse(sensor.VIBRATOR_SCENE_SHORT_STRONG, 180);
        pulse(sensor.VIBRATOR_SCENE_SHORT_STRONG, 360);
        stopAfter(720);
        break;
      case HAPTIC_PATTERNS.GENTLE_SHORT:
        startScene(sensor.VIBRATOR_SCENE_SHORT_LIGHT);
        stopAfter(220);
        break;
      case HAPTIC_PATTERNS.ERROR_COMBO:
        startScene(sensor.VIBRATOR_SCENE_SHORT_STRONG);
        pulse(sensor.VIBRATOR_SCENE_SHORT_MIDDLE, 220);
        pulse(sensor.VIBRATOR_SCENE_SHORT_STRONG, 440);
        stopAfter(820);
        break;
      case HAPTIC_PATTERNS.NONE:
      default:
        stopHaptics();
        break;
    }
  } catch (error) {
    console.log("AURI haptic unsupported", pattern, error);
  }
}
