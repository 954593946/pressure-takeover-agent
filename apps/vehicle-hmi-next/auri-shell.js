(function initAuriCockpit() {
  "use strict";

  const model = window.AuriWorldStateModel;
  const agentModule = window.AuriAgentClient;
  if (!model || !agentModule) {
    console.error("[AURI] World State modules are unavailable");
    return;
  }

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

  let viewModel = model.buildVehicleHmiViewModel(null);
  let activeSection = null;
  let connectionStatus = { type: "idle" };
  let lastAnimatedStage = null;
  let lastError = null;

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
      const next = agentModule.saveConfig({
        ...client.getConfig(),
        apiBase: document.getElementById("auri-config-api")?.value,
        token: document.getElementById("auri-config-token")?.value
      });
      lastError = null;
      client.reconfigure(next);
      renderConnectionStatus({ type: "preflighting" });
      openPanel("connection");
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
      headline.innerHTML = vm.risk.lateMinutes
        ? `${vm.risk.lateMinutes}<span>分钟</span>`
        : vm.navigation.hasDestination
          ? `按时<span>行驶</span>`
          : `--<span>路线</span>`;
    }
    if (instruction) instruction.textContent = vm.navigation.hasDestination ? vm.navigation.destination : "等待手机同步路线";
    if (destination) destination.textContent = vm.navigation.taskTitle || "暂无导航任务";
    if (eta) eta.textContent = vm.navigation.etaLabel;
    if (minutes) minutes.textContent = vm.tasks.total ? `${vm.tasks.total} 项` : "--";
    if (kilometers) kilometers.textContent = vm.risk.level;
    if (minutes?.nextElementSibling) minutes.nextElementSibling.textContent = "任务";
    if (kilometers?.nextElementSibling) kilometers.nextElementSibling.textContent = "压力";
    if (progress) progress.style.width = `${Math.round(stageProgress * 100)}%`;
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
    if (Number.isFinite(progress) && typeof window.mapCarTo === "function") {
      try { window.mapCarTo(progress, 1150); } catch (_error) { /* visual controller stays optional */ }
    }
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
    document.documentElement.dataset.auriShell = "phase-2";
    const offline = new URLSearchParams(window.location.search).get("offline") === "1";
    if (!offline) client.start();
  }

  window.AURI_HMI_NEXT = {
    applyState(state) { return client.injectSnapshot(state, "fixture"); },
    connect() { return client.start(); },
    disconnect() { return client.stop(); },
    getState() {
      return {
        syncMode: client.getSyncMode(),
        config: { ...client.getConfig(), token: client.getConfig().token ? "***" : "" },
        worldState: client.getSnapshot(),
        viewModel,
        activeSection
      };
    },
    openPanel,
    closePanel
  };

  window.addEventListener("beforeunload", () => client.stop(), { once: true });
  if (document.readyState === "complete") applyShell();
  else window.addEventListener("load", () => requestAnimationFrame(applyShell), { once: true });
})();
