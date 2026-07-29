const assert = require("node:assert/strict");
const view = require("./world-state-view.js");

const state = {
  risk: { late_minutes: 12 },
  tasks: [
    {
      task_id: "task_gym",
      title: "健身",
      scheduled_at: "2026-07-29T20:00:00+08:00",
      location: "健身房",
      task_type: "flexible",
      priority: "low",
      adjustable: true,
      status: "pending",
      waiting_party: []
    },
    {
      task_id: "task_airport",
      title: "去机场接同事",
      scheduled_at: "2026-07-29T18:40:00+08:00",
      location: "苏南硕放机场",
      task_type: "rigid",
      priority: "high",
      adjustable: false,
      status: "pending",
      waiting_party: ["同事"]
    },
    {
      task_id: "task_report",
      title: "提交周报",
      scheduled_at: null,
      location: null,
      task_type: "flexible",
      priority: "medium",
      adjustable: true,
      status: "rescheduled",
      waiting_party: []
    }
  ],
  actions: [
    { type: "reschedule", target: "健身", status: "completed", summary: "健身已调整" },
    { type: "message", target: "同事", status: "awaiting_confirmation", summary: "接机消息已准备" },
    { type: "reschedule", target: "周报", status: "ready", summary: "周报时间已调整" }
  ],
  vehicle_state: {
    ac_on: true,
    ac_target_temp: 21,
    ac_mode: "auto",
    fan_speed: "medium"
  },
  output: {
    conclusion: "空调已开启，设到 21°C，自动模式，中风量。手机和车机状态会同步显示。"
  },
  last_utterance: {
    text: "我还来得及吗？帮我处理",
    source: "mobile",
    input_mode: "voice",
    received_at: "2026-07-29T18:28:00+08:00"
  }
};

assert.equal(view.sortedTasks(state).length, 3);
assert.equal(view.primaryTask(state).task_id, "task_airport");
assert.equal(view.navigationTask(state).location, "苏南硕放机场");
assert.deepEqual(view.taskCounts(state), {
  total: 3,
  rigid: 1,
  flexible: 2,
  completed: 0,
  rescheduled: 1
});
assert.equal(view.taskView(state.tasks[0], state.risk).status, "可调整");
assert.equal(view.taskView(state.tasks[2], state.risk).status, "已调整");
assert.deepEqual(view.actionProgress(state), {
  total: 3,
  completed: 1,
  pending: 2,
  percent: 33
});
assert.deepEqual(view.climate(state), {
  available: true,
  on: true,
  temperature: "21.0",
  mode: "自动",
  fan: "中",
  summary: "AC 开启 · 21.0° · 自动 · 风量中"
});
assert.equal(view.isClimateConclusion(state.output.conclusion), true);
assert.equal(view.isClimateConclusion("空调已开启，任务状态已同步到手机。"), true);
assert.equal(view.driverConclusion(state, "当前行程正常。"), "当前行程正常。");
assert.equal(
  view.driverConclusion(
    { stage: "off_vehicle_idle", risk: { late_minutes: 0 }, output: { conclusion: "现在还剩 3 个任务。" } },
    "当前任务暂无行程风险。"
  ),
  "当前任务暂无行程风险。"
);
assert.match(view.planSummary(state), /^1\/3 项动作已完成。/);
assert.deepEqual(view.utterance(state), {
  available: true,
  text: "我还来得及吗？帮我处理",
  source: "mobile",
  inputMode: "voice",
  sourceLabel: "手机"
});
assert.equal(view.utterance({}).available, false);

const noVehicleState = view.climate({});
assert.equal(noVehicleState.available, false);
assert.equal(noVehicleState.temperature, "--");

console.log("world-state-view tests passed");
