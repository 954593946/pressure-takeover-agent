const assert = require("node:assert/strict");

const calls = [];
const storageValues = new Map();
let routePlanCount = 0;
let drivingResultMode = "complete";

class FakeClassList {
  constructor() {
    this.values = new Set();
  }

  add(value) {
    this.values.add(value);
  }

  remove(value) {
    this.values.delete(value);
  }

  contains(value) {
    return this.values.has(value);
  }
}

class FakeElement {
  constructor(tagName = "div") {
    this.tagName = tagName.toUpperCase();
    this.children = [];
    this.className = "";
    this.dataset = {};
    this.hidden = false;
    this.innerHTML = "";
    this.textContent = "";
    this.style = {
      values: new Map(),
      setProperty: (name, value) => this.style.values.set(name, value),
      getPropertyValue: (name) => this.style.values.get(name) || ""
    };
  }

  append(...children) {
    this.children.push(...children);
  }

  querySelector(selector) {
    if (selector === "span") return this.children.find((child) => child.tagName === "SPAN") || null;
    return null;
  }
}

class FakeMap {
  constructor(_container, options) {
    this.options = options;
    this.added = [];
    this.removed = [];
    this.rotation = Number(options.rotation || 0);
  }

  add(value) {
    this.added.push(value);
    calls.push(["add", value]);
  }

  remove(value) {
    this.removed.push(...value);
  }

  setFitView() {
    calls.push(["fit"]);
  }

  setZoomAndCenter(zoom, center) {
    calls.push(["zoom-center", zoom, center]);
  }

  setPitch(pitch) {
    calls.push(["pitch", pitch]);
  }

  setRotation(rotation) {
    this.rotation = rotation;
    calls.push(["rotation", rotation]);
  }

  getRotation() {
    return this.rotation;
  }

  zoomIn() {
    calls.push(["zoom-in"]);
  }

  zoomOut() {
    calls.push(["zoom-out"]);
  }

  resize() {
    calls.push(["resize"]);
  }
}

class FakeTrafficLayer {
  constructor(options) {
    this.options = options;
  }

  setOpacity(value) {
    this.opacity = value;
    calls.push(["traffic-opacity", value]);
  }
}

class FakePolyline {
  constructor(options) {
    this.options = { ...options };
    this.path = options.path;
  }

  setPath(path) {
    this.path = path;
    calls.push(["polyline-path", path.length]);
  }

  setOptions(options) {
    Object.assign(this.options, options);
  }
}

class FakeMarker {
  constructor(options) {
    this.options = options;
    this.position = options.position;
    this.visible = true;
  }

  setPosition(position) {
    this.position = position;
  }

  moveTo(position) {
    this.position = position;
    calls.push(["move-to", position]);
  }

  setAngle(angle) {
    this.angle = angle;
  }

  show() {
    this.visible = true;
  }

  hide() {
    this.visible = false;
  }
}

const successfulRoute = {
  distance: 4000,
  time: 900,
  steps: [{
    instruction: "左转进入星龙街",
    road: "星龙街",
    distance: 1000,
    path: [
      [120.791879, 31.33468],
      [120.786, 31.331]
    ]
  }, {
    instruction: "沿现代大道行驶 1000 米后右转",
    road: "现代大道",
    distance: 1000,
    path: [
      [120.786, 31.331],
      [120.775, 31.325]
    ]
  }, {
    instruction: "沿星湖街行驶2公里到达目的地",
    road: "星湖街",
    distance: 2000,
    path: [
      [120.775, 31.325],
      [120.7359, 31.3048]
    ]
  }]
};

class FakeDriving {
  search(_start, _end, callback) {
    routePlanCount += 1;
    if (drivingResultMode === "hang") return;
    if (drivingResultMode === "failure") {
      callback("error", { info: "route unavailable" });
      return;
    }
    callback("complete", { routes: [successfulRoute] });
  }
}

global.document = {
  createElement(tagName) {
    return new FakeElement(tagName);
  },
  head: {
    appendChild() {}
  }
};

global.localStorage = {
  getItem(key) {
    return storageValues.has(key) ? storageValues.get(key) : null;
  },
  setItem(key, value) {
    storageValues.set(key, value);
  },
  clear() {
    storageValues.clear();
  }
};

