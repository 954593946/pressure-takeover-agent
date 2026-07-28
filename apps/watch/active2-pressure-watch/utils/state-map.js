export const AURI_COLORS = {
  NAVY: 0x0b1b33,
  IVORY: 0xf5f2ec,
  MUTED: 0x8fa0b5,
  PROCESSING: 0x2f6bff,
  WARNING: 0xe6a700,
  SUCCESS: 0x2e9d6f,
  CRITICAL: 0xd1495b
};

export const WEARABLE_MODES = {
  IDLE: "idle",
  WARNING: "warning",
  HANDOVER: "handover",
  PROCESSING: "processing",
  COMPLETED: "completed",
  ERROR: "error"
};

export const HAPTIC_PATTERNS = {
  NONE: "none",
  DOUBLE_SHORT: "double_short",
  SINGLE_SHORT: "single_short",
  TRIPLE: "triple",
  GENTLE_SHORT: "gentle_short",
  ERROR_COMBO: "error_combo"
};

const MODE_CONFIG = {
  [WEARABLE_MODES.IDLE]: {
    mode: WEARABLE_MODES.IDLE,
    icon: "A",
    title: "AURI 已就绪",
    text: "等待手机同步",
    color: AURI_COLORS.PROCESSING,
    dimColor: 0x132c66,
    haptic: HAPTIC_PATTERNS.NONE
  },
  [WEARABLE_MODES.WARNING]: {
    mode: WEARABLE_MODES.WARNING,
    icon: "!",
    title: "风险提醒",
    text: "请关注接管准备",
    color: AURI_COLORS.WARNING,
    dimColor: 0x4d3b0b,
    haptic: HAPTIC_PATTERNS.DOUBLE_SHORT
  },
  [WEARABLE_MODES.HANDOVER]: {
    mode: WEARABLE_MODES.HANDOVER,
    icon: ">",
    title: "进入驾驶模式",
    text: "车机负责确认",
    color: AURI_COLORS.PROCESSING,
    dimColor: 0x132c66,
    haptic: HAPTIC_PATTERNS.SINGLE_SHORT
  },
  [WEARABLE_MODES.PROCESSING]: {
    mode: WEARABLE_MODES.PROCESSING,
    icon: "...",
    title: "接管处理中",
    text: "AURI 正在协调",
    color: AURI_COLORS.PROCESSING,
    dimColor: 0x132c66,
    haptic: HAPTIC_PATTERNS.TRIPLE
  },
  [WEARABLE_MODES.COMPLETED]: {
    mode: WEARABLE_MODES.COMPLETED,
    icon: "OK",
    title: "已完成",
    text: "保持当前节奏",
    color: AURI_COLORS.SUCCESS,
    dimColor: 0x123d2d,
    haptic: HAPTIC_PATTERNS.GENTLE_SHORT
  },
  [WEARABLE_MODES.ERROR]: {
    mode: WEARABLE_MODES.ERROR,
    icon: "X",
    title: "请看手机",
    text: "连接或数据异常",
    color: AURI_COLORS.CRITICAL,
    dimColor: 0x4d1821,
    haptic: HAPTIC_PATTERNS.ERROR_COMBO
  }
};

const LEGACY_STATE_MAP = {
  idle: WEARABLE_MODES.IDLE,
  ready: WEARABLE_MODES.IDLE,
  warning: WEARABLE_MODES.WARNING,
  driving: WEARABLE_MODES.HANDOVER,
  taking_over: WEARABLE_MODES.PROCESSING,
  awaiting_confirmation: WEARABLE_MODES.PROCESSING,
  resolved: WEARABLE_MODES.COMPLETED,
  offline: WEARABLE_MODES.ERROR
};

const LEGACY_HAPTIC_MAP = {
  none: HAPTIC_PATTERNS.NONE,
  short: HAPTIC_PATTERNS.SINGLE_SHORT,
  rhythmic: HAPTIC_PATTERNS.TRIPLE
};

function parseColor(input, fallback) {
  if (typeof input === "number") {
    return input;
  }

  if (typeof input === "string" && input[0] === "#") {
    const parsed = Number.parseInt(input.slice(1), 16);
    return Number.isNaN(parsed) ? fallback : parsed;
  }

  return fallback;
}

export function isSupportedMode(mode) {
  return Boolean(MODE_CONFIG[mode]);
}

export function getModeConfig(mode) {
  return MODE_CONFIG[mode] || MODE_CONFIG[WEARABLE_MODES.IDLE];
}

export function normalizeWearableCommand(input = {}) {
  const mode = isSupportedMode(input.mode)
    ? input.mode
    : LEGACY_STATE_MAP[input.state] || WEARABLE_MODES.IDLE;
  const config = getModeConfig(mode);

  return {
    command_id: input.command_id || input.commandId || `local-${Date.now()}`,
    mode,
    icon: input.icon || config.icon,
    title: input.title || config.title,
    text: input.text || input.subtitle || config.text,
    color: parseColor(input.color, config.color),
    dimColor: parseColor(input.dimColor, config.dimColor),
    haptic: input.haptic || LEGACY_HAPTIC_MAP[input.vibration] || config.haptic,
    duration_ms: input.duration_ms || input.durationMs || 3000,
    version: input.version || input.revision || 0,
    source: input.source || "local"
  };
}

export function mapWorldStateToWearableCommand(worldState = {}) {
  const wearable = worldState.wearable || {};

  return normalizeWearableCommand({
    command_id: `world-${worldState.revision || Date.now()}`,
    version: worldState.revision || 0,
    state: wearable.state,
    text: wearable.text,
    vibration: wearable.vibration,
    source: "world-state"
  });
}
