export const PROTOCOL_TYPES = {
  HELLO: "HELLO",
  SET_STATE: "SET_STATE",
  ACK: "ACK",
  SENSOR: "SENSOR",
  SENSOR_REQUEST: "SENSOR_REQUEST",
  PING: "PING",
  PONG: "PONG"
};

export const ACK_RESULTS = {
  OK: "ok",
  DUPLICATE: "duplicate",
  UNSUPPORTED: "unsupported",
  ERROR: "error"
};

export const PROTOCOL_METHODS = {
  WATCH_HELLO: "watch.hello",
  WATCH_ACK: "watch.ack",
  WATCH_SENSOR: "watch.sensor",
  WATCH_PONG: "watch.pong",
  SET_STATE: "watch.setState",
  SENSOR_REQUEST: "watch.sensorRequest",
  PING: "watch.ping"
};

export function createAck(commandId, result, reason = "") {
  return {
    type: PROTOCOL_TYPES.ACK,
    command_id: commandId || "",
    result,
    reason,
    timestamp: Date.now()
  };
}

export function createPong(pingId = "") {
  return {
    type: PROTOCOL_TYPES.PONG,
    ping_id: pingId,
    timestamp: Date.now()
  };
}

export function createHello(capabilities = {}) {
  return {
    type: PROTOCOL_TYPES.HELLO,
    device_id: "active2-round",
    fw_version: "auri-watch-0.2.0",
    capabilities: {
      haptics: true,
      sensor_heart_rate: true,
      sensor_spo2: true,
      sensor_sleep: true,
      ...capabilities
    },
    timestamp: Date.now()
  };
}