const fakeAMap = {
  Map: FakeMap,
  TileLayer: { Traffic: FakeTrafficLayer },
  Driving: FakeDriving,
  DrivingPolicy: { LEAST_TIME: 0 },
  Polyline: FakePolyline,
  Marker: FakeMarker
};
global.AMap = fakeAMap;

const amap = require("../src/amap-adapter.js");

function createAdapter(options = {}) {
  const container = new FakeElement();
  container.hidden = true;
  const mapWrap = { classList: new FakeClassList(), dataset: {} };
  const statuses = [];
  const routeMetas = [];
  const adapter = amap.create({
    container,
    mapWrap,
    onStatus(status) {
      statuses.push(status);
    },
    onRouteMeta(meta) {
      routeMetas.push(meta);
    },
    ...options
  });
  return { adapter, container, mapWrap, statuses, routeMetas };
}

function resetRuntime() {
  calls.length = 0;
  routePlanCount = 0;
  drivingResultMode = "complete";
  global.localStorage.clear();
}

function assertClose(actual, expected, tolerance = 1e-6) {
  assert.ok(Math.abs(actual - expected) <= tolerance, `${actual} should be within ${tolerance} of ${expected}`);
}

function currentLocalMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

async function settleWithin(promise, timeoutMs = 200) {
  const startedAt = Date.now();
  let timer;
  const timeout = new Promise((resolve) => {
    timer = setTimeout(() => resolve({ timedOut: true }), timeoutMs);
  });
  const outcome = await Promise.race([
    promise.then((value) => ({ timedOut: false, value })),
    timeout
  ]);
  clearTimeout(timer);
  return { ...outcome, elapsedMs: Date.now() - startedAt };
}

