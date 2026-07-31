(function initAuriCockpit() {
  "use strict";

  const model = window.AuriWorldStateModel;
  const agentModule = window.AuriAgentClient;
  const amapModule = window.AuriAmapAdapter;
  if (!model || !agentModule || !amapModule) {
    console.error("[AURI] World State modules are unavailable");
    return;
  }

  const ROUTE_ORIGIN = { name: "博世苏州 · 星龙街455号", coordinates: [120.791879, 31.334680] };
  const DEMO_DESTINATIONS = [
    { pattern: /(阳光小学|学校|接孩子|接送)/, name: "阳光小学", coordinates: [120.7359, 31.3048] },
    { pattern: /(苏州中心|东方之门)/, name: "苏州中心", coordinates: [120.6677, 31.3181] },
    { pattern: /(超市|采购|商超)/, name: "邻里生鲜超市", coordinates: [120.7506, 31.3147] }
  ];

  const POIS = [
    ["●", "当前位置", "home"],
    ["↗", "路线接续", ""],
    ["▦", "行程观察", ""],
    ["◉", "ETA 更新", ""],
    ["◷", "风险预测", "warning"],
    ["⇄", "手机任务同步", ""],
    ["A", "AURI 处理", "processing"],
    ["✓", "方案准备", "processing"],
    ["○", "等待授权", "warning"],
    ["✓", "行程继续", "success"]
  ];
  const STAGE_PROGRESS = {
    off_vehicle_idle: 0.03,
    pre_departure_warning: 0.08,
    handover_to_vehicle: 0.16,
    vehicle_observation: 0.32,
    takeover_L2: 0.46,
    takeover_L3: 0.5,
    planning: 0.58,
    service_prepared: 0.66,
    waiting_confirmation: 0.7,
    executing: 0.8,
    service_executed: 0.86,
    action_completed: 0.91,
    cooldown: 0.95,
    parked_review: 0.98,
    error: 0.03
  };
  const STATUS_VIEW = {
    idle: ["等待连接", "idle"],
    preflighting: ["正在连接", "processing"],
    healthy: ["服务已就绪", "processing"],
    connecting: ["同步中", "processing"],
    streaming: ["实时同步", "success"],
    polling_fallback: ["正在重连", "warning"],
    auth_required: ["需要配置", "warning"],
    schema_incompatible: ["版本不兼容", "critical"],
    stopped: ["已断开", "idle"]
  };
  const MAP_STATUS_VIEW = {
    offline: ["离线导航", "offline"],
    loading: ["路线载入中", "loading"],
    map_ready: ["地图已连接", "loading"],
    online: ["高德实时导航", "online"]
  };

  let viewModel = model.buildVehicleHmiViewModel(null);
  let activeSection = null;
  let connectionStatus = { type: "idle" };
  let lastAnimatedStage = null;
  let lastError = null;
  let routeMeta = null;
  let mapStatus = { mode: "offline", message: "离线导航" };
  let mapConfigReady = false;
  let mapInitPromise = null;

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function row(icon, title, detail, state, tone = "") {
    return `
      <div class="auri-shell-row${tone ? ` is-${tone}` : ""}">
        <span class="auri-shell-row-icon" aria-hidden="true">${escapeHtml(icon)}</span>
        <span class="auri-shell-row-copy"><strong>${escapeHtml(title)}</strong><span>${escapeHtml(detail)}</span></span>
        <span class="auri-shell-row-state">${escapeHtml(state)}</span>
      </div>
    `;
  }

  function emptyRow(icon, title, detail) {
    return row(icon, title, detail, "等待");
  }

  function panelFor(section) {
    const vm = viewModel;
    if (section === "auri") {
      const conclusion = vm.agentOutput.available
        ? vm.agentOutput.preview
        : vm.tasks.total
          ? `${vm.lifecycle.stageLabel}，AURI 正在持续检查任务与行程。`
          : "等待用户在手机端创建今天的任务。";
      return {
        title: vm.lifecycle.stageLabel === "数据不可用" ? "AURI 已就绪" : vm.lifecycle.stageLabel,
        subtitle: "你只管开，我来处理",
        lead: conclusion,
        copy: vm.utterance.available
          ? `来自${vm.utterance.sourceLabel}：“${vm.utterance.preview}”`
          : "手机语音会随 Agent World State 自动同步到车机。",
        status: vm.risk.label,
        tone: vm.risk.tone,
        rows: [
          vm.utterance.available
            ? row("声", "手机语音", vm.utterance.preview, vm.utterance.receivedAtLabel || "刚刚", "processing")
            : emptyRow("声", "手机语音", "等待用户在手机端求助"),
          row(vm.wearable.connected ? "腕" : "○", "腕上设备", vm.wearable.connected ? vm.wearable.text : "尚未连接", vm.wearable.connected ? vm.wearable.modeLabel : "离线", vm.wearable.mode)
        ]
      };
    }

    if (section === "tasks") {
      const taskRows = vm.tasks.items.map((task) => row(
        task.tone === "rigid" ? "刚" : "弹",
        task.displayTitle,
        task.location || (task.waitingParty.length ? task.waitingParty.join("、") : task.type),
        task.status,
        task.tone
      ));
      return {
        title: "今日任务",
        subtitle: vm.tasks.total ? `${vm.tasks.rigid} 项刚性 · ${vm.tasks.flexible} 项弹性` : "来自手机与 Agent",
        lead: vm.tasks.total ? `${vm.tasks.total} 项任务已同步` : "目前没有已同步任务",
        copy: vm.tasks.total
          ? "任务顺序、类型、时间和状态均来自当前 Agent World State。"
          : "请在手机端通过语音创建任务，车机会在状态更新后自动接续。",
        status: vm.tasks.completed ? `${vm.tasks.completed}/${vm.tasks.total} 已完成` : `${vm.tasks.total} 项`,
        tone: vm.tasks.total ? "processing" : "idle",
        rows: taskRows.length ? taskRows : [emptyRow("＋", "任务入口", "等待手机语音创建任务")]
      };
    }

    if (section === "messages") {
      const actionRows = vm.actions.items.map((action) => row(
        action.type === "message" ? "信" : action.type === "service_order" ? "单" : "调",
        action.target || "Agent 动作",
        action.preview,
        action.statusLabel,
        action.status === "completed" ? "completed" : action.status === "failed" || action.status === "blocked" ? "error" : "processing"
      ));
      return {
        title: "消息与执行",
        subtitle: vm.actions.counts.total ? `${vm.actions.counts.completed}/${vm.actions.counts.total} 已完成` : "等待 Agent 方案",
        lead: vm.actions.counts.total ? `${vm.actions.counts.total} 项动作已准备或执行` : "暂无 Agent 动作",
        copy: vm.agentOutput.available ? vm.agentOutput.fullText : "消息、任务调整和生活服务均以 Agent 返回的执行事实为准。",
        status: vm.actions.counts.failed || vm.actions.counts.blocked ? "需要注意" : vm.actions.counts.total ? "状态已同步" : "等待",
        tone: vm.actions.counts.failed || vm.actions.counts.blocked ? "critical" : vm.actions.counts.completed === vm.actions.counts.total && vm.actions.counts.total ? "success" : "processing",
        rows: actionRows.length ? actionRows : [emptyRow("□", "消息与服务", "等待 Agent 生成处理方案")]
      };
    }

    if (section === "vehicle") {
      const climate = vm.vehicle;
      const heartRate = vm.wearable.heartRate ? `${vm.wearable.heartRate} bpm` : "未提供";
      return {
        title: "座舱状态",
        subtitle: "车辆与随行设备",
        lead: climate.available ? climate.summary : "等待座舱状态同步",
        copy: "座舱能力与腕上设备状态均随当前 Agent World State 只读更新。",
        status: climate.available ? "状态已同步" : "等待",
        tone: climate.available ? "success" : "idle",
        rows: [
          row("温", "空调与温度", climate.available ? `${climate.mode} · ${climate.fan}` : "暂无有效车辆数据", climate.temperatureLabel, climate.acOn ? "processing" : "idle"),
          row("腕", "腕上设备", vm.wearable.connected ? `${vm.wearable.text} · 心率 ${heartRate}` : "尚未连接", vm.wearable.connected ? vm.wearable.modeLabel : "离线", vm.wearable.mode)
        ]
      };
    }

    return null;
  }

  function connectionPanel() {
    const config = client.getConfig();
    const statusLabel = STATUS_VIEW[connectionStatus.type]?.[0] || "等待连接";
    return {
      title: "连接 Agent",
      subtitle: statusLabel,
      lead: "选择本地或公网 Agent 服务",
      copy: lastError ? `最近一次连接未成功：${lastError}` : "配置只保存在当前浏览器，不会写入项目代码或显示在演示界面。",
      status: statusLabel,
      tone: STATUS_VIEW[connectionStatus.type]?.[1] || "idle",
      form: `
        <form class="auri-config-form" id="auri-config-form">
          <label><span>Agent API</span><input id="auri-config-api" type="url" spellcheck="false" value="${escapeHtml(config.apiBase)}" required></label>
          <label><span>Team Token</span><input id="auri-config-token" type="password" autocomplete="off" value="${escapeHtml(config.token)}" placeholder="仅保存在当前浏览器"></label>
          <div class="auri-config-presets">
            <button type="button" data-api="https://auri-agent-api.onrender.com">公网服务</button>
            <button type="button" data-api="https://auri-langchain-agent-api.onrender.com">LangChain 服务</button>
            <button type="button" data-api="http://127.0.0.1:8000">本地服务</button>
          </div>
          <details class="auri-map-config">
            <summary>地图连接设置 <span>${escapeHtml(MAP_STATUS_VIEW[mapStatus.mode]?.[0] || "离线导航")}</span></summary>
            <label><span>地图模式</span><select id="auri-config-map-provider">
              <option value="auto"${config.mapProvider === "auto" ? " selected" : ""}>自动读取 Agent 配置</option>
              <option value="amap"${config.mapProvider === "amap" ? " selected" : ""}>高德 Web JS API</option>
              <option value="offline"${config.mapProvider === "offline" ? " selected" : ""}>Bosch 离线地图</option>
            </select></label>
            <label><span>高德 Web Key</span><input id="auri-config-amap-key" type="password" autocomplete="off" value="${escapeHtml(config.amapKey)}" placeholder="仅保存在当前浏览器"></label>
            <label><span>高德安全码（本机诊断）</span><input id="auri-config-amap-security" type="password" autocomplete="off" value="${escapeHtml(config.amapSecurityJsCode)}" placeholder="生产环境请使用 Agent 安全代理"></label>
            <label><span>安全代理地址</span><input id="auri-config-amap-host" type="url" spellcheck="false" value="${escapeHtml(config.amapServiceHost)}" placeholder="由 /v1/map-config 自动提供"></label>
          </details>
          <button class="auri-config-submit" type="submit">保存并连接</button>
        </form>
      `
    };
  }

  function closePanel() {
    const panel = document.getElementById("left-panel");
    panel?.classList.remove("is-visible", "auri-shell-panel");
    activeSection = null;
    document.querySelectorAll("[data-auri-section]").forEach((item) => {
      item.classList.toggle("active", item.dataset.auriSection === "navigation");
    });
  }

  function bindConfigForm() {
    const form = document.getElementById("auri-config-form");
    if (!form) return;
    form.querySelectorAll("[data-api]").forEach((button) => {
      button.addEventListener("click", () => {
        const input = document.getElementById("auri-config-api");
        if (input) input.value = button.dataset.api;
      });
    });
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      agentModule.saveConfig({
        ...client.getConfig(),
        apiBase: document.getElementById("auri-config-api")?.value,
        token: document.getElementById("auri-config-token")?.value,
        mapProvider: document.getElementById("auri-config-map-provider")?.value,
        amapKey: document.getElementById("auri-config-amap-key")?.value,
        amapSecurityJsCode: document.getElementById("auri-config-amap-security")?.value,
        amapServiceHost: document.getElementById("auri-config-amap-host")?.value
      });
      window.location.reload();
    });
  }

  function openPanel(section) {
    if (section === "navigation") {
      closePanel();
      return;
    }
    const config = section === "connection" ? connectionPanel() : panelFor(section);
    const panel = document.getElementById("left-panel");
    if (!config || !panel) return;

    activeSection = section;
    panel.className = "left-panel is-visible auri-shell-panel";
    const icon = panel.querySelector(".lp-agent-icon");
    if (icon) icon.src = "icons/auri-icon.png";
    const title = document.getElementById("hdr-a");
    const subtitle = document.getElementById("sub-a");
    const body = document.getElementById("body-a");
    if (title) title.textContent = config.title;
    if (subtitle) subtitle.textContent = config.subtitle;
    if (body) body.innerHTML = `
      <div class="auri-shell-content">
        <p class="auri-shell-lead">${escapeHtml(config.lead)}</p>
        <p class="auri-shell-copy">${escapeHtml(config.copy)}</p>
        <span class="auri-shell-status is-${escapeHtml(config.tone || "idle")}">${escapeHtml(config.status)}</span>
        ${config.form || config.rows.join("")}
      </div>
    `;
    const secondaryHeader = document.getElementById("lp-b-hdr");
    const secondaryBody = document.getElementById("body-b");
    if (secondaryHeader) secondaryHeader.style.display = "none";
    if (secondaryBody) secondaryBody.style.display = "none";

    let close = panel.querySelector(".auri-panel-close");
    if (!close) {
      close = document.createElement("button");
      close.className = "auri-panel-close";
      close.type = "button";
      close.title = "关闭";
      close.setAttribute("aria-label", "关闭");
      close.textContent = "×";
      close.addEventListener("click", closePanel);
      panel.appendChild(close);
    }

    document.querySelectorAll("[data-auri-section]").forEach((item) => {
      item.classList.toggle("active", item.dataset.auriSection === section);
    });
    bindConfigForm();
  }

  function replaceCarBranding() {
    const scene = document.getElementById("scene3d");
    if (!scene || scene.querySelector(".auri-car-mark")) return;
    const badge = document.createElement("span");
    badge.className = "auri-car-mark auri-car-mark--badge";
    badge.textContent = "A";
    const plate = document.createElement("span");
    plate.className = "auri-car-mark auri-car-mark--plate";
    plate.textContent = "AURI";
    scene.append(badge, plate);
  }

  function replaceRoutePOIs() {
    const nodes = [...document.querySelectorAll(".map-poi-layer .map-poi")];
    nodes.forEach((node, index) => {
      const config = POIS[index];
      if (!config) {
        node.remove();
        return;
      }
      const [icon, label, tone] = config;
      node.className.baseVal = `map-poi${tone ? ` map-poi-${tone}` : ""}`;
      const iconNode = node.querySelector(".poi-ico");
      const labelNode = node.querySelector(".poi-tag");
      if (iconNode) iconNode.textContent = icon;
      if (labelNode) labelNode.textContent = label;
    });
  }

  function updateRoutePOIs() {
    const nodes = [...document.querySelectorAll(".map-poi-layer .map-poi")];
    const destination = viewModel.navigation.hasDestination ? viewModel.navigation.destination : "等待路线";
    const lastLabel = nodes.at(-1)?.querySelector(".poi-tag");
    if (lastLabel) lastLabel.textContent = destination;
    const warningLabel = nodes[4]?.querySelector(".poi-tag");
    if (warningLabel) warningLabel.textContent = viewModel.risk.lateMinutes ? `预计晚到 ${viewModel.risk.lateMinutes} 分钟` : viewModel.risk.label;
  }

  function prepareTopBar() {
    const play = document.getElementById("playbtn");
    play?.removeAttribute("onclick");
    const source = [...document.querySelectorAll(".tb-mic")].find((item) => item.id !== "tb-mute");
    if (source) {
      source.removeAttribute("onclick");
      source.title = "手机语音同步";
      source.setAttribute("aria-label", "手机语音同步状态");
      source.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="7" y="2.5" width="10" height="19" rx="2"/><path d="M10 6h4M10 18h4"/></svg>';
      source.addEventListener("click", () => openPanel("auri"));
    }
    const status = document.getElementById("tb-offline");
    if (status) {
      status.classList.add("show");
      status.setAttribute("role", "button");
      status.setAttribute("tabindex", "0");
      status.addEventListener("click", () => openPanel("connection"));
      status.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") openPanel("connection");
      });
    }
  }

  function prepareClimateControls() {
    document.querySelectorAll(".bb-arr").forEach((item) => {
      item.removeAttribute("onclick");
      item.setAttribute("aria-disabled", "true");
      item.title = "空调状态由 Agent 同步";
    });
  }

  function bindDock() {
    document.querySelectorAll("[data-auri-section]").forEach((item) => {
      item.setAttribute("role", "button");
      item.setAttribute("tabindex", "0");
      const activate = (event) => {
        event.preventDefault();
        event.stopPropagation();
        openPanel(item.dataset.auriSection);
      };
      item.addEventListener("click", activate);
      item.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") activate(event);
      });
    });
  }

  function disableLegacyDemoShortcuts() {
    document.addEventListener("keydown", (event) => {
      if (["Space", "ArrowLeft", "ArrowRight"].includes(event.code)) event.stopImmediatePropagation();
      if (event.key === "Escape") closePanel();
    }, true);
  }

  function renderConnectionStatus(next) {
    connectionStatus = next;
    const [label, tone] = STATUS_VIEW[next.type] || STATUS_VIEW.idle;
    const chip = document.getElementById("tb-offline");
    if (chip) {
      chip.textContent = label;
      chip.dataset.tone = tone;
      chip.title = `${label} · 点击配置 Agent`;
    }
    if (activeSection === "connection") openPanel("connection");
  }

  function renderNavigation() {
    const vm = viewModel;
    const card = document.getElementById("vd-nav-card");
    const headline = document.getElementById("vd-nav-distance");
    const instruction = document.getElementById("vd-nav-instruction");
    const destination = document.getElementById("vd-nav-dest");
    const eta = document.getElementById("vd-nav-eta");
    const minutes = document.getElementById("vd-nav-min");
    const kilometers = document.getElementById("vd-nav-km");
    const progress = document.getElementById("vd-nav-progress");
    const stageProgress = STAGE_PROGRESS[vm.lifecycle.stage] ?? 0.03;
    if (card) {
      card.dataset.risk = vm.risk.tone;
      card.classList.toggle("is-default", !vm.navigation.hasDestination);
    }
    if (headline) {
      headline.innerHTML = routeMeta?.nextDistance
        ? `${escapeHtml(routeMeta.nextDistance.value)}<span>${escapeHtml(routeMeta.nextDistance.unit)}</span>`
        : vm.risk.lateMinutes
        ? `${vm.risk.lateMinutes}<span>分钟</span>`
        : vm.navigation.hasDestination
          ? `按时<span>行驶</span>`
          : `--<span>路线</span>`;
    }
    if (instruction) instruction.textContent = routeMeta?.instruction || (vm.navigation.hasDestination ? vm.navigation.destination : "等待手机同步路线");
    if (destination) destination.textContent = vm.navigation.taskTitle || "暂无导航任务";
    if (eta) eta.textContent = vm.navigation.etaLabel;
    const remainingKm = routeMeta ? Math.max(0, routeMeta.remainingDistanceMeters / 1000) : null;
    if (minutes) minutes.textContent = vm.tasks.total ? String(vm.tasks.total) : "--";
    if (kilometers) kilometers.textContent = remainingKm === null ? "--" : remainingKm >= 10 ? String(Math.round(remainingKm)) : remainingKm.toFixed(1);
    if (minutes?.nextElementSibling) minutes.nextElementSibling.textContent = "今日任务";
    if (kilometers?.nextElementSibling) kilometers.nextElementSibling.textContent = "剩余公里";
    if (progress) progress.style.width = `${Math.round(stageProgress * 100)}%`;
    renderTurnArrow(routeMeta?.maneuver || "straight");
  }

  function renderTurnArrow(maneuver) {
    const path = document.querySelector("#vd-nav-card .vd-nav-arrow path:first-child");
    const tail = document.querySelector("#vd-nav-card .vd-nav-arrow path:last-child");
    if (!path || !tail) return;
    const routes = {
      left: ["M34 35V24c0-6.6-5.4-12-12-12H10", "M18 5l-8 7 8 7"],
      right: ["M14 35V24c0-6.6 5.4-12 12-12h12", "M30 5l8 7-8 7"],
      uturn: ["M34 36V22c0-8-5.6-13-12-13s-12 5-12 13v4", "M4 20l6 6 6-6"],
      arrive: ["M24 39V11", "M16 19l8-8 8 8"],
      straight: ["M24 39V9", "M16 17l8-8 8 8"]
    };
    const selected = routes[maneuver] || routes.straight;
    path.setAttribute("d", selected[0]);
    tail.setAttribute("d", selected[1]);
  }

  function renderClimate() {
    const climate = viewModel.vehicle;
    const temperature = climate.available ? climate.temperatureLabel : "--";
    ["bbl", "bbr"].forEach((id) => {
      const element = document.getElementById(id);
      if (element) element.textContent = temperature;
    });
    const fan = document.getElementById("bb-fan-awake");
    fan?.classList.toggle("awake-fan", climate.available && climate.acOn);
    if (fan) fan.title = climate.available ? climate.summary : "等待座舱状态同步";
  }

  function animateStage() {
    const stage = viewModel.lifecycle.stage;
    if (stage === lastAnimatedStage) return;
    lastAnimatedStage = stage;
    const progress = STAGE_PROGRESS[stage];
    if (mapAdapter.getStatus() !== "online" && Number.isFinite(progress) && typeof window.mapCarTo === "function") {
      try { window.mapCarTo(progress, 1150); } catch (_error) { /* visual controller stays optional */ }
    }
  }

  function coordinatesFromTask(task) {
    const raw = task?.raw || {};
    const pair = raw.coordinates || raw.location_coordinates || raw.destination_coordinates;
    if (Array.isArray(pair) && pair.length >= 2 && pair.every((value) => Number.isFinite(Number(value)))) {
      return [Number(pair[0]), Number(pair[1])];
    }
    const lng = Number(raw.longitude ?? raw.lng);
    const lat = Number(raw.latitude ?? raw.lat);
    if (Number.isFinite(lng) && Number.isFinite(lat)) return [lng, lat];
    const text = `${task?.location || ""} ${task?.title || ""}`;
    return DEMO_DESTINATIONS.find((item) => item.pattern.test(text))?.coordinates || null;
  }

  function routeDefinition() {
    const task = viewModel.tasks.navigation;
    const end = coordinatesFromTask(task);
    if (!task || !end) return null;
    const known = DEMO_DESTINATIONS.find((item) => item.coordinates[0] === end[0] && item.coordinates[1] === end[1]);
    return {
      start: ROUTE_ORIGIN.coordinates,
      end,
      originName: ROUTE_ORIGIN.name,
      destinationName: task.location || known?.name || task.title || "目的地"
    };
  }

  function navigationSnapshot() {
    const stage = viewModel.lifecycle.stage;
    const progress = STAGE_PROGRESS[stage] ?? 0.03;
    const driving = viewModel.lifecycle.scene === "driving" || ["handover_to_vehicle", "vehicle_observation", "takeover_L2", "takeover_L3", "planning", "service_prepared", "waiting_confirmation", "executing", "service_executed", "action_completed", "cooldown"].includes(stage);
    return {
      stage,
      progress,
      driving,
      showVehicle: driving,
      overview: !driving || ["handover_to_vehicle", "parked_review"].includes(stage),
      riskLevel: viewModel.risk.level,
      lateMinutes: viewModel.risk.lateMinutes
    };
  }

  async function ensureMapRoute() {
    if (!mapConfigReady) return;
    const route = routeDefinition();
    if (!route) {
      routeMeta = null;
      if (["online", "map_ready"].includes(mapAdapter.getStatus())) mapAdapter.clearNavigation("等待手机同步路线");
      renderNavigation();
      return;
    }
    const routeKey = `${viewModel.meta.sessionId || "no-session"}:${viewModel.tasks.navigation?.id || viewModel.navigation.destination}:${route.end.join(",")}`;
    await mapAdapter.setRoute(route, routeKey);
    mapAdapter.update(navigationSnapshot());
  }

  function renderMapStatus(next) {
    const previousMode = mapStatus.mode;
    mapStatus = next;
    const source = document.getElementById("auri-map-source");
    const controls = document.getElementById("auri-map-controls");
    const [label, tone] = MAP_STATUS_VIEW[next.mode] || [next.message || "离线导航", "offline"];
    if (source) {
      source.textContent = next.message || label;
      source.dataset.mode = tone;
    }
    if (controls) controls.hidden = next.mode !== "online";
    if (next.mode === "online") {
      try { window.mapCarStop?.(); } catch (_error) { /* offline controller is optional */ }
    } else if (previousMode === "online") {
      lastAnimatedStage = null;
      animateStage();
    }
    if (activeSection === "connection") openPanel("connection");
  }

  const mapAdapter = amapModule.create({
    container: document.getElementById("auri-amap-canvas"),
    mapWrap: document.querySelector(".right-panel"),
    onStatus: renderMapStatus,
    onRouteMeta(next) {
      routeMeta = next;
      renderNavigation();
    }
  });

  async function initializeMap() {
    if (mapInitPromise) return mapInitPromise;
    mapInitPromise = (async () => {
      let config = client.getConfig();
      if (config.mapProvider === "auto" && !config.amapKey) {
        try {
          const remote = await client.requestJson("/v1/map-config");
          if (remote?.enabled && remote?.provider === "amap" && remote?.key) {
            config = {
              ...config,
              mapProvider: "amap",
              amapKey: remote.key,
              amapServiceHost: remote.service_host || "",
              amapStyle: remote.style || config.amapStyle
            };
          } else config = { ...config, mapProvider: "offline" };
        } catch (_error) {
          config = { ...config, mapProvider: "offline" };
        }
      }
      await mapAdapter.init(config);
      mapConfigReady = true;
      await ensureMapRoute();
    })();
    return mapInitPromise;
  }

  function renderWorldState(state) {
    viewModel = model.buildVehicleHmiViewModel(state);
    const hmi = document.getElementById("hmi");
    if (hmi) {
      hmi.dataset.auriStage = viewModel.lifecycle.stage;
      hmi.dataset.auriRisk = viewModel.risk.tone;
      hmi.dataset.auriPrimarySurface = viewModel.lifecycle.primarySurface;
    }
    renderNavigation();
    renderClimate();
    updateRoutePOIs();
    animateStage();
    if (mapAdapter.getStatus() === "online") mapAdapter.update(navigationSnapshot());
    void ensureMapRoute();
    if (activeSection && activeSection !== "connection") openPanel(activeSection);
  }

  const client = agentModule.createClient({
    config: agentModule.loadConfig(),
    onStatus: renderConnectionStatus,
    onError(error) {
      lastError = error?.status === 401
        ? "Team Token 无效或缺失"
        : error?.code === "TIMEOUT"
          ? "请求超时"
          : "无法连接 Agent 服务";
    }
  });
  client.subscribe((state) => renderWorldState(state));

  function applyShell() {
    prepareTopBar();
    prepareClimateControls();
    replaceCarBranding();
    replaceRoutePOIs();
    bindDock();
    disableLegacyDemoShortcuts();
    closePanel();
    renderWorldState(null);
    document.querySelectorAll("[data-map-control]").forEach((button) => {
      button.addEventListener("click", () => mapAdapter.control(button.dataset.mapControl));
    });
    document.documentElement.dataset.auriShell = "phase-3";
    const offline = new URLSearchParams(window.location.search).get("offline") === "1";
    if (!offline) {
      client.start();
      void initializeMap();
    } else renderMapStatus({ mode: "offline", message: "离线导航" });
  }

  window.AURI_HMI_NEXT = {
    applyState(state) { return client.injectSnapshot(state, "fixture"); },
    connect() { return client.start(); },
    disconnect() { return client.stop(); },
    getState() {
      const publicConfig = client.getConfig();
      return {
        syncMode: client.getSyncMode(),
        config: {
          ...publicConfig,
          token: publicConfig.token ? "***" : "",
          amapKey: publicConfig.amapKey ? "***" : "",
          amapSecurityJsCode: publicConfig.amapSecurityJsCode ? "***" : ""
        },
        worldState: client.getSnapshot(),
        viewModel,
        activeSection,
        map: { status: mapAdapter.getStatus(), cameraMode: mapAdapter.getCameraMode(), usage: mapAdapter.getUsage(), routeMeta }
      };
    },
    openPanel,
    closePanel
  };

  window.addEventListener("beforeunload", () => client.stop(), { once: true });
  if (document.readyState === "complete") applyShell();
  else window.addEventListener("load", () => requestAnimationFrame(applyShell), { once: true });
})();
