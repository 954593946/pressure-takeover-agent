export const WATCH_STATES = {
  READY: "ready",
  WARNING: "warning",
  DRIVING: "driving",
  TAKING_OVER: "taking_over",
  AWAITING_CONFIRMATION: "awaiting_confirmation",
  RESOLVED: "resolved",
  OFFLINE: "offline"
};

export const VIBRATION_PATTERNS = {
  NONE: "none",
  SHORT: "short",
  RHYTHMIC: "rhythmic"
};

const STATE_CONFIG = {
  [WATCH_STATES.READY]: {
    state: WATCH_STATES.READY,
    title: "EMO 已就绪",
    subtitle: "等待任务同步",
    footer: "Active 2 · Demo",
    color: 0x2f80ff,
    dimColor: 0x14345f,
    vibration: VIBRATION_PATTERNS.NONE
  },
  [WATCH_STATES.WARNING]: {
    state: WATCH_STATES.WARNING,
    title: "风险提醒",
    subtitle: "请关注出发时间",
    footer: "Active 2 · Demo",
    color: 0xffc83d,
    dimColor: 0x5f4314,
    vibration: VIBRATION_PATTERNS.SHORT
  },
  [WATCH_STATES.DRIVING]: {
    state: WATCH_STATES.DRIVING,
    title: "驾驶模式已连接",
    subtitle: "车机接管主展示",
    footer: "Active 2 · Demo",
    color: 0x2f80ff,
    dimColor: 0x14345f,
    vibration: VIBRATION_PATTERNS.NONE
  },
  [WATCH_STATES.TAKING_OVER]: {
    state: WATCH_STATES.TAKING_OVER,
    title: "接管中",
    subtitle: "正在处理压力源",
    footer: "Active 2 · Demo",
    color: 0x2f80ff,
    dimColor: 0x14345f,
    vibration: VIBRATION_PATTERNS.RHYTHMIC
  },
  [WATCH_STATES.AWAITING_CONFIRMATION]: {
    state: WATCH_STATES.AWAITING_CONFIRMATION,
    title: "等待确认",
    subtitle: "消息已备好",
    footer: "Active 2 · Demo",
    color: 0xffc83d,
    dimColor: 0x5f4314,
    vibration: VIBRATION_PATTERNS.RHYTHMIC
  },
  [WATCH_STATES.RESOLVED]: {
    state: WATCH_STATES.RESOLVED,
    title: "已处理",
    subtitle: "按当前速度即可",
    footer: "Active 2 · Demo",
    color: 0x43d17a,
    dimColor: 0x145f34,
    vibration: VIBRATION_PATTERNS.SHORT
  },
  [WATCH_STATES.OFFLINE]: {
    state: WATCH_STATES.OFFLINE,
    title: "连接中断",
    subtitle: "稍后同步",
    footer: "Active 2 · Demo",
    color: 0x8d96a3,
    dimColor: 0x2b3038,
    vibration: VIBRATION_PATTERNS.NONE
  }
};

const WORLD_STATE_MAP = {
  idle: WATCH_STATES.READY,
  ready: WATCH_STATES.READY,
  warning: WATCH_STATES.WARNING,
  driving: WATCH_STATES.DRIVING,
  taking_over: WATCH_STATES.TAKING_OVER,
  awaiting_confirmation: WATCH_STATES.AWAITING_CONFIRMATION,
  resolved: WATCH_STATES.RESOLVED,
  offline: WATCH_STATES.OFFLINE
};

export function normalizeWatchState(input = {}) {
  const mappedState = WORLD_STATE_MAP[input.state] || WATCH_STATES.READY;
  const config = STATE_CONFIG[mappedState];

  return {
    ...config,
    revision: input.revision || 0,
    title: input.title || config.title,
    subtitle: input.text || input.subtitle || config.subtitle,
    footer: input.footer || config.footer,
    vibration: input.vibration || config.vibration,
    updatedAt: input.updatedAt || ""
  };
}

export function mapWorldStateToWatchState(worldState = {}) {
  const wearable = worldState.wearable || {};

  return normalizeWatchState({
    revision: worldState.revision,
    state: wearable.state,
    text: wearable.text,
    vibration: wearable.vibration,
    updatedAt: worldState.updatedAt
  });
}
