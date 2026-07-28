import * as ble from "@zos/ble";

function toBuffer(message) {
  const text = JSON.stringify(message);
  return Buffer.from(text, "utf-8");
}

function fromBuffer(payload) {
  const text = Buffer.from(payload).toString("utf-8");
  return JSON.parse(text);
}

export function createRawBridge({ onMessage, onStatus }) {
  let connected = false;
  const pending = [];

  function flush() {
    if (!connected || !ble.send) {
      return;
    }

    while (pending.length) {
      send(pending.shift());
    }
  }

  function send(message) {
    try {
      if (!ble.send) {
        pending.push(message);
        return false;
      }

      const buffer = toBuffer(message);
      const data = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);

      if (!connected) {
        pending.push(message);
        return false;
      }

      ble.send(data, buffer.byteLength);
      return true;
    } catch (error) {
      console.log("AURI bridge send failed", error);
      return false;
    }
  }

  function start() {
    try {
      if (ble.addListener) {
        ble.addListener((status) => {
          connected = Boolean(status);
          if (onStatus) {
            onStatus(connected);
          }
          flush();
        });
      }

      if (ble.createConnect) {
        ble.createConnect((index, data, size) => {
          try {
            const message = fromBuffer(data);
            if (onMessage) {
              onMessage(message, { index, size });
            }
          } catch (error) {
            console.log("AURI bridge parse failed", error);
          }
        });
      }
    } catch (error) {
      console.log("AURI bridge start failed", error);
    }
  }

  function stop() {
    try {
      if (ble.disConnect) {
        ble.disConnect();
      }
    } catch (error) {
      console.log("AURI bridge stop failed", error);
    }
  }

  return {
    start,
    stop,
    send,
    isConnected: () => connected
  };
}
