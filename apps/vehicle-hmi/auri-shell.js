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

  function rowButton(icon, title, detail, state, target, tone = "") {
    return `
      <button class="auri-shell-row auri-shell-row-button${tone ? ` is-${tone}` : ""}" type="button" data-panel-target="${escapeHtml(target)}">
        <span class="auri-shell-row-icon" aria-hidden="true">${escapeHtml(icon)}</span>
        <span class="auri-shell-row-copy"><strong>${escapeHtml(title)}</strong><span>${escapeHtml(detail)}</span></span>
        <span class="auri-shell-row-state">${escapeHtml(state)}</span>
      </button>
    `;
  }

  function ensureTakeoverUi() {
    const host = document.querySelector(".vd-half-bot");
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
      <p class="auri-takeover-conclusion" id="auri-takeover-conclusion"></p>
      <div class="auri-takeover-actions" id="auri-takeover-actions"></div>
      <div class="auri-takeover-devices" id="auri-takeover-devices"></div>
      <button class="auri-takeover-confirm" id="auri-takeover-confirm" type="button" hidden>
        <span id="auri-confirm-label">确认处理</span><small>方向盘 OK / 点击</small>
      </button>
      <p class="auri-confirm-error" id="auri-confirm-error" role="status" hidden></p>
    `;
    nav.insertAdjacentElement("afterend", card);
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
      <span class="auri-notice-icon" aria-hidden="true">腕</span>
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
      <span class="auri-stage-notice-icon" aria-hidden="true">A</span>
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
        icon: action.status === "completed" ? "✓" : action.type === "message" ? "信" : action.type === "service_order" ? "单" : "调",
        text: orderText || messageText || action.preview,
        state: action.statusLabel,
        completed: action.status === "completed"
      };
    });
    if (actions.length) return actions;
    if (["takeover_L2", "takeover_L3", "planning"].includes(viewModel.lifecycle.stage)) {
      return [
        { icon: "1", text: "核对 ETA 与刚性任务", state: "进行中" },
        { icon: "2", text: "寻找可后置或可代办事项", state: "等待" }
      ];
    }
    return [];
  }

  function renderTakeover() {
    const host = document.querySelector(".vd-half-bot");
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
    const utteranceLine = viewModel.utterance.available ? ` · 手机：“${viewModel.utterance.preview}”` : "";
    riskLine.textContent = stage === "parked_review"
      ? "车辆已停稳 · 完整明细已同步"
      : `${viewModel.risk.label}${utteranceLine}`;
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
          ? concisePlan
          : viewModel.agentOutput.available && viewModel.agentOutput.fullText.length <= 42
            ? viewModel.agentOutput.fullText
            : fallback;

    const actions = takeoverActions();
    document.getElementById("auri-takeover-actions").innerHTML = actions.map((action) => `
      <div class="auri-takeover-action${action.completed ? " is-completed" : ""}">
        <span>${escapeHtml(action.icon)}</span><b>${escapeHtml(action.text)}</b><small>${escapeHtml(action.state)}</small>
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
    if (notice) noticeHideTimer = setTimeout(() => { notice.hidden = true; }, 220);
  }

  function renderDeviceNotice() {
    const wearable = viewModel.wearable;
    if (viewModel.lifecycle.primarySurface !== "vehicle_hmi") return;
    if (!wearable.commandId || wearable.mode === "idle" || !wearable.haptic || wearable.haptic === "none") return;
    const commandKey = `${viewModel.meta.sessionId || "session"}:${wearable.commandId}`;
    if (notifiedDeviceCommands.has(commandKey)) return;
    notifiedDeviceCommands.add(commandKey);
    const notice = document.getElementById("auri-device-notice");
    if (!notice) return;
    const delivered = wearable.connected;
    const title = ["warning", "error"].includes(wearable.mode)
      ? delivered ? "腕上提醒已送达" : "腕上提醒等待同步"
      : wearable.mode === "completed"
        ? delivered ? "三端状态已同步" : "处理结果等待腕上同步"
        : delivered ? "腕上设备已接续" : "腕上状态等待设备连接";
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
    if (stage === "handover_to_vehicle") return ["场景切换", "路线正在同步到车机", `${destination} · 手机进入驾驶只读`, "handover"];
    if (stage === "vehicle_observation") return ["导航已接续", `正在前往 ${destination}`, "ETA 与任务状态会持续同步", "guidance"];
    if (stage === "action_completed") return ["处理完成", total ? `${completed}/${total} 项动作已完成` : "本次问题已处理", "手机、腕表与车机正在同步结果", "success"];
    if (stage === "cooldown") return ["恢复驾驶", "AURI 已降低打扰", "按当前路线继续即可", "success", true];
    if (stage === "parked_review") return ["本次接管结束", "完整记录已同步到手机", "消息、订单和处理结果可在手机查看", "success"];
    return null;
  }

  function renderStageNotice() {
    if (viewModel.lifecycle.primarySurface !== "vehicle_hmi") {
      const notice = document.getElementById("auri-stage-notice");
      if (notice && !notice.hidden) hideStageNotice();
      return;
    }
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
          : "手机语音已同步到当前行程。",
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
        title: "消息与执行",
        subtitle: vm.actions.counts.total ? `${vm.actions.counts.completed}/${vm.actions.counts.total} 已完成` : "等待 Agent 方案",
        lead: vm.actions.counts.total ? `${vm.actions.counts.total} 项动作已准备或执行` : "暂无 Agent 动作",
        copy: vm.agentOutput.available ? vm.agentOutput.fullText : "AURI 会在需要时准备消息、任务调整和生活服务。",
        status: vm.actions.counts.failed || vm.actions.counts.blocked ? "需要注意" : vm.actions.counts.total ? "状态已同步" : "等待",
        tone: vm.actions.counts.failed || vm.actions.counts.blocked ? "critical" : vm.actions.counts.completed === vm.actions.counts.total && vm.actions.counts.total ? "success" : "processing",
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
        subtitle: mapStatus.mode === "online" ? "高德实时导航" : "离线导航",
        lead: vm.navigation.hasDestination ? vm.navigation.destination : "等待手机同步路线",
        copy: vm.risk.lateMinutes
          ? `当前预计晚到 ${vm.risk.lateMinutes} 分钟，请保持安全驾驶。`
          : vm.navigation.hasEta ? `预计 ${vm.navigation.etaLabel} 到达。` : "任务建立后会自动准备路线。",
        status: vm.risk.label,
        tone: vm.risk.tone,
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
        rows: [
          row("手", "手机", vm.utterance.available ? `最近语音：“${vm.utterance.preview}”` : "任务与权限中心", phoneState, primary === "mobile" ? "success" : "processing"),
          row("腕", "腕表", vm.wearable.connected ? `${vm.wearable.text} · ${HAPTIC_LABEL[vm.wearable.haptic] || "无触觉"}` : "连接状态待更新", vm.wearable.connected ? vm.wearable.modeLabel : "离线", vm.wearable.connected ? vm.wearable.mode : "idle"),
          row("车", "车机", vm.navigation.hasDestination ? `导航至 ${vm.navigation.destination}` : "等待路线", carState, primary === "vehicle_hmi" ? "success" : "processing")
        ]
      };
    }

    if (section === "vehicle") {
      const climate = vm.vehicle;
      const heartRate = vm.wearable.heartRate ? `${vm.wearable.heartRate} bpm` : "未提供";
      return {
        title: "座舱状态",
        subtitle: "车辆与随行设备",
        lead: climate.available ? climate.summary : "等待座舱状态同步",
        copy: "车内舒适设置与腕上提醒保持同步。",
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
    const body = document.getElementById("body-a");
    if (!body?.querySelector("#auri-config-form")) return;
    const subtitle = document.getElementById("sub-a");
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
    body?.querySelectorAll("[data-panel-target]").forEach((button) => {
      button.addEventListener("click", () => openPanel(button.dataset.panelTarget));
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
    renderTakeover();
    renderDeviceNotice();
    renderStageNotice();
    announceCompletion();
    updateRoutePOIs();
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
    replaceRoutePOIs();
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
        map: { status: mapAdapter.getStatus(), cameraMode: mapAdapter.getCameraMode(), usage: mapAdapter.getUsage(), routeMeta }
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
