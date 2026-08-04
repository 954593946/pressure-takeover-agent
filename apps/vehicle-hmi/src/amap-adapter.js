(function initAuriAmapAdapter(root, factory) {
  const api = factory(root);
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.AuriAmapAdapter = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function createAmapModule(root) {
  "use strict";

  const USAGE_KEY = "auri-hmi-next-amap-usage";
  const DEFAULT_LIMITS = { mapLoads: 200, routePlans: 200 };
  const MAX_FAILURE_FALLBACK_MS = 1800;
  let loaderPromise = null;

  function boundedTimeoutMs(value) {
    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed <= 0) return MAX_FAILURE_FALLBACK_MS;
    return Math.min(MAX_FAILURE_FALLBACK_MS, Math.max(10, Math.round(parsed)));
  }

  function clamp(value, min = 0, max = 1) {
    return Math.max(min, Math.min(max, Number(value) || 0));
  }

  function pointValue(point) {
    if (!point) return null;
    if (Array.isArray(point)) return [Number(point[0]), Number(point[1])];
    if (typeof point.getLng === "function") return [point.getLng(), point.getLat()];
    if ("lng" in point && "lat" in point) return [Number(point.lng), Number(point.lat)];
    return null;
  }

  function flattenDrivingPath(route) {
    const path = [];
    (route?.steps || []).forEach((step) => {
      (step.path || []).forEach((point) => {
        const pair = pointValue(point);
        if (!pair || pair.some((value) => !Number.isFinite(value))) return;
        const previous = path[path.length - 1];
        if (!previous || previous[0] !== pair[0] || previous[1] !== pair[1]) path.push(pair);
      });
    });
    return path;
  }

  function bearing(from, to) {
    if (!from || !to) return 0;
    const latitude = ((from[1] + to[1]) / 2) * Math.PI / 180;
    const east = (to[0] - from[0]) * Math.cos(latitude);
    const north = to[1] - from[1];
    return Math.atan2(east, north) * 180 / Math.PI;
  }

  function screenHeading(heading, mapRotation = 0) {
    return ((Number(heading || 0) + Number(mapRotation || 0)) % 360 + 360) % 360;
  }

  function trafficColor(status) {
    const normalized = String(status || "").trim();
    if (/严重拥堵|极度拥堵/.test(normalized)) return "#8f2032";
    if (/拥堵/.test(normalized)) return "#d1495b";
    if (/缓行|慢行/.test(normalized)) return "#e6a700";
    if (/畅通/.test(normalized)) return "#2e9d6f";
    return "#2f6bff";
  }

  function flattenTrafficSegments(route) {
    const segments = [];
    (route?.steps || []).forEach((step) => {
      (step?.tmcs || []).forEach((tmc) => {
        const path = [];
        (tmc?.path || []).forEach((point) => {
          const pair = pointValue(point);
          if (pair && pair.every(Number.isFinite)) appendUnique(path, pair);
        });
        if (path.length > 1) segments.push({
          path,
          status: String(tmc?.status || "").trim(),
          color: trafficColor(tmc?.status)
        });
      });
    });
    return segments;
  }

  function distanceMeters(from, to) {
    const radius = 6371008.8;
    const lat1 = from[1] * Math.PI / 180;
    const lat2 = to[1] * Math.PI / 180;
    const deltaLat = (to[1] - from[1]) * Math.PI / 180;
    const deltaLng = (to[0] - from[0]) * Math.PI / 180;
    const sinLat = Math.sin(deltaLat / 2);
    const sinLng = Math.sin(deltaLng / 2);
    const value = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng;
    return radius * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
  }

  function buildRouteGeometry(path) {
    const normalizedPath = Array.isArray(path) ? path.filter((point) => Array.isArray(point) && point.length >= 2) : [];
    const cumulative = [0];
    for (let index = 1; index < normalizedPath.length; index += 1) {
      cumulative.push(cumulative[index - 1] + distanceMeters(normalizedPath[index - 1], normalizedPath[index]));
    }
    return { path: normalizedPath, cumulative, totalDistance: cumulative.at(-1) || 0 };
  }

  function appendUnique(path, point) {
    const previous = path.at(-1);
    if (!previous || previous[0] !== point[0] || previous[1] !== point[1]) path.push(point);
  }

  function locationAtProgress(geometry, progress) {
    if (!geometry?.path?.length) return { point: null, passed: [], remaining: [], heading: 0, beforeIndex: -1, afterIndex: -1 };
    if (geometry.path.length === 1) {
      return { point: geometry.path[0], passed: [geometry.path[0]], remaining: [geometry.path[0]], heading: 0, beforeIndex: 0, afterIndex: 0 };
    }
    const normalized = clamp(progress);
    const targetDistance = geometry.totalDistance * normalized;
    let afterIndex = geometry.cumulative.findIndex((distance) => distance >= targetDistance);
    if (afterIndex < 0) afterIndex = geometry.path.length - 1;
    const beforeIndex = Math.max(0, afterIndex - 1);
    const from = geometry.path[beforeIndex];
    const to = geometry.path[afterIndex] || from;
    const segmentStart = geometry.cumulative[beforeIndex] || 0;
    const segmentLength = Math.max(0, (geometry.cumulative[afterIndex] || segmentStart) - segmentStart);
    const ratio = segmentLength ? (targetDistance - segmentStart) / segmentLength : 0;
    const point = [from[0] + (to[0] - from[0]) * ratio, from[1] + (to[1] - from[1]) * ratio];
    const passed = geometry.path.slice(0, beforeIndex + 1);
    appendUnique(passed, point);
    const remaining = [point];
    geometry.path.slice(afterIndex).forEach((item) => appendUnique(remaining, item));
    return { point, passed, remaining, heading: bearing(from, to), beforeIndex, afterIndex };
  }

  function pathBetweenProgress(geometry, startProgress, endProgress) {
    const start = locationAtProgress(geometry, startProgress);
    const end = locationAtProgress(geometry, endProgress);
    if (!start.point || !end.point) return [];
    const path = [start.point];
    for (let index = start.afterIndex; index <= end.beforeIndex; index += 1) appendUnique(path, geometry.path[index]);
    appendUnique(path, end.point);
    return path;
  }

  function buildTimedMotionPath(path, durationMs, minimumSegmentMs = 16) {
    const source = Array.isArray(path) ? path.filter(Boolean) : [];
    if (!source.length) return [];
    if (source.length === 1) return [{ position: source[0], duration: 0 }];
    const totalDuration = Math.max(1, Math.round(Number(durationMs) || 1));
    const maxSegments = Math.max(1, Math.floor(totalDuration / Math.max(1, minimumSegmentMs)));
    const targetSegments = Math.min(source.length - 1, maxSegments);
    const sampled = [source[0]];
    for (let index = 1; index < targetSegments; index += 1) {
      const sourceIndex = Math.round(index * (source.length - 1) / targetSegments);
      const point = source[sourceIndex];
      if (point !== sampled.at(-1)) sampled.push(point);
    }
    if (source.at(-1) !== sampled.at(-1)) sampled.push(source.at(-1));
    const segmentCount = Math.max(1, sampled.length - 1);
    const baseDuration = Math.floor(totalDuration / segmentCount);
    const remainder = totalDuration - baseDuration * segmentCount;
    return sampled.map((position, index) => ({
      position,
      duration: index === 0 ? 0 : baseDuration + (index <= remainder ? 1 : 0)
    }));
  }

  function routeMeta(route, progress = 0) {
    const steps = (route?.steps || []).filter((step) => step?.instruction);
    const totalDistance = Number(route?.distance || 0) || steps.reduce((sum, step) => sum + Number(step.distance || 0), 0);
    const targetDistance = clamp(progress) * totalDistance;
    let covered = 0;
    let stepIndex = 0;
    for (let index = 0; index < steps.length; index += 1) {
      stepIndex = index;
      const distance = Number(steps[index].distance || 0);
      if (targetDistance <= covered + distance || index === steps.length - 1) break;
      covered += distance;
    }
    const step = steps[stepIndex];
    const remaining = Math.max(0, Number(step?.distance || 0) - Math.max(0, targetDistance - covered));
    const instruction = String(step?.instruction || "").replace(/行驶\s*\d+(?:\.\d+)?\s*(?:米|千米|公里)/g, "").trim();
    const maneuver = /掉头/.test(instruction) ? "uturn" : /左/.test(instruction) ? "left" : /右|出口|匝道/.test(instruction) ? "right" : /到达|目的地/.test(instruction) ? "arrive" : "straight";
    const roadName = String(step?.road || "").trim()
      || instruction.match(/(?:进入|沿|驶入)([^，。]+?)(?:后|行驶|靠|左转|右转|$)/)?.[1]
      || "当前道路";
    const totalDurationSeconds = Number(route?.time || 0);
    const trafficStatus = String(step?.tmcs?.find?.((item) => item?.status)?.status || "").trim() || "路况正常";
    return {
      instruction,
      maneuver,
      roadName,
      trafficStatus,
      nextDistanceMeters: remaining,
      nextDistance: remaining >= 1000
        ? { value: (remaining / 1000).toFixed(1), unit: "公里" }
        : { value: String(Math.max(50, Math.round(remaining / 10) * 10)), unit: "米" },
      totalDistanceMeters: totalDistance,
      totalDurationSeconds,
      remainingDurationSeconds: Math.max(0, Math.round(totalDurationSeconds * (1 - clamp(progress)))),
      remainingDistanceMeters: Math.max(0, totalDistance - targetDistance),
      stepIndex,
      stepCount: steps.length
    };
  }

  function currentMonth(now = new Date()) {
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  }

  function readUsage(storage = root?.localStorage) {
    const empty = { month: currentMonth(), mapLoads: 0, routePlans: 0 };
    try {
      const stored = JSON.parse(storage?.getItem(USAGE_KEY) || "null");
      if (!stored || stored.month !== empty.month) return empty;
      return {
        month: empty.month,
        mapLoads: Math.max(0, Number(stored.mapLoads || 0)),
        routePlans: Math.max(0, Number(stored.routePlans || 0))
      };
    } catch (_error) {
      return empty;
    }
  }

  function writeUsage(usage, storage = root?.localStorage) {
    try { storage?.setItem(USAGE_KEY, JSON.stringify(usage)); } catch (_error) { /* optional storage */ }
  }

  function usageLimits(config) {
    return {
      mapLoads: Math.max(1, Number(config.amapMonthlyMapLimit || DEFAULT_LIMITS.mapLoads)),
      routePlans: Math.max(1, Number(config.amapMonthlyRouteLimit || DEFAULT_LIMITS.routePlans))
    };
  }

  function recordUsage(type) {
    const usage = readUsage();
    usage[type] += 1;
    writeUsage(usage);
    return usage;
  }

  function loadAmap(config) {
    if (root.AMap) return Promise.resolve(root.AMap);
    if (loaderPromise) return loaderPromise;
    loaderPromise = new Promise((resolve, reject) => {
      root._AMapSecurityConfig = config.amapServiceHost
        ? { serviceHost: String(config.amapServiceHost).replace(/\/$/, "") }
        : { securityJsCode: String(config.amapSecurityJsCode || "").trim() };
      const script = root.document.createElement("script");
      script.async = true;
      script.dataset.auriAmap = "true";
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(config.amapKey)}&plugin=AMap.Driving,AMap.MoveAnimation`;
      const timeoutMs = boundedTimeoutMs(config.amapLoadTimeoutMs);
      let settled = false;
      let timer = null;
      const finish = (callback, value, removeScript = false) => {
        if (settled) return;
        settled = true;
        if (timer !== null) root.clearTimeout(timer);
        script.onload = null;
        script.onerror = null;
        if (removeScript) script.remove?.();
        callback(value);
      };
      script.onload = () => root.AMap
        ? finish(resolve, root.AMap)
        : finish(reject, new Error("高德地图对象不可用"), true);
      script.onerror = () => finish(reject, new Error("高德地图加载失败"), true);
      timer = root.setTimeout(
        () => finish(reject, new Error(`高德地图加载超时（${timeoutMs}ms）`), true),
        timeoutMs
      );
      root.document.head.appendChild(script);
    }).catch((error) => {
      loaderPromise = null;
      throw error;
    });
    return loaderPromise;
  }

  function markerContent(className, label) {
    const element = root.document.createElement("div");
    element.className = className;
    const mark = root.document.createElement("i");
    const text = root.document.createElement("span");
    text.textContent = label;
    element.append(mark, text);
    return element;
  }

  class AuriAmapAdapter {
    constructor({ container, mapWrap, onStatus, onRouteMeta }) {
      this.container = container;
      this.mapWrap = mapWrap;
      this.onStatus = onStatus || (() => {});
      this.onRouteMeta = onRouteMeta || (() => {});
      this.status = "offline";
      this.map = null;
      this.config = null;
      this.routeKey = null;
      this.routePath = [];
      this.routeGeometry = null;
      this.drivingRoute = null;
      this.trafficVisible = true;
      this.overlays = {};
      this.lastSnapshot = null;
      this.lastProgress = null;
      this.lastStage = null;
      this.lastCameraHeading = null;
      this.lastCameraRotation = null;
      this.lastAnimatedPoint = null;
      this.motionActive = false;
      this.motionOverlapCount = 0;
      this.motionCompletedCount = 0;
      this.lastMotionPlannedDurationMs = 0;
      this.lastRouteMetaKey = null;
      this.lastMetaProgress = null;
      this.cameraMode = "overview";
      this.native3d = null;
      this.pendingRouteKey = null;
      this.pendingRoutePromise = null;
      this.failedRouteKey = null;
      this.failedRouteReason = null;
    }

    async init(config) {
      this.config = config;
      if (config.mapProvider === "offline" || !config.amapKey) {
        this.fallback(config.mapProvider === "amap" ? "缺少高德地图配置" : "离线导航");
        return { mode: "offline" };
      }
      const usage = readUsage();
      const limits = usageLimits(config);
      if (usage.mapLoads >= limits.mapLoads) {
        this.fallback("已切换离线导航", "地图调用保护已启用");
        return { mode: "offline", reason: "usage_guard" };
      }
      this.onStatus({ mode: "loading", message: "正在连接高德地图", usage });
      try {
        const AMap = await loadAmap(config);
        const webglCapability = AMap.Browser?.isWebGL;
        this.native3d = typeof webglCapability === "function"
          ? Boolean(webglCapability.call(AMap.Browser))
          : webglCapability !== false;
        this.mapWrap.dataset.amap3d = this.native3d ? "native" : "simulated";
        this.container.hidden = false;
        recordUsage("mapLoads");
        this.map = new AMap.Map(this.container, {
          center: config.amapStart || [120.791879, 31.334680],
          zoom: 16.8,
          viewMode: "3D",
          pitch: 52,
          rotation: 0,
          mapStyle: config.amapStyle || "amap://styles/normal",
          features: ["bg", "road", "building", "point"],
          showBuildingBlock: true,
          buildingAnimation: true,
          skyColor: "#e9eef5",
          showLabel: true,
          resizeEnable: true,
          rotateEnable: true,
          pitchEnable: true,
          animateEnable: true,
          dragEnable: true,
          zoomEnable: true,
          keyboardEnable: false,
          doubleClickZoom: true
        });
        this.overlays.trafficLayer = new AMap.TileLayer.Traffic({ autoRefresh: true, interval: 180, opacity: 0.2, zIndex: 8 });
        this.map.add(this.overlays.trafficLayer);
        this.status = "map_ready";
        this.onStatus({ mode: "map_ready", message: "高德地图已连接", usage: readUsage() });
        return { mode: "map_ready" };
      } catch (error) {
        const reason = error?.message || String(error);
        this.fallback("已切换离线导航", reason);
        return { mode: "offline", reason };
      }
    }

    clearRoute() {
      const routeOverlays = Object.entries(this.overlays)
        .filter(([key]) => key !== "trafficLayer")
        .map(([, value]) => value)
        .flat()
        .filter(Boolean);
      if (routeOverlays.length) this.map?.remove?.(routeOverlays);
      const trafficLayer = this.overlays.trafficLayer;
      this.overlays = trafficLayer ? { trafficLayer } : {};
      this.routePath = [];
      this.routeGeometry = null;
      this.drivingRoute = null;
      this.lastProgress = null;
      this.lastAnimatedPoint = null;
      this.motionActive = false;
      this.motionOverlapCount = 0;
      this.motionCompletedCount = 0;
      this.lastMotionPlannedDurationMs = 0;
      this.lastRouteMetaKey = null;
      this.lastMetaProgress = null;
    }

    async setRoute(routeConfig, routeKey) {
      if (!this.map || !routeConfig?.start || !routeConfig?.end) return { mode: this.status, planned: false };
      if (this.routeKey === routeKey && this.routePath.length) return { mode: "online", planned: false };
      if (this.pendingRouteKey === routeKey && this.pendingRoutePromise) return this.pendingRoutePromise;
      if (this.failedRouteKey === routeKey) {
        return { mode: "offline", planned: false, reason: this.failedRouteReason || "route_failed" };
      }
      const usage = readUsage();
      if (usage.routePlans >= usageLimits(this.config).routePlans) {
        this.fallback("已切换离线导航", "路线调用保护已启用");
        return { mode: "offline", reason: "usage_guard" };
      }
      this.onStatus({ mode: "loading", message: "正在规划路线", usage });
      const AMap = root.AMap;
      this.pendingRouteKey = routeKey;
      this.pendingRoutePromise = (async () => {
        try {
          const route = await new Promise((resolve, reject) => {
            const timeoutMs = boundedTimeoutMs(this.config?.amapRouteTimeoutMs);
            const driving = new AMap.Driving({ policy: AMap.DrivingPolicy?.LEAST_TIME ?? 0, extensions: "all", hideMarkers: true, showTraffic: true });
            let settled = false;
            let timer = null;
            const finish = (callback, value) => {
              if (settled) return;
              settled = true;
              if (timer !== null) root.clearTimeout(timer);
              callback(value);
            };
            timer = root.setTimeout(
              () => finish(reject, new Error(`高德路线规划超时（${timeoutMs}ms）`)),
              timeoutMs
            );
            recordUsage("routePlans");
            driving.search(routeConfig.start, routeConfig.end, (status, result) => {
              const candidate = result?.routes?.[0];
              const path = flattenDrivingPath(candidate);
              if (status !== "complete" || path.length < 2) finish(reject, new Error(result?.info || "高德路线规划失败"));
              else finish(resolve, { route: candidate, path });
            });
          });
          this.clearRoute();
          this.routeKey = routeKey;
          this.drivingRoute = route.route;
          this.routePath = route.path;
          this.routeGeometry = buildRouteGeometry(route.path);
          this.failedRouteKey = null;
          this.failedRouteReason = null;
          this.drawRoute(AMap, routeConfig);
          this.status = "online";
          this.container.hidden = false;
          this.mapWrap.classList.add("is-amap-online");
          this.map.resize?.();
          const meta = routeMeta(this.drivingRoute, 0);
          this.lastRouteMetaKey = `${meta.stepIndex}:${meta.nextDistance.value}:${meta.nextDistance.unit}`;
          this.onRouteMeta(meta);
          this.onStatus({ mode: "online", message: "高德导航", usage: readUsage() });
          if (this.lastSnapshot) this.update(this.lastSnapshot);
          return { mode: "online", planned: true };
        } catch (error) {
          this.failedRouteKey = routeKey;
          this.failedRouteReason = error?.message || String(error);
          this.fallback("已切换离线导航", this.failedRouteReason);
          return { mode: "offline", reason: error?.message || String(error) };
        } finally {
          if (this.pendingRouteKey === routeKey) {
            this.pendingRouteKey = null;
            this.pendingRoutePromise = null;
          }
        }
      })();
      return this.pendingRoutePromise;
    }

    drawRoute(AMap, routeConfig) {
      const common = { path: this.routePath, lineJoin: "round", lineCap: "round", borderWeight: 0, showDir: false };
      this.overlays.routeShadow = new AMap.Polyline({ ...common, strokeColor: "#ffffff", strokeOpacity: 0.96, strokeWeight: 22, zIndex: 45 });
      this.overlays.routeBase = new AMap.Polyline({ ...common, strokeColor: "#0b1b33", strokeOpacity: 0.2, strokeWeight: 16, zIndex: 46 });
      this.overlays.routeRemaining = new AMap.Polyline({ ...common, strokeColor: "#2f6bff", strokeOpacity: 1, strokeWeight: 11, zIndex: 48 });
      this.overlays.routePassed = new AMap.Polyline({ ...common, path: this.routePath.slice(0, 2), strokeColor: "#aab4be", strokeOpacity: 0, strokeWeight: 11, zIndex: 49 });
      this.overlays.routeTrafficSegments = flattenTrafficSegments(this.drivingRoute).map((segment) => new AMap.Polyline({
        ...common,
        path: segment.path,
        strokeColor: segment.color,
        strokeOpacity: 0.92,
        strokeWeight: 12,
        zIndex: 50
      }));
      this.overlays.routeCongestionBands = [
        ["#e6a700", 51],
        ["#d1495b", 52],
        ["#8f2032", 53]
      ].map(([strokeColor, zIndex]) => new AMap.Polyline({
        ...common,
        path: this.routePath.slice(0, 2),
        strokeColor,
        strokeOpacity: 0,
        strokeWeight: 12,
        zIndex
      }));

      const vehicle = root.document.createElement("div");
      vehicle.className = "auri-amap-vehicle";
      vehicle.innerHTML = '<span class="auri-amap-vehicle-ring"></span><i></i><b>AURI</b>';
      this.overlays.vehicleContent = vehicle;
      this.overlays.vehicleMarker = new AMap.Marker({ position: this.routePath[0], content: vehicle, anchor: "center", zIndex: 130 });
      this.overlays.vehicleMarker.on?.("moving", (event) => {
        const point = pointValue(event?.pos)
          || pointValue(event?.target?.getPosition?.())
          || pointValue(this.overlays.vehicleMarker?.getPosition?.());
        if (!point) return;
        const previous = this.lastAnimatedPoint;
        if (previous && (previous[0] !== point[0] || previous[1] !== point[1])) {
          this.setVehicleHeading(bearing(previous, point));
        }
        this.lastAnimatedPoint = point;
      });
      this.overlays.vehicleMarker.on?.("moveend", () => {
        this.motionActive = false;
        this.motionCompletedCount += 1;
      });
      this.overlays.originMarker = new AMap.Marker({ position: this.routePath[0], content: markerContent("auri-amap-origin", routeConfig.originName || "博世苏州"), anchor: "bottom-left", zIndex: 109 });
      this.overlays.destinationMarker = new AMap.Marker({ position: this.routePath.at(-1), content: markerContent("auri-amap-destination", routeConfig.destinationName || "目的地"), anchor: "bottom-center", zIndex: 110 });
      const incident = markerContent("auri-amap-incident", "前方拥堵");
      this.overlays.incidentContent = incident.querySelector("span");
      this.overlays.incidentMarker = new AMap.Marker({ position: this.routePath[Math.floor(this.routePath.length * 0.7)], content: incident, anchor: "top-center", zIndex: 120 });
      this.overlays.incidentMarker.hide();
      this.overlays.routeChevrons = [0, 1, 2].map((index) => {
        const chevron = root.document.createElement("div");
        chevron.className = `auri-amap-chevron is-${index + 1}`;
        chevron.setAttribute?.("aria-hidden", "true");
        const marker = new AMap.Marker({ position: this.routePath[0], content: chevron, anchor: "center", zIndex: 82 - index });
        marker.hide();
        return marker;
      });
      this.map.add([
        this.overlays.routeShadow,
        this.overlays.routeBase,
        this.overlays.routeRemaining,
        this.overlays.routePassed,
        ...this.overlays.routeTrafficSegments,
        ...this.overlays.routeCongestionBands,
        this.overlays.vehicleMarker,
        this.overlays.originMarker,
        this.overlays.destinationMarker,
        this.overlays.incidentMarker,
        ...this.overlays.routeChevrons
      ]);
      this.applyOverviewCamera();
    }

    applyOverviewCamera() {
      if (!this.map || !this.overlays.routeShadow) return;
      this.cameraMode = "overview";
      this.mapWrap.dataset.cameraMode = "overview";
      this.map.setFitView([this.overlays.originMarker, this.overlays.routeShadow, this.overlays.destinationMarker], true, [110, 130, 155, 105], 16);
      this.map.setPitch?.(20, false, 520);
      this.map.setRotation?.(0, false, 520);
      this.mapWrap.style?.setProperty?.("--auri-sim-map-rotation", "0deg");
      this.lastCameraRotation = 0;
    }

    applyFollowCamera(snapshot, location, force = false) {
      if (!this.map || !location) return;
      const heading = Number(location.heading || 0);
      const rawDelta = this.lastCameraHeading === null ? 360 : Math.abs(heading - this.lastCameraHeading) % 360;
      const delta = Math.min(rawDelta, 360 - rawDelta);
      const attention = ["takeover_L2", "takeover_L3", "planning", "waiting_confirmation"].includes(snapshot.stage);
      const lookAhead = locationAtProgress(this.routeGeometry, Math.min(1, Number(snapshot.progress || 0) + 0.02));
      const meta = routeMeta(this.drivingRoute, snapshot.progress);
      const navigationZoom = meta.nextDistanceMeters <= 260 ? 18.15 : meta.nextDistanceMeters <= 900 ? 17.8 : 17.45;
      const cameraDuration = force ? 620 : Math.max(420, Math.min(720, Number(snapshot.motionDurationMs || 640)));
      const targetRotation = ((360 - heading) % 360 + 360) % 360;
      this.cameraMode = "follow";
      this.mapWrap.dataset.cameraMode = "follow";
      this.map.setZoomAndCenter(attention ? Math.min(navigationZoom, 17.65) : navigationZoom, lookAhead.point, false, cameraDuration);
      this.map.setPitch?.(attention ? 55 : 61, false, cameraDuration);
      if (force || delta >= 1.5 || this.lastCameraRotation === null) {
        this.map.setRotation?.(targetRotation, false, cameraDuration);
        this.mapWrap.style?.setProperty?.("--auri-sim-map-rotation", `${targetRotation}deg`);
        this.lastCameraHeading = heading;
        this.lastCameraRotation = targetRotation;
      }
    }

    setVehicleHeading(heading) {
      const cameraRotation = this.native3d === false
        ? 0
        : this.lastSnapshot?.overview
          ? Number(this.map?.getRotation?.() || 0)
          : Number(this.lastCameraRotation ?? this.map?.getRotation?.() ?? 0);
      const markerAngle = screenHeading(heading, cameraRotation);
      this.overlays.vehicleContent?.style?.setProperty("--auri-vehicle-heading", `${markerAngle}deg`);
    }

    updateChevrons(snapshot) {
      if (!this.overlays.routeChevrons || !this.routeGeometry) return;
      const visible = snapshot.showVehicle && !snapshot.overview;
      this.overlays.routeChevrons.forEach((marker, index) => {
        if (!visible) return marker.hide();
        const location = locationAtProgress(this.routeGeometry, Math.min(0.99, snapshot.progress + 0.008 + index * 0.008));
        marker.setPosition(location.point);
        marker.setAngle?.(screenHeading(location.heading, this.map?.getRotation?.() || 0));
        marker.show();
      });
    }

    update(snapshot) {
      this.lastSnapshot = snapshot;
      if (this.status !== "online" || !this.routeGeometry) return;
      const progress = clamp(snapshot.progress);
      const location = locationAtProgress(this.routeGeometry, progress);
      const fallback = location.remaining.length > 1 ? location.remaining.slice(0, 2) : location.passed.slice(-2);
      this.overlays.routePassed.setOptions({ strokeOpacity: location.passed.length > 1 ? 1 : 0 });
      this.overlays.routePassed.setPath(location.passed.length > 1 ? location.passed : fallback);
      this.overlays.routeRemaining.setOptions({ strokeOpacity: location.remaining.length > 1 ? 1 : 0 });
      this.overlays.routeRemaining.setPath(location.remaining.length > 1 ? location.remaining : fallback);

      const riskActive = ["L2", "L3"].includes(snapshot.riskLevel);
      const completed = ["action_completed", "cooldown", "parked_review"].includes(snapshot.stage);
      const congestionRanges = [[0.012, 0.04], [0.04, 0.068], [0.068, 0.094]];
      this.overlays.routeCongestionBands.forEach((band, index) => {
        const [startOffset, endOffset] = congestionRanges[index];
        const path = pathBetweenProgress(this.routeGeometry, Math.min(1, progress + startOffset), Math.min(1, progress + endOffset));
        band.setOptions({ strokeOpacity: riskActive && this.trafficVisible ? 1 : 0 });
        band.setPath(riskActive && path.length > 1 ? path : fallback);
      });
      this.overlays.routeTrafficSegments.forEach((segment) => segment.setOptions({ strokeOpacity: this.trafficVisible ? 0.92 : 0 }));
      if (riskActive) {
        this.overlays.incidentContent.textContent = snapshot.stopped
          ? `严重拥堵 · 已停车等待`
          : snapshot.lateMinutes ? `拥堵 · 晚到 ${snapshot.lateMinutes} 分钟` : "前方拥堵";
        this.overlays.incidentMarker.setPosition(locationAtProgress(this.routeGeometry, Math.min(1, progress + 0.04)).point);
        this.overlays.incidentMarker.show();
        this.overlays.trafficLayer.setOpacity(this.trafficVisible ? 0.5 : 0);
      } else {
        this.overlays.incidentMarker.hide();
        this.overlays.trafficLayer.setOpacity(this.trafficVisible ? (snapshot.driving ? 0.3 : 0.16) : 0);
      }

      const stageChanged = snapshot.stage !== this.lastStage;
      const progressChanged = this.lastProgress === null || Math.abs(progress - this.lastProgress) >= 0.00005;
      if (snapshot.overview) {
        if (stageChanged || this.cameraMode !== "overview") this.applyOverviewCamera();
      } else if (stageChanged || progressChanged || this.cameraMode !== "follow") {
        this.applyFollowCamera(snapshot, location, stageChanged || this.cameraMode !== "follow");
      }

      if (snapshot.showVehicle) {
        this.overlays.vehicleMarker.show();
        if (this.lastProgress !== null && progressChanged && progress > this.lastProgress && typeof this.overlays.vehicleMarker.moveAlong === "function") {
          const motionPath = pathBetweenProgress(this.routeGeometry, this.lastProgress, progress);
          const currentPosition = pointValue(this.overlays.vehicleMarker.getPosition?.());
          if (currentPosition && motionPath.length) motionPath[0] = currentPosition;
          this.lastAnimatedPoint = motionPath[0] || currentPosition;
          this.setVehicleHeading(motionPath.length > 1 ? bearing(motionPath[0], motionPath[1]) : location.heading);
          const totalDuration = Math.max(120, Number(snapshot.motionDurationMs || 640));
          const timedPath = buildTimedMotionPath(motionPath, totalDuration);
          this.lastMotionPlannedDurationMs = timedPath.reduce((sum, item) => sum + item.duration, 0);
          if (this.motionActive) {
            this.motionOverlapCount += 1;
            this.overlays.vehicleMarker.stopMove?.();
          }
          this.motionActive = true;
          this.overlays.vehicleMarker.moveAlong(timedPath, { autoRotation: false });
          this.lastMotionMethod = "moveAlong";
        } else if (this.lastProgress !== null && progressChanged && typeof this.overlays.vehicleMarker.moveTo === "function") {
          if (this.motionActive) this.overlays.vehicleMarker.stopMove?.();
          this.motionActive = false;
          this.setVehicleHeading(location.heading);
          this.overlays.vehicleMarker.moveTo(location.point, { duration: Math.min(240, Number(snapshot.motionDurationMs || 640)), autoRotation: false });
          this.lastAnimatedPoint = location.point;
          this.lastMotionMethod = "moveTo";
        } else if (this.lastProgress === null) {
          this.setVehicleHeading(location.heading);
          this.overlays.vehicleMarker.setPosition(location.point);
          this.lastAnimatedPoint = location.point;
        } else if (!progressChanged) this.setVehicleHeading(location.heading);
      } else this.overlays.vehicleMarker.hide();
      if (snapshot.overview) this.overlays.originMarker.show();
      else this.overlays.originMarker.hide();

      this.updateChevrons(snapshot);
      const meta = routeMeta(this.drivingRoute, progress);
      const key = `${meta.stepIndex}:${meta.nextDistance.value}:${meta.nextDistance.unit}`;
      if (meta.instruction && (key !== this.lastRouteMetaKey || Math.abs(progress - (this.lastMetaProgress ?? -1)) >= 0.002)) {
        this.lastRouteMetaKey = key;
        this.lastMetaProgress = progress;
        this.onRouteMeta(meta);
      }
      this.lastProgress = progress;
      this.lastStage = snapshot.stage;
    }

    control(action) {
      if (this.status !== "online" || !this.map) return false;
      if (action === "zoom-in") this.map.zoomIn();
      else if (action === "zoom-out") this.map.zoomOut();
      else if (action === "overview") this.applyOverviewCamera();
      else if (action === "traffic") {
        this.trafficVisible = !this.trafficVisible;
        if (this.lastSnapshot) this.update(this.lastSnapshot);
      }
      else if (action === "follow" && this.lastSnapshot && this.routeGeometry) {
        this.applyFollowCamera(this.lastSnapshot, locationAtProgress(this.routeGeometry, this.lastSnapshot.progress), true);
      } else return false;
      return true;
    }

    clearNavigation(message = "等待手机同步路线") {
      this.clearRoute();
      this.routeKey = null;
      this.lastSnapshot = null;
      this.lastStage = null;
      this.fallback(message);
    }

    fallback(message, detail = null) {
      this.status = "offline";
      this.overlays.vehicleMarker?.stopMove?.();
      this.motionActive = false;
      this.container.hidden = true;
      this.mapWrap.classList.remove("is-amap-online");
      this.onStatus({ mode: "offline", message, detail, usage: readUsage() });
    }

    getStatus() { return this.status; }
    getUsage() { return readUsage(); }
    getCameraMode() { return this.cameraMode; }
    isTrafficVisible() { return this.trafficVisible; }
    getCameraHeading() { return this.lastCameraHeading; }
    getCameraPitch() { return this.map?.getPitch?.() ?? null; }
    getCameraRotation() { return this.map?.getRotation?.() ?? null; }
    get3dMode() { return this.native3d === false ? "simulated" : "native"; }
    getMotionMethod() { return this.lastMotionMethod || "position"; }
    getMotionDiagnostics() {
      return {
        active: this.motionActive,
        overlapCount: this.motionOverlapCount,
        completedCount: this.motionCompletedCount,
        plannedDurationMs: this.lastMotionPlannedDurationMs
      };
    }
  }

  return {
    MAX_FAILURE_FALLBACK_MS,
    USAGE_KEY,
    bearing,
    boundedTimeoutMs,
    buildTimedMotionPath,
    buildRouteGeometry,
    create(options) { return new AuriAmapAdapter(options); },
    flattenDrivingPath,
    flattenTrafficSegments,
    locationAtProgress,
    pathBetweenProgress,
    routeMeta,
    screenHeading,
    trafficColor
  };
});
