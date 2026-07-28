import * as appApi from "@zos/app";
import * as sensor from "@zos/sensor";

const HEALTH_PERMISSIONS = [
  "data:user.hd.heart_rate",
  "data:user.hd.spo2",
  "data:user.hd.sleep"
];

function now() {
  return Math.floor(Date.now() / 1000);
}

function safeRead(readFn, fallback = null) {
  try {
    return readFn();
  } catch (error) {
    console.log("AURI sensor read failed", error);
    return fallback;
  }
}

export function ensureHealthPermissions() {
  try {
    if (!appApi.queryPermission || !appApi.requestPermission) {
      return false;
    }

    const results = appApi.queryPermission({ permissions: HEALTH_PERMISSIONS }) || [];
    const missing = HEALTH_PERMISSIONS.filter((_, index) => results[index] !== 2);

    if (!missing.length) {
      return true;
    }

    appApi.requestPermission({
      permissions: missing,
      callback: (result) => {
        console.log("AURI health permission result", result);
      }
    });

    return false;
  } catch (error) {
    console.log("AURI health permission unsupported", error);
    return false;
  }
}

export function collectHealthSnapshot() {
  const permissionReady = ensureHealthPermissions();
  let result = permissionReady ? "ok" : "unsupported";

  const heart_rate = safeRead(() => {
    if (!sensor.HeartRate || (sensor.checkSensor && !sensor.checkSensor(sensor.HeartRate))) {
      result = "unsupported";
      return null;
    }

    const heartRate = new sensor.HeartRate();
    const value = heartRate.getLast();
    return value > 0 ? value : null;
  });

  const spo2Result = safeRead(() => {
    if (!sensor.BloodOxygen || (sensor.checkSensor && !sensor.checkSensor(sensor.BloodOxygen))) {
      result = "unsupported";
      return null;
    }

    const bloodOxygen = new sensor.BloodOxygen();
    const current = bloodOxygen.getCurrent();
    return current && current.retCode === 2 ? current.value : null;
  });

  const sleepInfo = safeRead(() => {
    if (!sensor.Sleep || (sensor.checkSensor && !sensor.checkSensor(sensor.Sleep))) {
      result = "unsupported";
      return null;
    }

    const sleep = new sensor.Sleep();
    sleep.updateInfo();
    return sleep.getInfo();
  });

  const worn = safeRead(() => {
    if (!sensor.Wear || (sensor.checkSensor && !sensor.checkSensor(sensor.Wear))) {
      return null;
    }

    const wear = new sensor.Wear();
    return typeof wear.getStatus === "function" ? Boolean(wear.getStatus()) : null;
  });

  const battery = safeRead(() => {
    if (!sensor.Battery || (sensor.checkSensor && !sensor.checkSensor(sensor.Battery))) {
      return null;
    }

    const batterySensor = new sensor.Battery();
    return typeof batterySensor.getCurrent === "function" ? batterySensor.getCurrent() : null;
  });

  return {
    type: "SENSOR",
    heart_rate,
    spo2: spo2Result,
    sleep_minutes_yesterday: sleepInfo ? sleepInfo.totalTime : null,
    sleep_score: sleepInfo ? sleepInfo.score : null,
    deep_sleep_minutes: sleepInfo ? sleepInfo.deepTime : null,
    worn,
    battery,
    confidence: permissionReady ? "device" : "unsupported",
    timestamp: now(),
    result
  };
}