async function main() {
  assert.equal(amap.MAX_FAILURE_FALLBACK_MS, 1800);
  assert.equal(amap.boundedTimeoutMs(), 1800);
  assert.equal(amap.boundedTimeoutMs(10000), 1800, "external config cannot exceed the 2-second fallback budget");
  assert.equal(amap.boundedTimeoutMs(20), 20);
  assertClose(amap.bearing([120, 31], [120, 31.01]), 0);
  assertClose(amap.bearing([120, 31], [120.01, 31]), 90);
  assertClose(amap.screenHeading(-90, 90), 0);
  assertClose(amap.screenHeading(90, 0), 90);

  const flattened = amap.flattenDrivingPath({
    steps: [
      { path: [[0, 0], [0.001, 0], [0.001, 0]] },
      { path: [{ lng: 0.001, lat: 0 }, { lng: 0.004, lat: 0 }, ["bad", 1]] }
    ]
  });
  assert.deepEqual(flattened, [[0, 0], [0.001, 0], [0.004, 0]]);

  const geometry = amap.buildRouteGeometry(flattened);
  assert.equal(geometry.cumulative.length, 3);
  assert.ok(geometry.totalDistance > 440 && geometry.totalDistance < 450);
  const halfway = amap.locationAtProgress(geometry, 0.5);
  assertClose(halfway.point[0], 0.002, 0.00002);
  assertClose(halfway.point[1], 0);
  assert.deepEqual(halfway.passed.at(-1), halfway.point);
  assert.deepEqual(halfway.remaining[0], halfway.point);
  assert.ok(halfway.point[0] > flattened[1][0], "progress must be interpolated by distance, not point index");

  const middleSegment = amap.pathBetweenProgress(geometry, 0.25, 0.75);
  assertClose(middleSegment[0][0], 0.001, 0.00002);
  assertClose(middleSegment.at(-1)[0], 0.003, 0.00002);

  const firstMeta = amap.routeMeta(successfulRoute, 0);
  assert.equal(firstMeta.instruction, "左转进入星龙街");
  assert.equal(firstMeta.maneuver, "left");
  assert.equal(firstMeta.roadName, "星龙街");
  assert.deepEqual(firstMeta.nextDistance, { value: "1.0", unit: "公里" });
  assert.equal(firstMeta.remainingDistanceMeters, 4000);
  assert.equal(firstMeta.remainingDurationSeconds, 900);

  const secondMeta = amap.routeMeta(successfulRoute, 0.3);
  assert.equal(secondMeta.stepIndex, 1);
  assert.equal(secondMeta.instruction, "沿现代大道后右转");
  assert.equal(secondMeta.maneuver, "right");
  assert.equal(secondMeta.roadName, "现代大道");
  assert.deepEqual(secondMeta.nextDistance, { value: "800", unit: "米" });
  assert.equal(secondMeta.remainingDistanceMeters, 2800);
  assert.equal(secondMeta.remainingDurationSeconds, 630);

  resetRuntime();
  const online = createAdapter();
  const initialized = await online.adapter.init({
    mapProvider: "amap",
    amapKey: "test-key",
    amapStyle: "amap://styles/normal",
    amapMonthlyMapLimit: 10,
    amapMonthlyRouteLimit: 10
  });
  assert.equal(initialized.mode, "map_ready");
  assert.equal(online.container.hidden, false);
  assert.equal(online.adapter.getStatus(), "map_ready");

  const routeConfig = {
    start: [120.791879, 31.33468],
    end: [120.7359, 31.3048],
    originName: "博世苏州",
    destinationName: "阳光小学"
  };
  const firstPlan = await online.adapter.setRoute(routeConfig, "session-a:task-school");
  const duplicatePlan = await online.adapter.setRoute(routeConfig, "session-a:task-school");
  assert.deepEqual(firstPlan, { mode: "online", planned: true });
  assert.deepEqual(duplicatePlan, { mode: "online", planned: false });
  assert.equal(routePlanCount, 1, "same route key must not trigger another AMap.Driving search");
  assert.equal(online.adapter.getStatus(), "online");
  assert.equal(online.mapWrap.classList.contains("is-amap-online"), true);
  assert.equal(online.routeMetas[0].roadName, "星龙街");
  assert.deepEqual(online.adapter.getUsage(), {
    month: currentLocalMonth(),
    mapLoads: 1,
    routePlans: 1
  });

  online.adapter.update({
    stage: "waiting_confirmation",
    progress: 0.5,
    showVehicle: true,
    overview: false,
    driving: true,
    riskLevel: "L2",
    lateMinutes: 18
  });
  assert.equal(online.adapter.getCameraMode(), "follow");
  assert.ok(online.adapter.getCameraRotation() > 0);
  assertClose(online.adapter.overlays.vehicleMarker.angle, 0, 1e-6);
  assert.equal(
    online.adapter.overlays.vehicleContent.style.getPropertyValue("--auri-marker-counter-rotation"),
    `${-online.adapter.overlays.vehicleMarker.angle}deg`,
    "vehicle label must counter-rotate so the AURI wordmark stays horizontal"
  );
  assert.equal(online.adapter.overlays.incidentMarker.visible, true);
  assert.equal(online.adapter.overlays.incidentContent.textContent, "拥堵 · 晚到 18 分钟");
  assert.deepEqual(
    online.adapter.overlays.vehicleMarker.position,
    online.adapter.overlays.routePassed.path.at(-1),
    "vehicle position and passed-route endpoint must remain aligned"
  );
  online.adapter.clearNavigation();
  assert.equal(online.adapter.getStatus(), "offline");
  assert.equal(online.adapter.routePath.length, 0);
  assert.equal(online.mapWrap.classList.contains("is-amap-online"), false);
  assert.equal(online.container.hidden, true);
  const resumedPlan = await online.adapter.setRoute(routeConfig, "session-b:task-school");
  assert.deepEqual(resumedPlan, { mode: "online", planned: true });
  assert.equal(online.container.hidden, false, "a route created after the empty-task state must reveal the real map again");
  assert.equal(calls.some(([name]) => name === "resize"), true);

  resetRuntime();
  const mapGuard = createAdapter();
  storageValues.set(amap.USAGE_KEY, JSON.stringify({
    month: currentLocalMonth(),
    mapLoads: 1,
    routePlans: 0
  }));
  const mapGuardResult = await mapGuard.adapter.init({
    mapProvider: "amap",
    amapKey: "test-key",
    amapMonthlyMapLimit: 1,
    amapMonthlyRouteLimit: 10
  });
  assert.deepEqual(mapGuardResult, { mode: "offline", reason: "usage_guard" });
  assert.equal(mapGuard.container.hidden, true);
  assert.equal(mapGuard.statuses.at(-1).message, "已切换离线导航");
  assert.match(mapGuard.statuses.at(-1).detail, /地图调用保护/);

  resetRuntime();
  const routeGuard = createAdapter();
  await routeGuard.adapter.init({
    mapProvider: "amap",
    amapKey: "test-key",
    amapMonthlyMapLimit: 10,
    amapMonthlyRouteLimit: 1
  });
  storageValues.set(amap.USAGE_KEY, JSON.stringify({
    month: currentLocalMonth(),
    mapLoads: 1,
    routePlans: 1
  }));
  const routeGuardResult = await routeGuard.adapter.setRoute(routeConfig, "guarded-route");
  assert.deepEqual(routeGuardResult, { mode: "offline", reason: "usage_guard" });
  assert.equal(routePlanCount, 0);
  assert.equal(routeGuard.adapter.getStatus(), "offline");

  resetRuntime();
  drivingResultMode = "failure";
  const failedRoute = createAdapter();
  await failedRoute.adapter.init({
    mapProvider: "amap",
    amapKey: "test-key",
    amapMonthlyMapLimit: 10,
    amapMonthlyRouteLimit: 10
  });
  const failedRouteResult = await failedRoute.adapter.setRoute(routeConfig, "failed-route");
  assert.equal(failedRouteResult.mode, "offline");
  assert.match(failedRouteResult.reason, /route unavailable/);
  assert.equal(failedRoute.statuses.at(-1).message, "已切换离线导航");
  assert.match(failedRoute.statuses.at(-1).detail, /route unavailable/);
  assert.equal(failedRoute.container.hidden, true);
  assert.equal(failedRoute.mapWrap.classList.contains("is-amap-online"), false);

  resetRuntime();
  const offline = createAdapter();
  const offlineResult = await offline.adapter.init({ mapProvider: "auto", amapKey: "" });
  assert.deepEqual(offlineResult, { mode: "offline" });
  assert.equal(offline.container.hidden, true);
  assert.equal(offline.adapter.getStatus(), "offline");

  const timeoutFailures = [];

  resetRuntime();
  drivingResultMode = "hang";
  const hangingRoute = createAdapter();
  await hangingRoute.adapter.init({
    mapProvider: "amap",
    amapKey: "test-key",
    amapMonthlyMapLimit: 10,
    amapMonthlyRouteLimit: 10,
    amapRouteTimeoutMs: 20
  });
  const hangingRouteOutcome = await settleWithin(
    hangingRoute.adapter.setRoute(routeConfig, "hanging-route"),
    200
  );
  try {
    assert.equal(hangingRouteOutcome.timedOut, false, "hanging Driving.search must fall back within 200ms");
    assert.ok(hangingRouteOutcome.elapsedMs < 200, `route timeout fallback took ${hangingRouteOutcome.elapsedMs}ms`);
    assert.equal(hangingRouteOutcome.value.mode, "offline");
    assert.match(hangingRouteOutcome.value.reason, /超时/);
    assert.equal(hangingRoute.container.hidden, true);
    assert.equal(hangingRoute.mapWrap.classList.contains("is-amap-online"), false);
    const plansAfterFailure = routePlanCount;
    const duplicateFailure = await hangingRoute.adapter.setRoute(routeConfig, "hanging-route");
    assert.equal(duplicateFailure.mode, "offline");
    assert.equal(duplicateFailure.planned, false);
    assert.equal(routePlanCount, plansAfterFailure, "same failed route must not consume another route plan");
  } catch (error) {
    timeoutFailures.push(error);
  }

  resetRuntime();
  const loadingTimeout = createAdapter();
  delete global.AMap;
  try {
    const loadingOutcome = await settleWithin(loadingTimeout.adapter.init({
      mapProvider: "amap",
      amapKey: "test-key",
      amapMonthlyMapLimit: 10,
      amapMonthlyRouteLimit: 10,
      amapLoadTimeoutMs: 20
    }), 200);
    assert.equal(loadingOutcome.timedOut, false, "AMap script loading must fall back within 200ms");
    assert.ok(loadingOutcome.elapsedMs < 200, `AMap load timeout fallback took ${loadingOutcome.elapsedMs}ms`);
    assert.equal(loadingOutcome.value.mode, "offline");
    assert.match(loadingOutcome.value.reason, /超时/);
    assert.equal(loadingTimeout.container.hidden, true);
  } catch (error) {
    timeoutFailures.push(error);
  } finally {
    global.AMap = fakeAMap;
  }

  if (timeoutFailures.length) throw new AggregateError(timeoutFailures, "AMap timeout fallback tests failed");

  console.log("vehicle-hmi amap-adapter tests passed");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
