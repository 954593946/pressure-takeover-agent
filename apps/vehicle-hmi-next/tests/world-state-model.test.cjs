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
assert.equal(vm.navigation.route.id, "route_demo_task_pickup_child");
assert.deepEqual(vm.navigation.route.origin.coordinates, [120.791879, 31.33468]);
assert.deepEqual(vm.navigation.route.destination.coordinates, [120.7359, 31.3048]);
assert.equal(vm.navigation.route.progress, 0.7);
assert.equal(vm.navigation.route.source, "demo_fixture");
assert.equal(vm.navigation.route.isSimulated, true);
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
  navigation: null,
  vehicle_state: { ac_on: false, ac_target_temp: 24, ac_mode: "auto", fan_speed: "medium" }
};
const emptyVm = model.buildVehicleHmiViewModel(emptyState, { now });
assert.equal(emptyVm.tasks.total, 0);
assert.equal(emptyVm.navigation.hasDestination, false);
assert.equal(emptyVm.navigation.etaLabel, "--:--");
assert.equal(emptyVm.navigation.route, null);
assert.equal(emptyVm.vehicle.available, true);
assert.equal(emptyVm.vehicle.temperatureLabel, "24°C");
assert.equal(emptyVm.interaction.canConfirm, false);

const mixedState = {
  ...fixture,
  revision: 9,
  navigation: null,
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

const locationFallbackVm = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 10,
  navigation: null,
  tasks: [
    { ...fixture.tasks[0], task_id: "report", title: "提交报告", task_type: "rigid", priority: "high", location: null },
    { ...fixture.tasks[1], task_id: "pickup", title: "机场接人", task_type: "flexible", priority: "medium", location: "苏南硕放机场" }
  ]
}, { now });
assert.equal(locationFallbackVm.tasks.primary.title, "提交报告");
assert.equal(locationFallbackVm.tasks.navigation.title, "机场接人");
assert.equal(locationFallbackVm.navigation.destination, "苏南硕放机场");
assert.equal(locationFallbackVm.navigation.route, null);

const legacyLocationFallbackVm = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 15,
  navigation: null,
  tasks: [
    { ...fixture.tasks[0], task_id: "legacy", title: "机场接人", location: "苏南硕放机场" }
  ]
}, { now });
assert.equal(legacyLocationFallbackVm.navigation.route, null);
assert.equal(legacyLocationFallbackVm.navigation.destination, "苏南硕放机场");

const invalidContractVm = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 16,
  navigation: { ...fixture.navigation, task_id: "missing-task", progress: null }
}, { now });
assert.equal(invalidContractVm.navigation.route, null);
assert.equal(invalidContractVm.navigation.destination, "阳光小学");

const nullProgressVm = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 17,
  navigation: { ...fixture.navigation, progress: null }
}, { now });
assert.equal(nullProgressVm.navigation.route.progress, null);

const failedServiceVm = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 11,
  service_orders: [{ ...fixture.service_orders[0], status: "failed", error_code: "PROVIDER_UNAVAILABLE" }]
}, { now });
assert.equal(failedServiceVm.serviceOrders.hasFailure, true);

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

const wrongSurface = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 12,
  primary_surface: "mobile",
  confirmation: { ...fixture.confirmation, owner_surface: "mobile" }
}, { now });
assert.equal(wrongSurface.interaction.canConfirm, false);
assert.equal(wrongSurface.interaction.disabledReason, "wrong_surface");

const mobileOwnedOutput = model.buildVehicleHmiViewModel({
  ...fixture,
  revision: 13,
  output: { ...fixture.output, owner_surface: "mobile", suppressed_surfaces: [] }
}, { now });
assert.equal(mobileOwnedOutput.agentOutput.available, false);

const missingRequired = model.buildVehicleHmiViewModel({ ...fixture, revision: 14, tasks: undefined }, { now });
assert.equal(missingRequired.meta.isCompatible, false);
assert.equal(missingRequired.meta.reason, "invalid_tasks");

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
