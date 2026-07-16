import { WATCH_STATES, VIBRATION_PATTERNS } from "./state-map";

export const MOCK_WATCH_STATE = {
  revision: 1,
  state: WATCH_STATES.READY,
  title: "EMO 已就绪",
  text: "等待任务同步",
  footer: "Active 2 · Demo",
  vibration: VIBRATION_PATTERNS.NONE,
  updatedAt: ""
};

export const MOCK_STATE_SEQUENCE = [
  MOCK_WATCH_STATE,
  {
    revision: 2,
    state: WATCH_STATES.WARNING,
    title: "风险提醒",
    text: "17:38 前需出发",
    vibration: VIBRATION_PATTERNS.SHORT
  },
  {
    revision: 3,
    state: WATCH_STATES.DRIVING,
    title: "驾驶模式已连接",
    text: "车机接管主展示",
    vibration: VIBRATION_PATTERNS.NONE
  },
  {
    revision: 4,
    state: WATCH_STATES.AWAITING_CONFIRMATION,
    title: "等待确认",
    text: "消息已备好",
    vibration: VIBRATION_PATTERNS.RHYTHMIC
  },
  {
    revision: 5,
    state: WATCH_STATES.RESOLVED,
    title: "已处理",
    text: "按当前速度即可",
    vibration: VIBRATION_PATTERNS.SHORT
  },
  {
    revision: 6,
    state: WATCH_STATES.OFFLINE,
    title: "连接中断",
    text: "稍后同步",
    vibration: VIBRATION_PATTERNS.NONE
  }
];
