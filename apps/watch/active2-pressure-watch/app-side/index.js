const MOCK_COMMANDS = [
  {
    command_id: "side-idle-001",
    mode: "idle",
    icon: "A",
    title: "AURI 已就绪",
    text: "Side Service 已连接",
    color: 0x2f6bff,
    dimColor: 0x132c66,
    haptic: "none"
  },
  {
    command_id: "side-warning-001",
    mode: "warning",
    icon: "!",
    title: "风险提醒",
    text: "请关注接管准备",
    color: 0xe6a700,
    dimColor: 0x4d3b0b,
    haptic: "double_short"
  },
  {
    command_id: "side-processing-001",
    mode: "processing",
    icon: "...",
    title: "接管处理中",
    text: "AURI 正在协调",
    color: 0x2f6bff,
    dimColor: 0x132c66,
    haptic: "triple"
  },
  {
    command_id: "side-completed-001",
    mode: "completed",
    icon: "OK",
    title: "已完成",
    text: "保持当前节奏",
    color: 0x2e9d6f,
    dimColor: 0x123d2d,
    haptic: "gentle_short"
  }
];

let mockIndex = 0;
let heartbeatTimer = null;

function encode(message) {
  const buffer = Buffer.from(JSON.stringify(message), "utf-8");
  return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
}

function decode(payload) {
  return JSON.parse(Buffer.from(payload).toString("utf-8"));
}

function send(message) {
  try {
    messaging.peerSocket.send(encode(message));
  } catch (error) {
    console.log("AURI side send failed", error);
  }
}

function sendMockState(reason) {
  const command = MOCK_COMMANDS[mockIndex];
  mockIndex = (mockIndex + 1) % MOCK_COMMANDS.length;
  console.log("AURI side send state", reason, command.command_id);
  send({
    type: "SET_STATE",
    method: "watch.setState",
    params: command,
    timestamp: Date.now()
  });
}

function startHeartbeat() {
  if (heartbeatTimer) {
    return;
  }

  heartbeatTimer = setInterval(() => {
    send({
      type: "PING",
      method: "watch.ping",
      params: {
        ping_id: `ping-${Date.now()}`
      },
      timestamp: Date.now()
    });
  }, 15000);
}

AppSideService({
  onInit() {
    console.log("AURI side service init");
    messaging.peerSocket.addListener("message", (payload) => {
      try {
        const message = decode(payload);
        console.log("AURI side received", JSON.stringify(message));
      } catch (error) {
        console.log("AURI side parse failed", error);
      }
    });
  },

  onRun() {
    console.log("AURI side service run");
    sendMockState("run");
    startHeartbeat();
  },

  onDestroy() {
    console.log("AURI side service destroy");
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }
});
