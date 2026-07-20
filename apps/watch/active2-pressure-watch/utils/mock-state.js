import { HAPTIC_PATTERNS, WEARABLE_MODES } from "./state-map";

export const MOCK_SET_STATE_COMMANDS = [
  {
    type: "SET_STATE",
    command_id: "mock-idle-001",
    mode: WEARABLE_MODES.IDLE,
    title: "AURI 已就绪",
    text: "等待手机同步",
    haptic: HAPTIC_PATTERNS.NONE,
    duration_ms: 3000
  },
  {
    type: "SET_STATE",
    command_id: "mock-warning-001",
    mode: WEARABLE_MODES.WARNING,
    title: "风险提醒",
    text: "请关注接管准备",
    haptic: HAPTIC_PATTERNS.DOUBLE_SHORT,
    duration_ms: 3000
  },
  {
    type: "SET_STATE",
    command_id: "mock-handover-001",
    mode: WEARABLE_MODES.HANDOVER,
    title: "进入驾驶模式",
    text: "车机负责确认",
    haptic: HAPTIC_PATTERNS.SINGLE_SHORT,
    duration_ms: 3000
  },
  {
    type: "SET_STATE",
    command_id: "mock-processing-001",
    mode: WEARABLE_MODES.PROCESSING,
    title: "接管处理中",
    text: "AURI 正在协调",
    haptic: HAPTIC_PATTERNS.TRIPLE,
    duration_ms: 3000
  },
  {
    type: "SET_STATE",
    command_id: "mock-completed-001",
    mode: WEARABLE_MODES.COMPLETED,
    title: "已完成",
    text: "保持当前节奏",
    haptic: HAPTIC_PATTERNS.GENTLE_SHORT,
    duration_ms: 3000
  },
  {
    type: "SET_STATE",
    command_id: "mock-error-001",
    mode: WEARABLE_MODES.ERROR,
    title: "请看手机",
    text: "连接或数据异常",
    haptic: HAPTIC_PATTERNS.ERROR_COMBO,
    duration_ms: 3000
  },
  {
    type: "SET_STATE",
    command_id: "mock-warning-001",
    mode: WEARABLE_MODES.WARNING,
    title: "重复风险提醒",
    text: "应返回 duplicate",
    haptic: HAPTIC_PATTERNS.DOUBLE_SHORT,
    duration_ms: 3000
  }
];

export const MOCK_WEARABLE_COMMAND = MOCK_SET_STATE_COMMANDS[0];
