const assert = require("node:assert/strict");

const calls = [];
const storage = new Map();

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

class FakeMap {
  constructor(_container, options) {
    this.options = options;
  }

  add(value) {
    calls.push(["add", value]);
  }

  remove(value) {
    calls.push(["remove", value]);
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
    calls.push(["rotation", rotation]);
  }

  zoomIn() {
    calls.push(["zoom-in"]);
  }

  zoomOut() {
    calls.push(["zoom-out"]);
  }
}

class FakeTrafficLayer {
  setOpacity(value) {
    calls.push(["traffic-opacity", value]);
  }
}

class FakePolyline {
  constructor(options) {
    this.options = options;
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

class FakeDriving {
  search(start, end, callback) {
    calls.push(["route-search", start, end]);
    callback("complete", {
      routes: [{
        distance: 7800,
        time: 1080,
        steps: [{
          instruction: "左转进入学院路",
          distance: 3900,
          path: [
            [121.4382, 31.218],
            [121.452, 31.222],
            [121.47, 31.229]
          ]
        }, {
          instruction: "沿学院路行驶3.8千米靠右进入阳光大道",
          distance: 3900,
          path: [
            [121.47, 31.229],
            [121.488, 31.234],
            [121.5054, 31.2396]
          ]
        }]
      }]
    });
  }
}

global.document = {
  createElement() {
    return { className: "", innerHTML: "", textContent: "", dataset: {}, append() {} };
  },
  head: {
    appendChild() {}
  }
};

global.window = {
  localStorage: {
    getItem(key) {
      return storage.has(key) ? storage.get(key) : null;
    },
    setItem(key, value) {
      storage.set(key, value);
    }
  },
  AMap: {
    Map: FakeMap,
    TileLayer: { Traffic: FakeTrafficLayer },
    Driving: FakeDriving,
    DrivingPolicy: { LEAST_TIME: 0 },
    Polyline: FakePolyline,
    Marker: FakeMarker
  }
};

require("./amap-adapter.js");

async function main() {
  const container = { hidden: true };
  const mapWrap = { classList: new FakeClassList(), dataset: {} };
  let route = null;
  const adapter = window.AuriAmapAdapter.create({
    container,
    mapWrap,
    onRouteMeta(meta) {
      route = meta;
    }
  });

  const initialized = await adapter.init({
    mapProvider: "amap",
    amapKey: "test-key",
    amapSecurityJsCode: "test-code"
  });
  assert.equal(initialized.mode, "online");
  assert.equal(container.hidden, false);
  assert.equal(mapWrap.classList.contains("is-amap-online"), true);
  assert.equal(adapter.map.options.viewMode, "3D");
  assert.equal(adapter.map.options.pitch, 52);
  assert.equal(adapter.map.options.mapStyle, "amap://styles/whitesmoke");
  assert.equal(adapter.map.options.rotateEnable, true);
  assert.equal(adapter.map.options.pitchEnable, true);
  assert.equal(adapter.overlays.originMarker.options.anchor, "bottom-left");
  assert.equal(route.instruction, "左转进入学院路");
  assert.equal(route.maneuver, "left");
  assert.equal(route.roadName, "学院路");
  assert.deepEqual(route.nextDistance, { value: "3.9", unit: "公里" });

  adapter.update({
    stage: "waiting_confirmation",
    mapStage: "takeover",
    progress: 0.52,
    showVehicle: true,
    driving: true,
    riskLevel: "L2",
    lateMinutes: 18
  });
  assert.equal(route.instruction, "沿学院路靠右进入阳光大道");
  assert.equal(route.maneuver, "right");
  assert.deepEqual(route.nextDistance, { value: "3.7", unit: "公里" });
  assert.equal(adapter.overlays.routeRemaining.options.showDir, false);
  assert.deepEqual(
    adapter.overlays.vehicleMarker.position,
    adapter.overlays.routePassed.path[adapter.overlays.routePassed.path.length - 1]
  );
  assert.equal(adapter.overlays.originMarker.visible, false);
  assert.equal(adapter.overlays.routeChevrons.every((marker) => marker.visible), true);
  assert.equal(adapter.getCameraMode(), "follow");
  const now = new Date();
  const currentMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  assert.deepEqual(adapter.getUsage(), {
    month: currentMonth,
    mapLoads: 1,
    routePlans: 1
  });
  assert.equal(adapter.getStatus(), "online");
  assert.equal(adapter.control("zoom-in"), true);
  assert.equal(adapter.control("overview"), true);
  assert.equal(adapter.getCameraMode(), "overview");
  assert.equal(adapter.control("follow"), true);
  assert.equal(adapter.getCameraMode(), "follow");
  assert.ok(calls.some(([name]) => name === "traffic-opacity"));
  assert.ok(calls.some(([name]) => name === "polyline-path"));
  assert.ok(calls.some(([name]) => name === "zoom-in"));
  assert.ok(calls.some(([name, pitch]) => name === "pitch" && pitch >= 50));
  assert.ok(calls.some(([name]) => name === "rotation"));

  const alternateRoute = {
    start: [120.8, 31.3],
    end: [120.4, 31.5],
    originName: "新起点",
    destinationName: "新目的地"
  };
  const replanned = await adapter.setRoute(alternateRoute, "route-new");
  assert.equal(replanned.planned, true);
  assert.ok(calls.some(([name, start, end]) => name === "route-search"
    && start === alternateRoute.start && end === alternateRoute.end));
  assert.ok(calls.some(([name]) => name === "remove"));

  const guardedContainer = { hidden: false };
  const guarded = window.AuriAmapAdapter.create({
    container: guardedContainer,
    mapWrap: { classList: new FakeClassList(), dataset: {} }
  });
  const guardedResult = await guarded.init({
    mapProvider: "amap",
    amapKey: "test-key",
    amapMonthlyMapLimit: 1,
    amapMonthlyRouteLimit: 1
  });
  assert.equal(guardedResult.mode, "offline");
  assert.match(guardedResult.reason, /调用保护已触发/);
  assert.equal(guardedContainer.hidden, true);

  const fallbackContainer = { hidden: false };
  const fallbackWrap = { classList: new FakeClassList() };
  const fallback = window.AuriAmapAdapter.create({
    container: fallbackContainer,
    mapWrap: fallbackWrap
  });
  const result = await fallback.init({ mapProvider: "auto", amapKey: "" });
  assert.equal(result.mode, "offline");
  assert.equal(fallbackContainer.hidden, true);

  class NeverDriving {
    search() {}
  }
  window.AMap.Driving = NeverDriving;
  const routeTimeout = window.AuriAmapAdapter.create({
    container: { hidden: false },
    mapWrap: { classList: new FakeClassList(), dataset: {} }
  });
  const routeTimeoutResult = await routeTimeout.init({
    mapProvider: "amap",
    amapKey: "test-key",
    amapOperationTimeoutMs: 100
  });
  assert.equal(routeTimeoutResult.mode, "offline");
  assert.match(routeTimeoutResult.reason, /路线规划超时/);

  window.AMap = null;
  const sdkTimeout = window.AuriAmapAdapter.create({
    container: { hidden: false },
    mapWrap: { classList: new FakeClassList(), dataset: {} }
  });
  const sdkTimeoutResult = await sdkTimeout.init({
    mapProvider: "amap",
    amapKey: "test-key",
    amapOperationTimeoutMs: 100
  });
  assert.equal(sdkTimeoutResult.mode, "offline");
  assert.match(sdkTimeoutResult.reason, /JS API 加载超时/);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
