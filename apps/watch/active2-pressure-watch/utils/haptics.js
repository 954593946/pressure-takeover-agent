import { HAPTIC_PATTERNS } from "./state-map";
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

function stopVibratorOnly() {
  try {
    if (vibrator) {
      vibrator.stop();
    }
  } catch (error) {
    console.log("AURI haptic pulse stop failed", error);
  }
}

function startScene(scene) {
  const current = getVibrator();
  if (!current || !scene) {
    return;
  }

  current.stop();
  current.start({ mode: scene });
}

function schedule(action, delay) {
  const timerId = setTimeout(() => {
    hapticTimers = hapticTimers.filter((current) => current !== timerId);
    action();
  }, delay);
  hapticTimers.push(timerId);
}

function pulse(scene, delay, duration) {
  schedule(() => {
    startScene(scene);
  }, delay);
  schedule(() => {
    stopVibratorOnly();
  }, delay + duration);
}

function playSequence(pulses, totalDuration) {
  stopHaptics();
  pulses.forEach((item) => {
    pulse(item.scene, item.delay, item.duration);
  });
  schedule(() => {
    stopHaptics();
  }, totalDuration);
}

function playCountedPulses(count, scene, duration = 140, gap = 280) {
  const pulses = [];
  for (let index = 0; index < count; index += 1) {
    pulses.push({
      scene,
      delay: index * gap,
      duration
    });
  }
  playSequence(pulses, (count - 1) * gap + duration + 120);
}

export function stopHaptics() {
  try {
    clearHapticTimers();
    stopVibratorOnly();
  } catch (error) {
    console.log("AURI haptic stop failed", error);
  }
}

export function playHaptic(pattern) {
  try {
    switch (pattern) {
      case HAPTIC_PATTERNS.DOUBLE_SHORT:
        playCountedPulses(3, sensor.VIBRATOR_SCENE_SHORT_STRONG, 150, 300);
        break;
      case HAPTIC_PATTERNS.SINGLE_SHORT:
        playCountedPulses(2, sensor.VIBRATOR_SCENE_SHORT_MIDDLE, 150, 300);
        break;
      case HAPTIC_PATTERNS.TRIPLE:
        playCountedPulses(4, sensor.VIBRATOR_SCENE_SHORT_STRONG, 140, 280);
        break;
      case HAPTIC_PATTERNS.GENTLE_SHORT:
        playCountedPulses(1, sensor.VIBRATOR_SCENE_SHORT_LIGHT, 160, 280);
        break;
      case HAPTIC_PATTERNS.ERROR_COMBO:
        playCountedPulses(5, sensor.VIBRATOR_SCENE_SHORT_STRONG, 130, 260);
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
