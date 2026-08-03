(function initAuriCockpit() {
  "use strict";

  const model = window.AuriWorldStateModel;
  const agentModule = window.AuriAgentClient;
  const amapModule = window.AuriAmapAdapter;
  if (!model || !agentModule || !amapModule) {
    console.error("[AURI] World State modules are unavailable");
    return;
  }

  const COMPAT_ROUTE_ORIGIN = { name: "博世苏州 · 星龙街455号", coordinates: [120.791879, 31.334680] };
  const COMPAT_DEMO_DESTINATIONS = [
    { aliases: ["阳光小学", "Demo 阳光小学"], name: "阳光小学", coordinates: [120.7359, 31.3048] },
    { aliases: ["苏州中心", "东方之门"], name: "苏州中心", coordinates: [120.6677, 31.3181] },
    { aliases: ["邻里生鲜超市", "Demo 邻里生鲜超市"], name: "邻里生鲜超市", coordinates: [120.7506, 31.3147] }
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
    online: ["高德导航 · Demo 定位", "online"]
  };
  const TAKEOVER_STAGES = new Set([
    "takeover_L2", "takeover_L3", "planning", "service_prepared",
    "waiting_confirmation", "executing", "service_executed", "action_completed",
    "parked_review"
  ]);
  const TAKEOVER_STAGE_VIEW = {
    takeover_L2: ["AURI 接管", "我正在核对时间和可调整任务。", "processing"],
    takeover_L3: ["安全优先", "先保持当前车速，我会压缩非必要操作。", "critical"],
    planning: ["正在处理", "我正在重新安排任务并准备必要联系。", "processing"],
    service_prepared: ["方案已准备", "处理方案已经就绪，等待确认入口开放。", "warning"],
    waiting_confirmation: ["等待确认", "方案已准备，只需确认一次。", "warning"],
    executing: ["正在执行", "正在同步消息、任务与服务状态。", "processing"],
    service_executed: ["执行完成", "处理结果正在同步到各端。", "success"],
    action_completed: ["问题已处理", "已完成本次接管，按当前路线继续即可。", "success"],
    parked_review: ["手机继续处理", "本次接管已结束，消息、订单和处理记录已同步到手机。", "success"]
  };
  const HAPTIC_LABEL = {
    none: "无振动", double_short: "双短震", single_pulse: "一次短震",
    three_beat: "三拍提示", soft_short: "柔和短震", error_once: "一次明确提醒"
  };

  let viewModel = model.buildVehicleHmiViewModel(null);
  let activeSection = null;
  let connectionStatus = { type: "idle" };
  let lastHealth = null;
  let lastAnimatedStage = null;
  let lastError = null;
  let routeMeta = null;
  let mapStatus = { mode: "offline", message: "离线导航" };
  let mapConfigReady = false;
  let mapInitPromise = null;
  let confirmInFlight = false;
  let confirmOutcomeUnknown = false;
  let confirmError = null;
  let climateDraft = null;
  let climateDraftDirty = false;
  let climateRequest = null;
  let climateError = null;
  let lastConfirmationId = null;
  let confirmationExpiryTimer = null;
  const notifiedDeviceCommands = new Set();
  const notifiedStages = new Set();
  let noticeTimer = null;
  let stageNoticeTimer = null;
  let noticeHideTimer = null;
  let stageNoticeHideTimer = null;
  const completionSpeechKeys = new Set((() => {
    try { return JSON.parse(sessionStorage.getItem("auri-hmi-next-completion-speech") || "[]"); }
    catch (_error) { return []; }
  })());

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  const ICON_PATHS = {
    assistant: '<circle cx="12" cy="12" r="8"/><path d="M12 7.5v9M7.5 12h9"/>',
    phone: '<rect x="7" y="2.5" width="10" height="19" rx="2.2"/><path d="M10.5 5.5h3M10.5 18.5h3"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3M9 21h6"/>',
    watch: '<rect x="7" y="6" width="10" height="12" rx="3"/><path d="m9 6 1-3h4l1 3m0 12-1 3h-4l-1-3"/>',
    car: '<path d="m5 16-1-2 2-6h12l2 6-1 2"/><path d="M4 14h16v5H4zM7 19v2m10-2v2M7 11h10"/>',
    task: '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="m8 12 2.2 2.2L16 8.5"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 10h18"/>',
    message: '<path d="M4 5h16v12H8l-4 4z"/><path d="M8 9h8m-8 4h5"/>',
    order: '<path d="M3 5h2l2.2 10h9.8l2-7H6"/><circle cx="9" cy="19" r="1.2"/><circle cx="17" cy="19" r="1.2"/>',
    route: '<circle cx="6" cy="18" r="2"/><circle cx="18" cy="6" r="2"/><path d="M7.5 16.5 16.5 7.5M8 6h5M8 6l2-2M8 6l2 2"/>',
    clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
    distance: '<path d="M4 17h16M6 14v6m12-6v6M8 8l4-4 4 4M12 4v10"/>',
    devices: '<rect x="3" y="5" width="12" height="9" rx="1.5"/><path d="M7 18h4M9 14v4"/><rect x="17" y="7" width="4" height="10" rx="1"/>',
    climate: '<path d="M12 3v18M5.6 6.5l12.8 11M18.4 6.5l-12.8 11M4 12h16"/><circle cx="12" cy="12" r="2"/>',
    warning: '<path d="M12 3 2.5 20h19z"/><path d="M12 9v4m0 3h.01"/>',
    check: '<circle cx="12" cy="12" r="9"/><path d="m7.5 12 3 3 6-7"/>',
    flexible: '<path d="M4 8h10a4 4 0 0 1 4 4v5"/><path d="m15 14 3 3 3-3M4 16h7"/>',
    back: '<path d="m15 18-6-6 6-6"/>',
    add: '<circle cx="12" cy="12" r="9"/><path d="M12 8v8M8 12h8"/>',
    minus: '<path d="M5 12h14"/>',
    power: '<path d="M12 3v9"/><path d="M7.2 5.8a8 8 0 1 0 9.6 0"/>',
    airflow: '<path d="M4 8h10.5a2.5 2.5 0 1 0-2.2-3.7"/><path d="M4 12h14a2 2 0 1 1-1.7 3"/><path d="M4 16h7"/>',
    link: '<path d="M10 13a5 5 0 0 0 7.1.1l2-2a5 5 0 0 0-7.1-7.1l-1.1 1.1"/><path d="M14 11a5 5 0 0 0-7.1-.1l-2 2A5 5 0 0 0 12 20l1.1-1.1"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10h.01"/>'
  };

  const ICON_ALIASES = {
    "声": "mic", "腕": "watch", "联": "devices", "刚": "calendar", "弹": "flexible",
    "路": "route", "返": "back", "信": "message", "单": "order", "调": "task",
    "时": "clock", "距": "distance", "务": "task", "手": "phone", "车": "car",
    "温": "climate", "○": "info", "＋": "add", "+": "add", "✓": "check",
    "1": "clock", "2": "flexible"
  };

  function semanticIconName(name) {
    return ICON_PATHS[name] ? name : ICON_ALIASES[String(name)] || "info";
  }

  function iconSvg(name, className = "") {
    const icon = semanticIconName(name);
    return `<svg class="auri-icon${className ? ` ${className}` : ""}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON_PATHS[icon]}</svg>`;
  }

  function row(icon, title, detail, state, tone = "") {
    return `
      <div class="auri-shell-row${tone ? ` is-${tone}` : ""}">
        <span class="auri-shell-row-icon" aria-hidden="true">${iconSvg(icon)}</span>
        <span class="auri-shell-row-copy"><strong>${escapeHtml(title)}</strong><span>${escapeHtml(detail)}</span></span>
        <span class="auri-shell-row-state">${escapeHtml(state)}</span>
      </div>
    `;
  }

  function emptyRow(icon, title, detail) {
    return row(icon, title, detail, "等待");
  }

  function rowButton(icon, title, detail, state, target, tone = "") {
    return `
      <button class="auri-shell-row auri-shell-row-button${tone ? ` is-${tone}` : ""}" type="button" data-panel-target="${escapeHtml(target)}">
        <span class="auri-shell-row-icon" aria-hidden="true">${iconSvg(icon)}</span>
        <span class="auri-shell-row-copy"><strong>${escapeHtml(title)}</strong><span>${escapeHtml(detail)}</span></span>
        <span class="auri-shell-row-state">${escapeHtml(state)}</span>
      </button>
    `;
  }

  function ensureDriverPanel() {
    const body = document.querySelector(".hmi-body");
    const vehiclePanel = document.querySelector(".vd-panel");
    if (!body || !vehiclePanel || document.getElementById("auri-driver-panel")) return;
    const panel = document.createElement("aside");
    panel.id = "auri-driver-panel";
    panel.className = "auri-driver-panel";
    panel.setAttribute("aria-label", "AURI 驾驶协助");
    panel.innerHTML = `
      <header class="auri-driver-head">
        <span class="auri-driver-brand"><img src="icons/auri-icon.png" alt=""><span><b>AURI</b><small>你只管开，我来处理</small></span></span>
        <button class="auri-driver-risk" id="auri-driver-risk" type="button" data-panel-target="auri">状态平稳</button>
      </header>
      <div class="auri-driver-overview" id="auri-driver-overview">
        <section class="auri-driver-summary" data-panel-target="auri" role="button" tabindex="0">
          <small id="auri-driver-kicker">AURI 当前判断</small>
          <h2 id="auri-driver-title">等待手机创建今天的任务</h2>
          <p id="auri-driver-copy">请在手机端通过语音创建，任务会自动同步到这里。</p>
        </section>
        <section class="auri-driver-context" id="auri-driver-context" data-panel-target="auri" role="button" tabindex="0">
          <span class="auri-driver-context-icon">${iconSvg("phone")}</span>
          <span><small id="auri-driver-source">手机端</small><b id="auri-driver-utterance">等待语音输入</b></span>
          <em id="auri-driver-context-state">待同步</em>
        </section>
        <section class="auri-driver-tasks" aria-label="当前任务">
          <div class="auri-driver-section-head"><span>当前任务</span><button type="button" data-panel-target="tasks" id="auri-driver-task-count">0 项</button></div>
          <div id="auri-driver-task-list" class="auri-driver-task-list"></div>
        </section>
        <section class="auri-driver-glance" aria-label="快捷状态">
          <button type="button" data-panel-target="messages" id="auri-glance-actions">${iconSvg("message")}<span><small>处理进度</small><b>等待 Agent 方案</b></span><em>查看</em></button>
          <button type="button" data-panel-target="vehicle" id="auri-glance-cabin">${iconSvg("climate")}<span><small>座舱状态</small><b>等待车辆数据</b></span><em>查看</em></button>
        </section>
        <section class="auri-driver-devices" aria-label="设备状态">
          <button type="button" data-panel-target="sync" id="auri-device-phone">${iconSvg("phone")}<span><b>手机</b><small>等待同步</small></span></button>
          <button type="button" data-panel-target="sync" id="auri-device-watch">${iconSvg("watch")}<span><b>腕表</b><small>未连接</small></span></button>
          <button type="button" data-panel-target="sync" id="auri-device-car">${iconSvg("car")}<span><b>车机</b><small>已就绪</small></span></button>
        </section>
        <div id="auri-driver-primary" class="auri-driver-primary"></div>
      </div>
      <section class="auri-driver-detail" id="auri-driver-detail" hidden aria-live="polite">
        <header class="auri-driver-detail-head">
          <button class="auri-driver-back" id="auri-driver-back" type="button" aria-label="返回 AURI 概览">${iconSvg("back")}</button>
          <span><small id="auri-detail-subtitle">AURI</small><h2 id="auri-detail-title">详细信息</h2></span>
        </header>
        <div class="auri-driver-detail-body" id="auri-detail-body"></div>
      </section>
    `;
    body.insertBefore(panel, vehiclePanel);
    panel.addEventListener("click", (event) => {
      const target = event.target.closest("[data-panel-target]");
      if (target) openPanel(target.dataset.panelTarget);
    });
    panel.addEventListener("keydown", (event) => {
      const target = event.target.closest("[data-panel-target]");
      if (target && (event.key === "Enter" || event.key === " ")) {
        event.preventDefault();
        openPanel(target.dataset.panelTarget);
      }
    });
    panel.querySelector("#auri-driver-back")?.addEventListener("click", closePanel);
  }

  function ensureNavigationHud() {
    const map = document.querySelector(".right-panel");
    if (!map || document.getElementById("auri-nav-hud")) return;
    const hud = document.createElement("section");
    hud.id = "auri-nav-hud";
    hud.className = "auri-nav-hud";
    hud.setAttribute("aria-label", "下一步导航");
    hud.innerHTML = `
      <span class="auri-nav-maneuver" id="auri-nav-maneuver">${iconSvg("route")}</span>
      <span class="auri-nav-next"><b id="auri-nav-next-distance">等待路线</b><small id="auri-nav-next-road">手机同步目的地后开始导航</small><em id="auri-nav-source">离线导航</em></span>
      <span class="auri-nav-remaining"><b id="auri-nav-remaining-time">--</b><small id="auri-nav-remaining-distance">-- 公里</small></span>
    `;
    map.appendChild(hud);
  }

  function ensureTakeoverUi() {
    ensureDriverPanel();
    ensureNavigationHud();
    const host = document.getElementById("auri-driver-primary");
    const nav = document.getElementById("vd-nav-card");
    if (!host || !nav || document.getElementById("auri-takeover-card")) return;
    const card = document.createElement("section");
    card.id = "auri-takeover-card";
    card.className = "auri-takeover-card";
    card.hidden = true;
    card.setAttribute("aria-live", "polite");
    card.innerHTML = `
      <div class="auri-takeover-head">
        <span class="auri-takeover-orbit" aria-hidden="true"><i>A</i></span>
        <span><b id="auri-takeover-stage">AURI 接管</b><small id="auri-takeover-risk">状态平稳</small></span>
      </div>
      <div class="auri-takeover-label">现实结论</div>
      <p class="auri-takeover-conclusion" id="auri-takeover-conclusion"></p>
      <div class="auri-takeover-section-head"><span>AURI 已准备</span><em id="auri-takeover-action-count">0 项</em></div>
      <div class="auri-takeover-actions" id="auri-takeover-actions"></div>
      <div class="auri-takeover-devices" id="auri-takeover-devices"></div>
      <div class="auri-takeover-next" id="auri-takeover-next"><small>下一步</small><b>保持驾驶，等待处理结果</b></div>
      <button class="auri-takeover-confirm" id="auri-takeover-confirm" type="button" hidden>
        <span id="auri-confirm-label">确认处理</span>
      </button>
      <p class="auri-confirm-error" id="auri-confirm-error" role="status" hidden></p>
    `;
    host.appendChild(card);
    card.querySelector("#auri-takeover-confirm")?.addEventListener("click", () => void confirmCurrentActions("button"));

    const taskStrip = document.createElement("div");
    taskStrip.id = "auri-responsibility-strip";
    taskStrip.className = "auri-responsibility-strip";
    taskStrip.hidden = true;
    nav.querySelector(".vd-nav-progress")?.insertAdjacentElement("beforebegin", taskStrip);
    taskStrip.addEventListener("click", (event) => {
      if (event.target.closest("button")) openPanel("tasks");
    });

    nav.setAttribute("role", "button");
    nav.setAttribute("tabindex", "0");
    nav.title = "查看行程详情";
    nav.addEventListener("click", (event) => {
      if (!event.target.closest("#auri-responsibility-strip")) openPanel("route");
    });
    nav.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        openPanel("route");
      }
    });

    const notice = document.createElement("aside");
    notice.id = "auri-device-notice";
    notice.className = "auri-device-notice";
    notice.hidden = true;
    notice.innerHTML = `
      <span class="auri-notice-icon" aria-hidden="true">${iconSvg("watch")}</span>
      <span class="auri-notice-copy"><b id="auri-notice-title">腕上提醒</b><small id="auri-notice-text"></small></span>
      <button type="button" aria-label="关闭提醒">×</button>
    `;
    document.querySelector(".right-panel")?.appendChild(notice);
    notice.querySelector("button")?.addEventListener("click", hideDeviceNotice);

    const stageNotice = document.createElement("aside");
    stageNotice.id = "auri-stage-notice";
    stageNotice.className = "auri-stage-notice";
    stageNotice.hidden = true;
    stageNotice.setAttribute("aria-live", "polite");
    stageNotice.innerHTML = `
      <span class="auri-stage-notice-icon" aria-hidden="true">${iconSvg("assistant")}</span>
      <span><small id="auri-stage-notice-kicker">场景切换</small><b id="auri-stage-notice-title"></b><em id="auri-stage-notice-detail"></em></span>
      <button type="button" aria-label="关闭提示">×</button>
    `;
    document.querySelector(".right-panel")?.appendChild(stageNotice);
    stageNotice.querySelector("button")?.addEventListener("click", hideStageNotice);
  }

  function takeoverActions() {
    const actions = viewModel.actions.items.slice(0, 3).map((action) => {
      const order = action.type === "service_order"
        ? viewModel.serviceOrders.items.find((item) => item.id === action.detailsRef)
        : null;
      const orderText = order
        ? `模拟配送 · ${order.itemCount} 件/${order.itemKinds} 种 · ${order.total === null ? "金额待定" : `${order.total} 元`} · ${order.deliveryWindow || "配送待定"}`
        : null;
      const messageText = action.type === "message"
        ? `给${action.target || "联系人"}的消息草稿已生成（模拟）`
        : null;
      return {
        icon: action.status === "completed" ? "check" : action.type === "message" ? "message" : action.type === "service_order" ? "order" : "task",
        text: orderText || messageText || action.preview,
        state: action.statusLabel,
        completed: action.status === "completed"
      };
    });
    if (actions.length) return actions;
    if (["takeover_L2", "takeover_L3", "planning"].includes(viewModel.lifecycle.stage)) {
      return [
        { icon: "clock", text: "核对 ETA 与刚性任务", state: "进行中" },
        { icon: "flexible", text: "寻找可后置或可代办事项", state: "等待" }
      ];
    }
    return [];
  }

  function driverSummaryView() {
    const vm = viewModel;
    const stage = vm.lifecycle.stage;
    const late = vm.risk.lateMinutes;
    if (!vm.tasks.total && stage === "off_vehicle_idle") {
      return ["AURI 当前判断", "等待手机创建今天的任务", "请在手机端通过语音创建，任务会自动同步到这里。"];
    }
    if (stage === "pre_departure_warning") {
      return ["时间风险", "出发窗口正在缩短", late ? `按当前计划预计晚到 ${late} 分钟，AURI 正在保护刚性任务。` : "建议尽快出发，AURI 会持续核对 ETA。"];
    }
    if (stage === "handover_to_vehicle") return ["场景已切换", "路线已接续到车机", `正在前往 ${vm.navigation.destination}，手机进入驾驶只读状态。`];
    if (stage === "vehicle_observation") return ["行程进行中", `正在前往 ${vm.navigation.destination}`, late ? `当前预计晚到 ${late} 分钟，AURI 正在观察变化。` : "行程状态平稳，AURI 会在风险成立时主动处理。"];
    if (["takeover_L2", "takeover_L3", "planning"].includes(stage)) return ["AURI 正在处理", "我已收到你的求助", "正在核对到达时间、任务优先级和可代办事项。"];
    if (["service_prepared", "waiting_confirmation"].includes(stage)) return ["方案已准备", "需要你确认一次", late ? `预计晚到 ${late} 分钟，消息和可调整事项已经备好。` : "必要消息和可调整事项已经备好。"];
    if (["executing", "service_executed"].includes(stage)) return ["正在执行", "处理结果正在同步", "手机、腕表和车机会自动更新当前状态。"];
    if (["action_completed", "cooldown"].includes(stage)) return ["问题已处理", "按当前路线继续即可", "AURI 已完成必要动作，并降低后续打扰。"];
    if (stage === "parked_review") return ["本次行程已结束", "完整记录已回到手机", "消息、服务和处理记录可在手机端查看。"];
    return ["AURI 当前判断", vm.tasks.total ? `${vm.tasks.total} 项任务已同步` : vm.lifecycle.stageLabel, vm.agentOutput.available ? vm.agentOutput.preview : "AURI 正在持续检查任务与行程。"];
  }

  function renderDriverPanel() {
    const panel = document.getElementById("auri-driver-panel");
    if (!panel) return;
    const [kicker, title, copy] = driverSummaryView();
    panel.dataset.tone = viewModel.risk.tone;
    panel.classList.toggle("is-takeover", TAKEOVER_STAGES.has(viewModel.lifecycle.stage));
    document.getElementById("auri-driver-kicker").textContent = kicker;
    document.getElementById("auri-driver-title").textContent = title;
    document.getElementById("auri-driver-copy").textContent = copy;
    const risk = document.getElementById("auri-driver-risk");
    risk.textContent = viewModel.risk.label;
    risk.dataset.tone = viewModel.risk.tone;

    const utterance = document.getElementById("auri-driver-utterance");
    const source = document.getElementById("auri-driver-source");
    const contextState = document.getElementById("auri-driver-context-state");
    const context = document.getElementById("auri-driver-context");
    if (viewModel.utterance.available) {
      source.textContent = viewModel.utterance.sourceLabel || "手机语音";
      utterance.textContent = `“${viewModel.utterance.preview}”`;
      utterance.title = viewModel.utterance.text;
      contextState.textContent = viewModel.utterance.receivedAtLabel || "已同步";
      context.classList.add("is-active");
    } else {
      source.textContent = "手机语音";
      utterance.textContent = viewModel.tasks.total ? "等待用户在手机端求助" : "等待用户在手机端创建任务";
      utterance.title = "";
      contextState.textContent = "等待";
      context.classList.remove("is-active");
    }

    const taskList = document.getElementById("auri-driver-task-list");
    const taskCount = document.getElementById("auri-driver-task-count");
    const taskPreviewLimit = document.getElementById("hmi")?.classList.contains("is-ultrawide") ? 2 : 3;
    taskCount.textContent = `${viewModel.tasks.total} 项`;
    taskList.innerHTML = viewModel.tasks.total
      ? viewModel.tasks.items.slice(0, taskPreviewLimit).map((task) => `
          <button type="button" class="auri-driver-task is-${escapeHtml(task.tone)}" data-panel-target="task:${escapeHtml(task.id)}">
            <span class="auri-driver-task-icon">${iconSvg(task.tone === "rigid" ? "calendar" : "flexible")}</span>
            <span><b>${escapeHtml(task.displayTitle)}</b><small>${escapeHtml(task.tone === "rigid" ? "优先保护时间窗口" : "可调整顺序")}</small></span>
            <em>${escapeHtml(task.status)}</em>
          </button>
        `).join("") + (viewModel.tasks.total > taskPreviewLimit ? `<button type="button" class="auri-driver-task-more" data-panel-target="tasks">查看其余 ${viewModel.tasks.total - taskPreviewLimit} 项任务</button>` : "")
      : `<button type="button" class="auri-driver-task is-empty" data-panel-target="tasks"><span class="auri-driver-task-icon">${iconSvg("add")}</span><span><b>暂无任务</b><small>任务将从手机端自动同步</small></span><em>等待</em></button>`;

    const phone = document.querySelector("#auri-device-phone small");
    const watch = document.querySelector("#auri-device-watch small");
    const car = document.querySelector("#auri-device-car small");
    if (phone) phone.textContent = viewModel.utterance.available || viewModel.tasks.total ? "已同步" : "等待同步";
    if (watch) watch.textContent = viewModel.wearable.connected ? viewModel.wearable.modeLabel : "未连接";
    if (car) car.textContent = viewModel.lifecycle.primarySurface === "vehicle_hmi" ? "当前主端" : "已就绪";
    document.getElementById("auri-device-phone")?.classList.toggle("is-active", Boolean(viewModel.utterance.available || viewModel.tasks.total));
    document.getElementById("auri-device-watch")?.classList.toggle("is-active", viewModel.wearable.connected);
    document.getElementById("auri-device-car")?.classList.toggle("is-active", viewModel.lifecycle.primarySurface === "vehicle_hmi");
    const actionGlance = document.querySelector("#auri-glance-actions b");
    const cabinGlance = document.querySelector("#auri-glance-cabin b");
    if (actionGlance) actionGlance.textContent = viewModel.actions.counts.total
      ? `${viewModel.actions.counts.completed}/${viewModel.actions.counts.total} 项已完成`
      : "等待 Agent 方案";
    if (cabinGlance) cabinGlance.textContent = viewModel.vehicle.available
      ? viewModel.vehicle.summary
      : "等待车辆数据";
  }

  function renderTakeover() {
    const host = document.getElementById("auri-driver-panel");
    const card = document.getElementById("auri-takeover-card");
    if (!host || !card) return;
    const stage = viewModel.lifecycle.stage;
    const visible = TAKEOVER_STAGES.has(stage);
    host.classList.toggle("is-auri-takeover", visible);
    card.hidden = !visible;
    if (!visible) return;

    const [label, fallback, tone] = TAKEOVER_STAGE_VIEW[stage] || [viewModel.lifecycle.stageLabel, "保持当前路线。", "processing"];
    card.dataset.tone = tone;
    document.getElementById("auri-takeover-stage").textContent = label;
    const riskLine = document.getElementById("auri-takeover-risk");
    riskLine.textContent = stage === "parked_review"
      ? "车辆已停稳 · 完整明细已同步"
      : viewModel.risk.lateMinutes > 0
        ? `${viewModel.risk.label} · 预计晚到 ${viewModel.risk.lateMinutes} 分钟`
        : viewModel.risk.label;
    riskLine.title = viewModel.utterance.available ? viewModel.utterance.text : viewModel.risk.label;
    const messageCount = viewModel.actions.items.filter((action) => action.type === "message").length;
    const hasServicePlan = viewModel.actions.items.some((action) => action.type === "service_order");
    const preparedParts = [
      messageCount ? "消息" : "",
      hasServicePlan ? "生活服务" : ""
    ].filter(Boolean).join("与");
    const concisePlan = viewModel.risk.lateMinutes > 0 && preparedParts
      ? `预计晚到${viewModel.risk.lateMinutes}分钟，${preparedParts}已备好。`
      : preparedParts ? `${preparedParts}已备好。` : fallback;
    document.getElementById("auri-takeover-conclusion").textContent = stage === "parked_review"
      ? fallback
      : viewModel.serviceOrders.hasFailure
        ? "生活服务暂不可用，消息和任务调整方案仍保留。"
        : ["service_prepared", "waiting_confirmation"].includes(stage)
          ? viewModel.risk.lateMinutes > 0
            ? `无法准点，预计晚到 ${viewModel.risk.lateMinutes} 分钟`
            : concisePlan
          : viewModel.agentOutput.available && viewModel.agentOutput.fullText.length <= 42
            ? viewModel.agentOutput.fullText
            : fallback;

    const actions = takeoverActions();
    document.getElementById("auri-takeover-action-count").textContent = `${actions.length} 项`;
    document.getElementById("auri-takeover-actions").innerHTML = actions.map((action) => `
      <div class="auri-takeover-action${action.completed ? " is-completed" : ""}">
        <span>${iconSvg(action.icon)}</span><b>${escapeHtml(action.text)}</b><small>${escapeHtml(action.state)}</small>
      </div>
    `).join("");

    const devices = [
      ["手机", viewModel.lifecycle.primarySurface === "mobile" ? "当前主端" : viewModel.utterance.available ? "语音已同步" : "保持连接", viewModel.lifecycle.primarySurface === "mobile" || viewModel.utterance.available],
      ["腕表", viewModel.wearable.connected ? viewModel.wearable.modeLabel : "未连接", viewModel.wearable.connected],
      ["车机", viewModel.lifecycle.primarySurface === "vehicle_hmi" ? "当前主端" : stage === "parked_review" ? "本次结束" : "只读显示", true]
    ];
    document.getElementById("auri-takeover-devices").innerHTML = devices.map(([name, status, active]) => `
      <span class="${active ? "is-active" : ""}"><i></i><b>${escapeHtml(name)}</b><small>${escapeHtml(status)}</small></span>
    `).join("");

    const button = document.getElementById("auri-takeover-confirm");
    clearTimeout(confirmationExpiryTimer);
    const expiresIn = Number(viewModel.interaction.expiresAt) - Date.now();
    const confirmationExpired = Number.isFinite(expiresIn) && expiresIn <= 0;
    if (Number.isFinite(expiresIn) && expiresIn > 0) {
      confirmationExpiryTimer = setTimeout(renderTakeover, Math.min(expiresIn + 20, 2147483647));
    }
    const showConfirm = stage === "waiting_confirmation" && viewModel.interaction.canConfirm && !confirmationExpired;
    const showExecuting = stage === "executing" || confirmInFlight;
    button.hidden = !(showConfirm || showExecuting);
    button.disabled = !showConfirm || confirmInFlight || confirmOutcomeUnknown;
    button.classList.toggle("is-loading", showExecuting);
    const next = document.querySelector("#auri-takeover-next b");
    if (next) next.textContent = showConfirm
      ? "说“确认”，或点击下方按钮"
      : showExecuting
        ? "正在执行，无需重复操作"
        : ["action_completed", "cooldown", "parked_review"].includes(stage)
          ? "继续按当前路线行驶"
          : "保持驾驶，AURI 正在处理";
    document.getElementById("auri-confirm-label").textContent = showExecuting ? "正在执行" : "确认处理";
    const error = document.getElementById("auri-confirm-error");
    error.hidden = !confirmError;
    error.textContent = confirmError || "";
  }

  function renderResponsibilityStrip() {
    const strip = document.getElementById("auri-responsibility-strip");
    if (!strip) return;
    const tasks = viewModel.tasks.items;
    strip.hidden = !tasks.length;
    if (!tasks.length) {
      strip.innerHTML = "";
      return;
    }
    const visible = tasks.slice(0, 2);
    strip.innerHTML = visible.map((task) => `
      <button class="auri-responsibility-item is-${escapeHtml(task.tone)}" type="button" title="查看全部任务">
        <span>${task.tone === "rigid" ? "刚性责任" : "弹性任务"}</span>
        <b>${escapeHtml(task.displayTitle)}</b>
        <em>${escapeHtml(task.status)}</em>
      </button>
    `).join("") + (tasks.length > 2 ? `<button class="auri-responsibility-more" type="button" title="查看全部任务">+${tasks.length - 2}</button>` : "");
  }

  function hideDeviceNotice() {
    clearTimeout(noticeTimer);
    clearTimeout(noticeHideTimer);
    const notice = document.getElementById("auri-device-notice");
    notice?.classList.remove("is-visible");
    if (notice) noticeHideTimer = setTimeout(() => {
      notice.hidden = true;
      renderStageNotice();
    }, 220);
  }

  function renderDeviceNotice() {
    const wearable = viewModel.wearable;
    if (!wearable.commandId || wearable.mode === "idle" || !wearable.haptic || wearable.haptic === "none") {
      const notice = document.getElementById("auri-device-notice");
      if (notice && !notice.hidden) hideDeviceNotice();
      return;
    }
    const commandKey = `${viewModel.meta.sessionId || "session"}:${wearable.commandId}`;
    if (notifiedDeviceCommands.has(commandKey)) return;
    notifiedDeviceCommands.add(commandKey);
    const notice = document.getElementById("auri-device-notice");
    if (!notice) return;
    // A device notice temporarily owns the single map notification lane. If
    // the stage notice was scheduled in the same render pass, allow it to be
    // shown after the device notice closes instead of losing it permanently.
    notifiedStages.delete(`${viewModel.meta.sessionId}:${viewModel.lifecycle.stage}`);
    hideStageNotice();
    const delivered = wearable.connected;
    const title = ["warning", "error"].includes(wearable.mode)
      ? delivered ? "腕表已发出风险提醒" : "腕表风险提醒已准备"
      : wearable.mode === "completed"
        ? delivered ? "手机、腕表与车机已同步" : "处理结果已准备同步"
        : delivered ? "腕表已接续当前状态" : "腕表状态已准备";
    document.getElementById("auri-notice-title").textContent = title;
    document.getElementById("auri-notice-text").textContent = `${wearable.text} · ${HAPTIC_LABEL[wearable.haptic] || "状态提示"}${delivered ? "" : " · 设备未连接"}`;
    notice.dataset.tone = wearable.mode;
    clearTimeout(noticeHideTimer);
    notice.hidden = false;
    requestAnimationFrame(() => notice.classList.add("is-visible"));
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(hideDeviceNotice, 4600);
  }

  function hideStageNotice() {
    clearTimeout(stageNoticeTimer);
    clearTimeout(stageNoticeHideTimer);
    const notice = document.getElementById("auri-stage-notice");
    notice?.classList.remove("is-visible");
    if (notice) stageNoticeHideTimer = setTimeout(() => { notice.hidden = true; }, 220);
  }

  function stageNoticeView() {
    const stage = viewModel.lifecycle.stage;
    const destination = viewModel.navigation.destination;
    const completed = viewModel.actions.counts.completed;
    const total = viewModel.actions.counts.total;
    if (stage === "pre_departure_warning") return ["时间风险", "出发窗口正在缩短", viewModel.wearable.connected ? "腕表已双短震并显示黄色提醒" : "腕表黄色提醒已准备，设备连接后同步", "warning"];
    if (stage === "handover_to_vehicle") return ["场景切换", "路线正在同步到车机", `${destination} · 手机进入驾驶只读`, "handover"];
    if (stage === "vehicle_observation") return ["导航已接续", `正在前往 ${destination}`, "ETA 与任务状态会持续同步", "guidance"];
    if (["takeover_L2", "takeover_L3", "planning"].includes(stage)) return ["手机求助已接收", "AURI 正在处理现实问题", "正在核对 ETA、任务优先级和可代办事项", stage === "takeover_L3" ? "critical" : "guidance"];
    if (["service_prepared", "waiting_confirmation"].includes(stage)) return ["处理方案已准备", "只需确认一次", "消息与可调整事项已在左侧就绪", "warning"];
    if (stage === "action_completed") return ["处理完成", total ? `${completed}/${total} 项动作已完成` : "本次问题已处理", "手机、腕表与车机正在同步结果", "success"];
    if (stage === "cooldown") return ["恢复驾驶", "AURI 已降低打扰", "按当前路线继续即可", "success", true];
    if (stage === "parked_review") return ["本次接管结束", "完整记录已同步到手机", "消息、订单和处理结果可在手机查看", "success"];
    return null;
  }

  function renderStageNotice() {
    const deviceNotice = document.getElementById("auri-device-notice");
    if (deviceNotice && !deviceNotice.hidden) return;
    const view = stageNoticeView();
    if (!view || !viewModel.meta.sessionId) {
      const notice = document.getElementById("auri-stage-notice");
      if (notice && !notice.hidden) hideStageNotice();
      return;
    }
    const stageKey = `${viewModel.meta.sessionId}:${viewModel.lifecycle.stage}`;
    if (notifiedStages.has(stageKey)) return;
    notifiedStages.add(stageKey);
    const notice = document.getElementById("auri-stage-notice");
    if (!notice) return;
    const [kicker, title, detail, tone, persistent] = view;
    notice.dataset.tone = tone;
    notice.dataset.stage = viewModel.lifecycle.stage;
    document.getElementById("auri-stage-notice-kicker").textContent = kicker;
    document.getElementById("auri-stage-notice-title").textContent = title;
    document.getElementById("auri-stage-notice-detail").textContent = detail;
    clearTimeout(stageNoticeHideTimer);
    notice.hidden = false;
    requestAnimationFrame(() => notice.classList.add("is-visible"));
    clearTimeout(stageNoticeTimer);
    if (!persistent) stageNoticeTimer = setTimeout(hideStageNotice, 4800);
  }

  function announceCompletion() {
    if (viewModel.lifecycle.stage !== "action_completed" || viewModel.lifecycle.primarySurface !== "vehicle_hmi") return;
    const messageId = viewModel.agentOutput.messageId;
    if (!messageId) return;
    const key = `${viewModel.meta.sessionId}:${messageId}`;
    if (completionSpeechKeys.has(key)) return;
    completionSpeechKeys.add(key);
    try {
      sessionStorage.setItem("auri-hmi-next-completion-speech", JSON.stringify([...completionSpeechKeys].slice(-30)));
      if (window.speechSynthesis && window.SpeechSynthesisUtterance) {
        const utterance = new SpeechSynthesisUtterance("已处理，你按当前速度安全驾驶即可。");
        utterance.lang = "zh-CN";
        utterance.rate = 0.96;
        utterance.pitch = 1;
        window.speechSynthesis.speak(utterance);
      }
    } catch (_error) { /* TTS is a non-blocking output channel. */ }
  }

  function confirmationErrorMessage(error) {
    if (error?.status === 401) return "连接凭证失效，请重新连接 Agent。";
    if (error?.code === "WRONG_SURFACE") return "确认入口已切换到其他设备。";
    if (error?.code === "EXPIRED") return "本次确认已过期，等待 Agent 更新方案。";
    if (error?.code === "NOT_FOUND") return "方案已变化，正在同步最新状态。";
    return "暂时无法确认，导航保持可用。";
  }

  async function confirmCurrentActions(inputMode = "button") {
    const confirmationId = viewModel.interaction.confirmationId;
    if (!viewModel.interaction.canConfirm || !confirmationId || confirmInFlight) return;
    confirmInFlight = true;
    confirmOutcomeUnknown = false;
    confirmError = null;
    renderTakeover();
    try {
      const state = await client.requestJson("/v1/confirm", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          confirmation_id: confirmationId,
          decision: "accept",
          confirmed_by: "vehicle_hmi",
          input_mode: inputMode
        })
      });
      client.injectSnapshot(state, "confirm");
    } catch (error) {
      confirmOutcomeUnknown = true;
      confirmError = "正在核对执行结果，请勿重复确认。";
      try {
        const reconciled = await client.refresh("confirm_reconcile");
        const stillPending = reconciled?.stage === "waiting_confirmation"
          && reconciled?.confirmation?.confirmation_id === confirmationId
          && reconciled?.confirmation?.status === "pending";
        confirmOutcomeUnknown = false;
        if (stillPending) confirmError = `${confirmationErrorMessage(error)} 本次未执行，可再次确认。`;
      } catch (_reconcileError) {
        confirmError = "执行结果暂时未知，正在等待 Agent 状态同步。";
      }
    } finally {
      confirmInFlight = false;
      renderTakeover();
    }
  }

  function ensureClimateDraft() {
    if (climateDraft && climateDraftDirty) return climateDraft;
    const climate = viewModel.vehicle;
    climateDraft = {
      ac_on: climate.acOn === true,
      ac_target_temp: Number.isFinite(Number(climate.temperature)) ? Number(climate.temperature) : 24,
      ac_mode: climate.rawMode || "auto",
      fan_speed: climate.rawFan || "medium"
    };
    return climateDraft;
  }

  function taskBoardContent(vm) {
    const groups = [
      ["rigid", "刚性任务", "优先保护时间窗口", "calendar"],
      ["flexible", "弹性任务", "可调整或转交 Agent", "flexible"]
    ];
    if (!vm.tasks.total) return `
      <section class="auri-empty-state">
        <span>${iconSvg("task")}</span><h3>等待手机创建任务</h3>
        <p>手机语音创建后，任务会按刚性与弹性自动归组。</p>
      </section>`;
    return groups.map(([tone, title, subtitle, icon]) => {
      const items = vm.tasks.items.filter((task) => task.tone === tone);
      if (!items.length) return "";
      return `<section class="auri-task-group is-${tone}">
        <header><span>${iconSvg(icon)}</span><div><b>${title}</b><small>${subtitle}</small></div><em>${items.length}</em></header>
        <div>${items.map((task) => `<button type="button" class="auri-task-card" data-panel-target="task:${escapeHtml(task.id)}">
          <span><b>${escapeHtml(task.displayTitle)}</b><small>${escapeHtml(task.location || task.type)}</small></span>
          <em>${escapeHtml(task.status)}</em>
        </button>`).join("")}</div>
      </section>`;
    }).join("");
  }

  function actionBoardContent(vm) {
    if (!vm.actions.items.length && !vm.serviceOrders.items.length) return `
      <section class="auri-empty-state">
        <span>${iconSvg("message")}</span><h3>等待 AURI 准备处理方案</h3>
        <p>风险成立后，这里会显示消息、任务调整与生活服务的执行进度。</p>
      </section>`;
    const actions = vm.actions.items.map((action, index) => `
      <button type="button" class="auri-action-step is-${escapeHtml(action.status)}" data-panel-target="action:${escapeHtml(action.id)}">
        <span class="auri-action-index">${action.status === "completed" ? iconSvg("check") : index + 1}</span>
        <span><b>${escapeHtml(action.target || "Agent 动作")}</b><small>${escapeHtml(action.summary)}</small></span>
        <em>${escapeHtml(action.statusLabel)}</em>
      </button>`).join("");
    return `<section class="auri-action-board"><header><b>处理队列</b><span>${vm.actions.counts.completed}/${vm.actions.counts.total} 完成</span></header>${actions}</section>`;
  }

  function syncBoardContent(vm) {
    const primary = vm.lifecycle.primarySurface;
    const devices = [
      ["phone", "手机", primary === "mobile" ? "当前主交互端" : vm.utterance.available ? "语音与任务已同步" : "任务与权限中心", primary === "mobile" || vm.utterance.available],
      ["watch", "腕上设备", vm.wearable.connected ? `${vm.wearable.text} · ${vm.wearable.modeLabel}` : "尚未连接", vm.wearable.connected],
      ["car", "车机", primary === "vehicle_hmi" ? "驾驶主交互端" : "导航与状态已就绪", connectionStatus.type === "streaming"]
    ];
    return `<section class="auri-sync-board">
      <div class="auri-sync-line" aria-hidden="true"></div>
      ${devices.map(([icon, title, copy, active]) => `<article class="${active ? "is-online" : "is-offline"}">
        <span>${iconSvg(icon)}</span><b>${escapeHtml(title)}</b><small>${escapeHtml(copy)}</small><em>${active ? "已同步" : "等待"}</em>
      </article>`).join("")}
      <footer>${iconSvg("link")}<span><b>World State r${vm.meta.revision < 0 ? "--" : vm.meta.revision}</b><small>${connectionStatus.type === "streaming" ? "实时流已连接" : "正在恢复连接"}</small></span></footer>
    </section>`;
  }

  function vehicleBoardContent(vm) {
    const draft = ensureClimateDraft();
    const modeLabels = { auto: "自动", cool: "制冷", heat: "制热", fan: "送风" };
    const fanLabels = { low: "低", medium: "中", high: "高" };
    return `<section class="auri-climate-board${draft.ac_on ? " is-on" : " is-off"}" data-climate-board>
      <header>
        <span class="auri-climate-symbol">${iconSvg("climate")}</span>
        <span><small>车内舒适</small><b>${draft.ac_on ? "空调运行中" : "空调已关闭"}</b></span>
        <button type="button" class="auri-power-toggle${draft.ac_on ? " is-on" : ""}" data-climate-control="power" aria-pressed="${draft.ac_on}">${iconSvg("power")}<span>${draft.ac_on ? "关闭" : "开启"}</span></button>
      </header>
      <div class="auri-temperature-control">
        <button type="button" data-climate-control="temperature-down" aria-label="降低温度">${iconSvg("minus")}</button>
        <div><strong data-climate-temperature>${Number(draft.ac_target_temp).toFixed(Number.isInteger(draft.ac_target_temp) ? 0 : 1)}</strong><span>°C</span><small>目标温度</small></div>
        <button type="button" data-climate-control="temperature-up" aria-label="提高温度">${iconSvg("add")}</button>
      </div>
      <div class="auri-climate-setting"><label>运行模式</label><div class="auri-segmented" data-climate-group="mode">
        ${Object.entries(modeLabels).map(([value, label]) => `<button type="button" data-climate-mode="${value}" class="${draft.ac_mode === value ? "is-active" : ""}">${label}</button>`).join("")}
      </div></div>
      <div class="auri-climate-setting"><label>风量</label><div class="auri-segmented" data-climate-group="fan">
        ${Object.entries(fanLabels).map(([value, label]) => `<button type="button" data-climate-fan="${value}" class="${draft.fan_speed === value ? "is-active" : ""}">${label}风</button>`).join("")}
      </div></div>
      <div class="auri-climate-sync">
        <span>${iconSvg("link")}</span><span><b>${climateRequest ? "正在同步设置" : climateError ? "同步未完成" : "手机与车机使用同一状态"}</b><small>${climateError ? escapeHtml(climateError) : `World State · revision ${vm.meta.revision < 0 ? "--" : vm.meta.revision}`}</small></span>
      </div>
      <button type="button" class="auri-climate-apply" data-climate-control="apply" ${climateRequest || !climateDraftDirty ? "disabled" : ""}>${climateRequest ? "正在同步…" : climateDraftDirty ? "应用到座舱" : "设置已同步"}</button>
    </section>`;
  }

  function routeBoardContent(vm) {
    const remainingMeters = Number(routeMeta?.remainingDistanceMeters);
    const remaining = Number.isFinite(remainingMeters) ? (remainingMeters >= 1000 ? `${(remainingMeters / 1000).toFixed(1)} 公里` : `${Math.round(remainingMeters)} 米`) : "--";
    return `<section class="auri-route-board">
      <header><span>${iconSvg("route")}</span><div><small>当前目的地</small><h3>${escapeHtml(vm.navigation.destination)}</h3></div></header>
      <div class="auri-route-metrics"><article><small>预计到达</small><b>${escapeHtml(vm.navigation.etaLabel)}</b></article><article><small>剩余距离</small><b>${escapeHtml(remaining)}</b></article><article><small>路况影响</small><b>${vm.risk.lateMinutes ? `晚 ${vm.risk.lateMinutes} 分钟` : "基本畅通"}</b></article></div>
      <div class="auri-route-next"><span>${iconSvg("route")}</span><span><small>下一步</small><b>${escapeHtml(routeMeta?.instruction || "等待导航指引")}</b></span><em>${routeMeta?.nextDistance ? `${routeMeta.nextDistance.value} ${routeMeta.nextDistance.unit}` : "--"}</em></div>
      <p>${mapStatus.mode === "online" ? "高德真实底图与路线 · 车辆位置为 Demo 状态回放" : "当前使用离线路线演示"}</p>
    </section>`;
  }

  function panelFor(section) {
    const vm = viewModel;
    if (section === "auri") {
      const [, summaryTitle, summaryCopy] = driverSummaryView();
      return {
        title: "AURI",
        subtitle: vm.lifecycle.stageLabel === "数据不可用" ? "已就绪" : vm.lifecycle.stageLabel,
        lead: summaryTitle,
        copy: vm.utterance.available
          ? `手机语音：${vm.utterance.preview}`
          : summaryCopy,
        status: vm.risk.label,
        tone: vm.risk.tone,
        rows: [
          vm.utterance.available
            ? row("声", "手机语音", vm.utterance.preview, vm.utterance.receivedAtLabel || "刚刚", "processing")
            : emptyRow("声", "手机语音", "等待用户在手机端求助"),
          row(vm.wearable.connected ? "腕" : "○", "腕上设备", vm.wearable.connected ? vm.wearable.text : "尚未连接", vm.wearable.connected ? vm.wearable.modeLabel : "离线", vm.wearable.mode),
          rowButton("联", "设备状态", "查看手机、腕表与车机当前同步状态", "查看", "sync")
        ]
      };
    }

    if (section === "tasks") {
      const taskRows = vm.tasks.items.map((task) => rowButton(
        task.tone === "rigid" ? "刚" : "弹",
        task.displayTitle,
        task.location || (task.waitingParty.length ? task.waitingParty.join("、") : task.type),
        task.status,
        `task:${task.id}`,
        task.tone
      ));
      return {
        title: "今日任务",
        subtitle: vm.tasks.total ? `${vm.tasks.rigid} 项刚性 · ${vm.tasks.flexible} 项弹性` : "来自手机与 Agent",
        lead: vm.tasks.total ? `${vm.tasks.total} 项任务已同步` : "目前没有已同步任务",
        copy: vm.tasks.total
          ? "任务已按当前责任优先级排列。"
          : "请在手机端通过语音创建任务，车机会在状态更新后自动接续。",
        status: vm.tasks.completed ? `${vm.tasks.completed}/${vm.tasks.total} 已完成` : `${vm.tasks.total} 项`,
        tone: vm.tasks.total ? "processing" : "idle",
        content: taskBoardContent(vm),
        rows: taskRows.length
          ? [...taskRows, rowButton("路", "当前行程", `${vm.navigation.destination} · ETA ${vm.navigation.etaLabel}`, "查看", "route")]
          : [emptyRow("＋", "任务入口", "等待手机语音创建任务")]
      };
    }

    if (section.startsWith("task:")) {
      const taskId = section.slice("task:".length);
      const task = vm.tasks.items.find((item) => item.id === taskId);
      if (!task) return panelFor("tasks");
      return {
        title: task.title,
        subtitle: `${task.type} · ${task.status}`,
        lead: task.displayTitle,
        copy: task.location
          ? `地点：${task.location}${task.waitingParty.length ? ` · 关联：${task.waitingParty.join("、")}` : ""}`
          : task.waitingParty.length ? `关联：${task.waitingParty.join("、")}` : "暂无更多任务说明。",
        status: task.status,
        tone: task.tone === "rigid" ? "warning" : "processing",
        rows: [
          row(task.tone === "rigid" ? "刚" : "弹", task.type, task.tone === "rigid" ? "优先保护时间窗口" : "可由 Agent 调整顺序", task.status, task.tone),
          task.location
            ? rowButton("路", "导航目的地", task.location, "查看", "route")
            : row("○", "导航目的地", "当前任务未提供地点", "无路线"),
          rowButton("返", "返回任务列表", "查看全部刚性与弹性任务", "返回", "tasks")
        ]
      };
    }

    if (section === "messages") {
      const actionRows = vm.actions.items.map((action) => rowButton(
        action.type === "message" ? "信" : action.type === "service_order" ? "单" : "调",
        action.target || "Agent 动作",
        action.summary,
        action.statusLabel,
        `action:${action.id}`,
        action.status === "completed" ? "completed" : action.status === "failed" || action.status === "blocked" ? "error" : "processing"
      ));
      const orderRows = vm.serviceOrders.items
        .filter((order) => !vm.actions.items.some((action) => action.detailsRef === order.id))
        .map((order) => row(
          "单",
          "生活服务",
          `${order.itemCount} 件 · ${order.total === null ? "金额待定" : `${order.total} 元`} · ${order.deliveryWindow || "配送时间待定"}`,
          order.status,
          order.status === "submitted" ? "completed" : order.errorCode ? "error" : "processing"
        ));
      return {
        title: "处理进度",
        subtitle: vm.actions.counts.total ? `${vm.actions.counts.completed}/${vm.actions.counts.total} 已完成` : "等待 Agent 方案",
        lead: vm.actions.counts.total ? `${vm.actions.counts.total} 项动作已准备或执行` : "暂无 Agent 动作",
        copy: vm.actions.counts.total
          ? "消息、任务调整和生活服务按执行状态排列。"
          : "AURI 会在需要时准备消息、任务调整和生活服务。",
        status: vm.actions.counts.failed || vm.actions.counts.blocked ? "需要注意" : vm.actions.counts.total ? "状态已同步" : "等待",
        tone: vm.actions.counts.failed || vm.actions.counts.blocked ? "critical" : vm.actions.counts.completed === vm.actions.counts.total && vm.actions.counts.total ? "success" : "processing",
        content: actionBoardContent(vm),
        rows: actionRows.length || orderRows.length ? [...actionRows, ...orderRows] : [emptyRow("□", "消息与服务", "等待 Agent 生成处理方案")]
      };
    }

    if (section.startsWith("action:")) {
      const actionId = section.slice("action:".length);
      const action = vm.actions.items.find((item) => item.id === actionId);
      if (!action) return panelFor("messages");
      const order = vm.serviceOrders.items.find((item) => item.id === action.detailsRef);
      const detailRows = [
        action.type === "message"
          ? row("信", action.target || "联系人", action.summary, action.statusLabel, action.status === "completed" ? "completed" : "processing")
          : row("调", "Agent 动作", action.summary, action.statusLabel, action.status === "completed" ? "completed" : "processing")
      ];
      if (order) detailRows.push(row("单", `${order.itemCount} 件商品`, `${order.total === null ? "金额待定" : `${order.total} 元`} · ${order.deliveryWindow || "配送时间待定"}`, order.status, order.errorCode ? "error" : order.status === "submitted" ? "completed" : "processing"));
      detailRows.push(rowButton("返", "返回动作列表", "查看全部消息、任务调整和生活服务", "返回", "messages"));
      return {
        title: action.type === "message" ? `给${action.target || "联系人"}的消息` : action.type === "service_order" ? "生活服务方案" : "任务调整详情",
        subtitle: action.statusLabel,
        lead: action.summary,
        copy: action.requiresConfirmation ? "确认后，AURI 将执行这项处理。" : "处理结果已同步到车机。",
        status: action.statusLabel,
        tone: action.status === "completed" ? "success" : action.status === "failed" || action.status === "blocked" ? "critical" : "processing",
        rows: detailRows
      };
    }

    if (section === "route") {
      const remainingMeters = Number(routeMeta?.remainingDistanceMeters);
      const remaining = Number.isFinite(remainingMeters)
        ? remainingMeters >= 1000
          ? `${(remainingMeters / 1000).toFixed(1)} 公里`
          : `${Math.round(remainingMeters)} 米`
        : "等待路线数据";
      return {
        title: "行程详情",
        subtitle: mapStatus.mode === "online" ? "高德导航 · Demo 定位" : "离线导航",
        lead: vm.navigation.hasDestination ? vm.navigation.destination : "等待手机同步路线",
        copy: vm.risk.lateMinutes
          ? `当前预计晚到 ${vm.risk.lateMinutes} 分钟，请保持安全驾驶。`
          : vm.navigation.hasEta ? `预计 ${vm.navigation.etaLabel} 到达。` : "任务建立后会自动准备路线。",
        status: vm.risk.label,
        tone: vm.risk.tone,
        content: routeBoardContent(vm),
        rows: [
          row("时", "预计到达", vm.navigation.taskTitle || "当前导航任务", vm.navigation.etaLabel, vm.risk.lateMinutes ? "warning" : "success"),
          row("路", "下一动作", routeMeta?.instruction || "等待导航指引", routeMeta?.nextDistance ? `${routeMeta.nextDistance.value}${routeMeta.nextDistance.unit}` : "--", "processing"),
          row("距", "剩余距离", mapStatus.mode === "online" ? "路线随车辆位置更新" : "离线演示路线", remaining, mapStatus.mode === "online" ? "success" : "idle"),
          rowButton("务", "沿途任务", vm.tasks.total ? `${vm.tasks.total} 项任务待处理` : "当前无任务", "查看", "tasks")
        ]
      };
    }

    if (section === "sync") {
      const primary = vm.lifecycle.primarySurface;
      const phoneState = primary === "mobile" ? "当前主端" : vm.utterance.available ? "语音已同步" : "保持连接";
      const carState = primary === "vehicle_hmi" ? "当前主端" : vm.lifecycle.stage === "parked_review" ? "本次结束" : "只读显示";
      return {
        title: "设备同步",
        subtitle: "手机 · 腕表 · 车机",
        lead: primary === "vehicle_hmi" ? "驾驶任务已接续到车机" : primary === "mobile" ? "手机正在管理完整信息" : "当前保持低干扰",
        copy: vm.lifecycle.stage === "parked_review"
          ? "停车后，消息、订单和处理记录回到手机继续查看。"
          : "各端显示同一任务和处理结果，操作入口跟随当前场景切换。",
        status: connectionStatus.type === "streaming" ? "状态已同步" : "正在同步",
        tone: connectionStatus.type === "streaming" ? "success" : "processing",
        content: syncBoardContent(vm),
        rows: [
          row("手", "手机", vm.utterance.available ? `最近语音：“${vm.utterance.preview}”` : "任务与权限中心", phoneState, primary === "mobile" ? "success" : "processing"),
          row("腕", "腕表", vm.wearable.connected ? `${vm.wearable.text} · ${HAPTIC_LABEL[vm.wearable.haptic] || "无触觉"}` : "连接状态待更新", vm.wearable.connected ? vm.wearable.modeLabel : "离线", vm.wearable.connected ? vm.wearable.mode : "idle"),
          row("车", "车机", vm.navigation.hasDestination ? `导航至 ${vm.navigation.destination}` : "等待路线", carState, primary === "vehicle_hmi" ? "success" : "processing")
        ]
      };
    }

    if (section === "vehicle") {
      const climate = vm.vehicle;
      return {
        title: "座舱状态",
        subtitle: "座舱舒适",
        lead: climate.available ? climate.summary : "等待座舱状态同步",
        copy: "车机控制经 Agent 校验后写入共享状态，手机同步显示结果。",
        status: climate.available ? "状态已同步" : "等待",
        tone: climate.available ? "success" : "idle",
        content: vehicleBoardContent(vm),
        rows: [
          row("温", "空调与温度", climate.available ? `${climate.mode} · ${climate.fan}` : "暂无有效车辆数据", climate.temperatureLabel, climate.acOn ? "processing" : "idle")
        ]
      };
    }

    return null;
  }

  function connectionPanel() {
    const config = client.getConfig();
    const statusLabel = STATUS_VIEW[connectionStatus.type]?.[0] || "等待连接";
    const session = viewModel.meta.sessionId ? `…${viewModel.meta.sessionId.slice(-8)}` : "--";
    const revision = viewModel.meta.revision >= 0 ? String(viewModel.meta.revision) : "--";
    const schema = lastHealth?.schema_version || viewModel.meta.schemaVersion || "--";
    return {
      title: "连接 Agent",
      subtitle: statusLabel,
      lead: "选择本地或公网 Agent 服务",
      copy: lastError ? `最近一次连接未成功：${lastError}` : "连接后，任务、路线和处理状态将自动同步。",
      status: statusLabel,
      tone: STATUS_VIEW[connectionStatus.type]?.[1] || "idle",
      form: `
        <form class="auri-config-form" id="auri-config-form">
          <div class="auri-connection-summary">
            <span><small>同步方式</small><b data-connection-metric="sync">${escapeHtml(connectionStatus.type === "streaming" ? "实时流" : connectionStatus.type === "polling_fallback" ? "轮询恢复" : statusLabel)}</b></span>
            <span><small>Session</small><b data-connection-metric="session">${escapeHtml(session)}</b></span>
            <span><small>Revision</small><b data-connection-metric="revision">${escapeHtml(revision)}</b></span>
            <span><small>Schema</small><b data-connection-metric="schema">${escapeHtml(schema)}</b></span>
            <span><small>Agent Health</small><b data-connection-metric="health">${escapeHtml(lastHealth?.status === "ok" ? "正常" : lastHealth ? "异常" : "等待预检")}</b></span>
            <span><small>LLM</small><b data-connection-metric="llm">${escapeHtml(lastHealth?.llm_model ? `${lastHealth.llm_model} · ${lastHealth.llm_last_mode || "待调用"}` : "状态未提供")}</b></span>
          </div>
          <label><span>Agent API</span><input id="auri-config-api" type="url" spellcheck="false" value="${escapeHtml(config.apiBase)}" required></label>
          <label><span>Team Token</span><input id="auri-config-token" type="password" autocomplete="off" value="${escapeHtml(config.token)}" placeholder="仅保存在当前浏览器"></label>
          <div class="auri-config-presets">
            <button type="button" data-api="https://auri-agent-api.onrender.com">公网服务</button>
            <button type="button" data-api="https://auri-langchain-agent-api.onrender.com">LangChain 服务</button>
            <button type="button" data-api="http://127.0.0.1:8000">本地服务</button>
          </div>
          <details class="auri-map-config">
            <summary>地图连接设置 <span data-connection-map-status>${escapeHtml(MAP_STATUS_VIEW[mapStatus.mode]?.[0] || "离线导航")}</span></summary>
            <label><span>地图模式</span><select id="auri-config-map-provider">
              <option value="auto"${config.mapProvider === "auto" ? " selected" : ""}>自动读取 Agent 配置</option>
              <option value="amap"${config.mapProvider === "amap" ? " selected" : ""}>高德 Web JS API</option>
              <option value="offline"${config.mapProvider === "offline" ? " selected" : ""}>Bosch 离线地图</option>
            </select></label>
            <label><span>高德 Web Key</span><input id="auri-config-amap-key" type="password" autocomplete="off" value="${escapeHtml(config.amapKey)}" placeholder="仅保存在当前浏览器"></label>
            <label><span>高德安全码（可选）</span><input id="auri-config-amap-security" type="password" autocomplete="off" value="${escapeHtml(config.amapSecurityJsCode)}" placeholder="本机地图连接时填写"></label>
            <label><span>安全代理地址</span><input id="auri-config-amap-host" type="url" spellcheck="false" value="${escapeHtml(config.amapServiceHost)}" placeholder="由 /v1/map-config 自动提供"></label>
          </details>
          <button class="auri-config-submit" type="submit">保存并连接</button>
        </form>
      `
    };
  }

  function refreshConnectionPanel() {
    if (activeSection !== "connection") return;
    const panel = connectionPanel();
    const body = document.getElementById("auri-detail-body");
    if (!body?.querySelector("#auri-config-form")) return;
    const subtitle = document.getElementById("auri-detail-subtitle");
    const copy = body.querySelector(".auri-shell-copy");
    const status = body.querySelector(".auri-shell-status");
    if (subtitle) subtitle.textContent = panel.subtitle;
    if (copy) copy.textContent = panel.copy;
    if (status) {
      status.textContent = panel.status;
      status.className = `auri-shell-status is-${panel.tone || "idle"}`;
    }
    const statusLabel = STATUS_VIEW[connectionStatus.type]?.[0] || "等待连接";
    const values = {
      sync: connectionStatus.type === "streaming" ? "实时流" : connectionStatus.type === "polling_fallback" ? "轮询恢复" : statusLabel,
      session: viewModel.meta.sessionId ? `…${viewModel.meta.sessionId.slice(-8)}` : "--",
      revision: viewModel.meta.revision >= 0 ? String(viewModel.meta.revision) : "--",
      schema: lastHealth?.schema_version || viewModel.meta.schemaVersion || "--",
      health: lastHealth?.status === "ok" ? "正常" : lastHealth ? "异常" : "等待预检",
      llm: lastHealth?.llm_model ? `${lastHealth.llm_model} · ${lastHealth.llm_last_mode || "待调用"}` : "状态未提供"
    };
    Object.entries(values).forEach(([key, value]) => {
      const node = body.querySelector(`[data-connection-metric="${key}"]`);
      if (node) node.textContent = value;
    });
    const map = body.querySelector("[data-connection-map-status]");
    if (map) map.textContent = MAP_STATUS_VIEW[mapStatus.mode]?.[0] || "离线导航";
  }

  function closePanel() {
    const panel = document.getElementById("auri-driver-panel");
    const detail = document.getElementById("auri-driver-detail");
    panel?.classList.remove("is-detail");
    if (detail) detail.hidden = true;
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
      const apiBase = document.getElementById("auri-config-api")?.value;
      agentModule.saveConfig({
        ...client.getConfig(),
        apiBase,
        streamUrl: `${String(apiBase || "").trim().replace(/\/$/, "")}/v1/stream`,
        token: document.getElementById("auri-config-token")?.value,
        mapProvider: document.getElementById("auri-config-map-provider")?.value,
        amapKey: document.getElementById("auri-config-amap-key")?.value,
        amapSecurityJsCode: document.getElementById("auri-config-amap-security")?.value,
        amapServiceHost: document.getElementById("auri-config-amap-host")?.value
      });
      window.location.reload();
    });
  }

  function refreshClimatePanel() {
    if (activeSection === "vehicle") openPanel("vehicle");
  }

  function updateClimateDraft(patch) {
    const draft = ensureClimateDraft();
    climateDraft = { ...draft, ...patch };
    climateDraftDirty = true;
    climateError = null;
    climateRequest = null;
    refreshClimatePanel();
  }

  async function submitClimateSettings() {
    if (climateRequest?.inFlight) return;
    const draft = ensureClimateDraft();
    if (!climateDraftDirty) return;
    if (!climateRequest) {
      climateRequest = {
        eventId: `hmi_climate_${Date.now()}_${Math.random().toString(16).slice(2, 9)}`,
        timestamp: new Date().toISOString(),
        payload: { ...draft }
      };
    }
    climateRequest.inFlight = true;
    climateError = null;
    refreshClimatePanel();
    try {
      await client.submitEvent("vehicle.control", climateRequest.payload, {
        eventId: climateRequest.eventId,
        timestamp: climateRequest.timestamp,
        source: "vehicle_hmi"
      });
      climateRequest = null;
      climateDraftDirty = false;
      climateDraft = null;
      climateError = null;
    } catch (error) {
      climateRequest.inFlight = false;
      climateError = error?.message || "Agent 未接受本次设置";
      lastError = climateError;
    }
    refreshClimatePanel();
  }

  function bindPanelInteractions() {
    const body = document.getElementById("auri-detail-body");
    if (!body) return;
    body.querySelectorAll("[data-panel-target]").forEach((button) => {
      button.addEventListener("click", () => openPanel(button.dataset.panelTarget));
    });
    body.querySelector('[data-climate-control="power"]')?.addEventListener("click", () => {
      const draft = ensureClimateDraft();
      updateClimateDraft({ ac_on: !draft.ac_on });
    });
    body.querySelector('[data-climate-control="temperature-down"]')?.addEventListener("click", () => {
      const draft = ensureClimateDraft();
      updateClimateDraft({ ac_target_temp: Math.max(16, Number(draft.ac_target_temp) - 0.5), ac_on: true });
    });
    body.querySelector('[data-climate-control="temperature-up"]')?.addEventListener("click", () => {
      const draft = ensureClimateDraft();
      updateClimateDraft({ ac_target_temp: Math.min(30, Number(draft.ac_target_temp) + 0.5), ac_on: true });
    });
    body.querySelectorAll("[data-climate-mode]").forEach((button) => {
      button.addEventListener("click", () => updateClimateDraft({ ac_mode: button.dataset.climateMode, ac_on: true }));
    });
    body.querySelectorAll("[data-climate-fan]").forEach((button) => {
      button.addEventListener("click", () => updateClimateDraft({ fan_speed: button.dataset.climateFan, ac_on: true }));
    });
    body.querySelector('[data-climate-control="apply"]')?.addEventListener("click", () => void submitClimateSettings());
  }

  function openPanel(section) {
    if (section === "navigation" || section === "home") {
      closePanel();
      return;
    }
    const config = section === "connection" ? connectionPanel() : panelFor(section);
    const panel = document.getElementById("auri-driver-panel");
    const detail = document.getElementById("auri-driver-detail");
    if (!config || !panel) return;

    activeSection = section;
    panel.classList.add("is-detail");
    if (detail) detail.hidden = false;
    const title = document.getElementById("auri-detail-title");
    const subtitle = document.getElementById("auri-detail-subtitle");
    const body = document.getElementById("auri-detail-body");
    if (title) title.textContent = config.title;
    if (subtitle) subtitle.textContent = config.subtitle;
    if (body) {
      body.classList.toggle("is-custom", Boolean(config.content));
      body.innerHTML = config.content
        ? `<div class="auri-shell-content is-custom">${config.content}</div>`
        : `<div class="auri-shell-content">
            <p class="auri-shell-lead">${escapeHtml(config.lead)}</p>
            <p class="auri-shell-copy">${escapeHtml(config.copy)}</p>
            <span class="auri-shell-status is-${escapeHtml(config.tone || "idle")}">${escapeHtml(config.status)}</span>
            ${config.form || config.rows.join("")}
          </div>`;
    }
    document.querySelectorAll("[data-auri-section]").forEach((item) => {
      item.classList.toggle("active", item.dataset.auriSection === section);
    });
    bindConfigForm();
    bindPanelInteractions();
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
      item.removeAttribute("aria-disabled");
      item.setAttribute("role", "button");
      item.setAttribute("tabindex", "0");
      item.title = item.classList.contains("bb-arr-blue") ? "降低座舱温度" : "提高座舱温度";
      const activate = () => {
        const draft = ensureClimateDraft();
        const delta = item.classList.contains("bb-arr-blue") ? -0.5 : 0.5;
        updateClimateDraft({ ac_target_temp: Math.max(16, Math.min(30, Number(draft.ac_target_temp) + delta)), ac_on: true });
        openPanel("vehicle");
      };
      item.addEventListener("click", activate);
      item.addEventListener("keydown", (event) => {
        if (event.key === "Enter" || event.key === " ") activate();
      });
    });
    document.getElementById("bb-fan-awake")?.addEventListener("click", () => openPanel("vehicle"));
  }

  function bindDock() {
    document.querySelectorAll(".bb-dock-icon[data-icon]").forEach((item) => {
      item.innerHTML = iconSvg(item.dataset.icon);
    });
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
      if (event.key === "Enter" && !["INPUT", "SELECT", "TEXTAREA", "BUTTON"].includes(event.target?.tagName)) {
        const button = document.getElementById("auri-takeover-confirm");
        if (button && !button.hidden && !button.disabled) {
          event.preventDefault();
          void confirmCurrentActions("button");
        }
      }
    }, true);
  }

  function renderConnectionStatus(next) {
    connectionStatus = next;
    if (next.health) lastHealth = next.health;
    if (next.type === "streaming") lastError = null;
    const [label, tone] = STATUS_VIEW[next.type] || STATUS_VIEW.idle;
    const chip = document.getElementById("tb-offline");
    if (chip) {
      chip.textContent = label;
      chip.dataset.tone = tone;
      chip.title = `${label} · 点击配置 Agent`;
    }
    refreshConnectionPanel();
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
    const stageProgress = vm.navigation.route?.progress ?? STAGE_PROGRESS[vm.lifecycle.stage] ?? 0.03;
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
    const routeMinutes = Number.isFinite(Number(routeMeta?.remainingDurationSeconds))
      ? Math.max(1, Math.round(Number(routeMeta.remainingDurationSeconds) / 60))
      : null;
    const etaTime = Date.parse(vm.navigation.etaIso || "");
    const updatedTime = Date.parse(vm.meta.updatedAt || "");
    const etaMinutes = Number.isFinite(etaTime) && Number.isFinite(updatedTime)
      ? Math.round((etaTime - updatedTime) / 60000)
      : null;
    const remainingMinutes = routeMinutes ?? (etaMinutes > 0 && etaMinutes <= 360 ? etaMinutes : null);
    if (minutes) minutes.textContent = remainingMinutes === null ? "--" : String(remainingMinutes);
    if (kilometers) kilometers.textContent = remainingKm === null ? "--" : remainingKm >= 10 ? String(Math.round(remainingKm)) : remainingKm.toFixed(1);
    if (minutes?.nextElementSibling) minutes.nextElementSibling.textContent = "剩余分钟";
    if (kilometers?.nextElementSibling) kilometers.nextElementSibling.textContent = "剩余公里";
    if (progress) progress.style.width = `${Math.round(stageProgress * 100)}%`;
    renderTurnArrow(routeMeta?.maneuver || "straight");

    const hudDistance = document.getElementById("auri-nav-next-distance");
    const hudRoad = document.getElementById("auri-nav-next-road");
    const hudTime = document.getElementById("auri-nav-remaining-time");
    const hudRemaining = document.getElementById("auri-nav-remaining-distance");
    const hudManeuver = document.getElementById("auri-nav-maneuver");
    if (hudDistance) hudDistance.textContent = routeMeta?.nextDistance
      ? `${routeMeta.nextDistance.value} ${routeMeta.nextDistance.unit}`
      : vm.navigation.hasDestination ? "路线已接续" : "等待路线";
    if (hudRoad) hudRoad.textContent = routeMeta?.instruction || (vm.navigation.hasDestination ? `前往 ${vm.navigation.destination}` : "手机同步目的地后开始导航");
    if (hudTime) hudTime.textContent = remainingMinutes === null ? `ETA ${vm.navigation.etaLabel}` : `${remainingMinutes} 分钟`;
    if (hudRemaining) hudRemaining.textContent = remainingKm === null ? vm.navigation.destination : `${remainingKm >= 10 ? Math.round(remainingKm) : remainingKm.toFixed(1)} 公里 · ${vm.navigation.etaLabel}`;
    if (hudManeuver) {
      hudManeuver.dataset.maneuver = routeMeta?.maneuver || "straight";
      hudManeuver.innerHTML = maneuverSvg(routeMeta?.maneuver || "straight");
    }
  }

  function maneuverSvg(maneuver) {
    const paths = {
      left: '<path d="M18 20v-7a5 5 0 0 0-5-5H5"/><path d="m9 4-4 4 4 4"/>',
      right: '<path d="M6 20v-7a5 5 0 0 1 5-5h8"/><path d="m15 4 4 4-4 4"/>',
      uturn: '<path d="M18 20V11a6 6 0 0 0-12 0v4"/><path d="m3 12 3 3 3-3"/>',
      arrive: '<path d="M12 20V5"/><path d="m7 10 5-5 5 5"/>',
      straight: '<path d="M12 20V5"/><path d="m7 10 5-5 5 5"/>'
    };
    return `<svg class="auri-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[maneuver] || paths.straight}</svg>`;
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

  function renderDrivingStatus() {
    const driving = ["driving", "high_load_driving"].includes(viewModel.lifecycle.scene);
    const speed = document.getElementById("vd-speed");
    const arrow = document.getElementById("vd-speed-arrow");
    if (speed) {
      speed.textContent = driving ? "68" : "0";
      speed.title = driving ? "Demo 车辆信号" : "车辆未在行驶";
    }
    if (arrow) arrow.hidden = !driving;
  }

  function animateStage() {
    const stage = viewModel.lifecycle.stage;
    if (stage === lastAnimatedStage) return;
    lastAnimatedStage = stage;
    const progress = viewModel.navigation.route?.progress ?? STAGE_PROGRESS[stage];
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
    const location = String(task?.location || "").trim();
    return COMPAT_DEMO_DESTINATIONS.find((item) => item.aliases.includes(location))?.coordinates || null;
  }

  function routeDefinition() {
    const contractRoute = viewModel.navigation.route;
    if (contractRoute?.origin?.coordinates && contractRoute?.destination?.coordinates) {
      return {
        id: contractRoute.id,
        start: contractRoute.origin.coordinates,
        end: contractRoute.destination.coordinates,
        originName: contractRoute.origin.name || contractRoute.origin.address || "出发地",
        destinationName: contractRoute.destination.name || contractRoute.destination.address || "目的地"
      };
    }
    const task = viewModel.tasks.navigation;
    const end = coordinatesFromTask(task);
    if (!task || !end) return null;
    const known = COMPAT_DEMO_DESTINATIONS.find((item) => item.coordinates[0] === end[0] && item.coordinates[1] === end[1]);
    return {
      id: `compat:${task.id || task.location || task.title}`,
      start: COMPAT_ROUTE_ORIGIN.coordinates,
      end,
      originName: COMPAT_ROUTE_ORIGIN.name,
      destinationName: task.location || known?.name || task.title || "目的地"
    };
  }

  function navigationSnapshot() {
    const stage = viewModel.lifecycle.stage;
    const progress = viewModel.navigation.route?.progress ?? STAGE_PROGRESS[stage] ?? 0.03;
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
    const routeKey = `${viewModel.meta.sessionId || "no-session"}:${route.id || viewModel.tasks.navigation?.id || viewModel.navigation.destination}:${route.end.join(",")}`;
    await mapAdapter.setRoute(route, routeKey);
    mapAdapter.update(navigationSnapshot());
  }

  function renderMapStatus(next) {
    const previousMode = mapStatus.mode;
    mapStatus = next;
    const source = document.getElementById("auri-map-source");
    const navSource = document.getElementById("auri-nav-source");
    const controls = document.getElementById("auri-map-controls");
    const [label, tone] = MAP_STATUS_VIEW[next.mode] || [next.message || "离线导航", "offline"];
    if (source) {
      source.textContent = next.message || label;
      source.dataset.mode = tone;
    }
    if (navSource) {
      navSource.textContent = label;
      navSource.dataset.mode = tone;
    }
    if (controls) controls.hidden = next.mode !== "online";
    if (next.mode === "online") {
      try { window.mapCarStop?.(); } catch (_error) { /* offline controller is optional */ }
    } else if (previousMode === "online") {
      lastAnimatedStage = null;
      animateStage();
    }
    refreshConnectionPanel();
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
    if (lastConfirmationId !== viewModel.interaction.confirmationId) {
      confirmError = null;
      confirmOutcomeUnknown = false;
      lastConfirmationId = viewModel.interaction.confirmationId;
    }
    if (viewModel.lifecycle.stage !== "waiting_confirmation") confirmOutcomeUnknown = false;
    const hmi = document.getElementById("hmi");
    if (hmi) {
      hmi.dataset.auriStage = viewModel.lifecycle.stage;
      hmi.dataset.auriRisk = viewModel.risk.tone;
      hmi.dataset.auriPrimarySurface = viewModel.lifecycle.primarySurface;
    }
    renderNavigation();
    renderResponsibilityStrip();
    renderDrivingStatus();
    renderClimate();
    renderDriverPanel();
    renderTakeover();
    renderDeviceNotice();
    renderStageNotice();
    announceCompletion();
    animateStage();
    if (mapAdapter.getStatus() === "online") mapAdapter.update(navigationSnapshot());
    void ensureMapRoute();
    if (activeSection === "connection") refreshConnectionPanel();
    else if (activeSection) openPanel(activeSection);
  }

  const client = agentModule.createClient({
    config: agentModule.loadConfig(),
    onStatus: renderConnectionStatus,
    onError(error) {
      lastError = error?.status === 401
        ? "Team Token 无效或缺失"
        : error?.status === 503
          ? "Agent 服务正在启动或暂不可用"
        : error?.code === "TIMEOUT"
          ? "请求超时，公网服务可能正在唤醒"
          : error?.name === "TypeError"
            ? "网络不可达，请检查服务地址或浏览器网络"
            : "无法连接 Agent 服务";
      refreshConnectionPanel();
    }
  });
  client.subscribe((state) => renderWorldState(state));

  function applyShell() {
    prepareTopBar();
    prepareClimateControls();
    replaceCarBranding();
    ensureTakeoverUi();
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
        map: { status: mapAdapter.getStatus(), cameraMode: mapAdapter.getCameraMode(), cameraHeading: mapAdapter.getCameraHeading(), cameraRotation: mapAdapter.getCameraRotation(), usage: mapAdapter.getUsage(), routeMeta }
      };
    },
    openPanel,
    closePanel
  };

  window.addEventListener("beforeunload", () => client.stop(), { once: true });
  window.addEventListener("storage", (event) => {
    if (event.key !== agentModule.SHARED_STORAGE_KEY || !event.newValue) return;
    try {
      const shared = JSON.parse(event.newValue);
      if (!shared.apiBase) return;
      const apiBase = String(shared.apiBase).trim().replace(/\/$/, "");
      const current = client.getConfig();
      if (apiBase === current.apiBase && String(shared.token || "") === current.token) return;
      client.reconfigure({
        ...current,
        apiBase,
        streamUrl: `${apiBase}/v1/stream`,
        token: String(shared.token || "")
      });
    } catch (_error) {
      lastError = "共享连接配置无效";
      renderConnectionStatus({ type: "polling_fallback" });
    }
  });
  if (document.readyState === "complete") applyShell();
  else window.addEventListener("load", () => requestAnimationFrame(applyShell), { once: true });
})();
