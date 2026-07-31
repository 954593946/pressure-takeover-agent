const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const model = require("../src/world-state-model.js");

const fixture = JSON.parse(fs.readFileSync(
  path.resolve(__dirname, "../../../contracts/examples/world-state.json"),
  "utf8"
));
const now = Date.parse("2026-07-15T18:29:00+08:00");

const vm = model.buildVehicleHmiViewModel(fixture, { now });
assert.equal(vm.meta.isCompatible, true);
assert.equal(vm.meta.sessionId, "demo_run_001");
assert.equal(vm.tasks.total, 2);
assert.equal(vm.tasks.rigid, 1);
assert.equal(vm.tasks.flexible, 1);
assert.equal(vm.tasks.primary.title, "接孩子");
assert.equal(vm.tasks.navigation.location, "阳光小学");
assert.equal(vm.navigation.destination, "阳光小学");
assert.equal(vm.navigation.etaLabel, "18:28");
assert.equal(vm.navigation.lateMinutes, 18);
assert.equal(vm.risk.level, "L2");
assert.equal(vm.actions.counts.total, 3);
assert.equal(vm.actions.counts.pending, 3);
assert.equal(vm.agentOutput.available, true);
assert.ok(vm.agentOutput.fullText.length > vm.agentOutput.preview.length);
assert.equal(vm.utterance.text, "我还来得及吗？帮我处理");
assert.equal(vm.utterance.sourceLabel, "手机语音");
assert.equal(vm.wearable.connected, true);
assert.equal(vm.vehicle.acOn, false);
assert.equal(vm.vehicle.temperatureLabel, "24°C");
assert.equal(vm.interaction.canConfirm, true);
assert.equal(vm.serviceOrders.items[0].itemKinds, 8);
assert.equal(vm.serviceOrders.items[0].itemCount, 9);
assert.equal(vm.serviceOrders.totalAmount, 186);

const climateOutput = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 12,
  output: { ...fixture.output, conclusion: "空调已开启，设到 21°C，自动模式，中风量。" }
}, { now });
assert.equal(climateOutput.agentOutput.available, false);
assert.equal(climateOutput.agentOutput.climateOnly, true);

const emptyState = {
  ...fixture,
  revision: 8,
  stage: "off_vehicle_idle",
  scene: "off_vehicle",
  primary_surface: "mobile",
  tasks: [],
  eta: null,
  actions: [],
  confirmation: null,
  output: null,
  last_utterance: null,
  service_orders: [],
  vehicle_state: null
};
const emptyVm = model.buildVehicleHmiViewModel(emptyState, { now });
assert.equal(emptyVm.tasks.total, 0);
assert.equal(emptyVm.navigation.hasDestination, false);
assert.equal(emptyVm.navigation.etaLabel, "--:--");
assert.equal(emptyVm.vehicle.available, false);
assert.equal(emptyVm.vehicle.temperatureLabel, "--");
assert.equal(emptyVm.interaction.canConfirm, false);

const mixedState = {
  ...fixture,
  revision: 9,
  tasks: [
    { ...fixture.tasks[0], task_id: "completed", title: "已完成旧任务", status: "completed", location: "旧地点" },
    { ...fixture.tasks[1], task_id: "flex", title: "提交周报", status: "pending", location: null },
    { ...fixture.tasks[0], task_id: "active", title: "机场接人", status: "pending", location: "苏南硕放机场" }
  ],
  actions: [
    { ...fixture.actions[0], status: "completed" },
    { ...fixture.actions[1], status: "blocked" },
    { ...fixture.actions[2], status: "failed" }
  ]
};
const mixedVm = model.buildVehicleHmiViewModel(mixedState, { now });
assert.equal(mixedVm.tasks.total, 3);
assert.equal(mixedVm.tasks.navigation.title, "机场接人");
assert.equal(mixedVm.actions.counts.completed, 1);
assert.equal(mixedVm.actions.counts.blocked, 1);
assert.equal(mixedVm.actions.counts.failed, 1);

const suppressed = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 10,
  output: { ...fixture.output, suppressed_surfaces: ["vehicle_hmi"] }
}, { now });
assert.equal(suppressed.agentOutput.available, false);

const expired = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 11,
  confirmation: { ...fixture.confirmation, expires_at: "2026-07-15T18:20:00+08:00" }
}, { now });
assert.equal(expired.interaction.canConfirm, false);
assert.equal(expired.interaction.disabledReason, "expired");

assert.deepEqual(
  model.acceptWorldState({ sessionId: "s1", revision: 2, retiredSessionIds: [] }, { ...fixture, session_id: "s1", revision: 3 }),
  { accepted: true, resetRequired: false, reason: "new_revision" }
);
assert.equal(model.acceptWorldState({ sessionId: "s1", revision: 3, retiredSessionIds: [] }, { ...fixture, session_id: "s1", revision: 3 }).reason, "stale_revision");
assert.equal(model.acceptWorldState({ sessionId: "s1", revision: 9, retiredSessionIds: [] }, { ...fixture, session_id: "s2", revision: 1 }).resetRequired, true);
assert.equal(model.acceptWorldState({ sessionId: "s2", revision: 1, retiredSessionIds: ["s1"] }, { ...fixture, session_id: "s1", revision: 99 }).reason, "retired_session");

const incompatible = model.buildVehicleHmiViewModel({ ...fixture, schema_version: "9.9.9" }, { now });
assert.equal(incompatible.meta.isCompatible, false);
assert.equal(incompatible.interaction.canConfirm, false);

console.log("vehicle-hmi-next world-state-model tests passed");
