(function initAuriVisualShell() {
  "use strict";

  const PANELS = {
    auri: {
      title: "AURI 已就绪",
      subtitle: "你只管开，我来处理",
      lead: "等待手机同步今天的任务",
      copy: "任务进入车辆后，AURI 会在同一座舱界面中接续路线、判断现实风险，并只在需要授权时请求一次确认。",
      rows: [
        ["◌", "手机任务", "等待语音创建", "待同步"],
        ["◇", "随行设备", "状态与触觉将在这里接续", "待连接"]
      ]
    },
    tasks: {
      title: "今日任务",
      subtitle: "来自手机与 Agent",
      lead: "目前没有已同步任务",
      copy: "任务数量、责任类型、时间和状态将在下一阶段直接读取 Agent World State。",
      rows: [
        ["＋", "任务入口", "请在手机端通过语音创建", "0 项"],
        ["◎", "路线准备", "收到含地点的任务后自动更新", "等待"]
      ]
    },
    messages: {
      title: "消息与执行",
      subtitle: "仅在 Agent 准备动作后显示",
      lead: "暂无待确认动作",
      copy: "驾驶中只显示联系人、动作状态和必要摘要；消息全文与订单明细留在手机端查看。",
      rows: [
        ["□", "消息草稿", "等待 Agent 生成", "0 条"],
        ["✓", "处理结果", "确认后从后端同步", "未开始"]
      ]
    },
    vehicle: {
      title: "座舱状态",
      subtitle: "车辆能力只读展示",
      lead: "驾驶环境保持稳定",
      copy: "空调、温度、模式和风量将在下一阶段读取 Agent 共享状态，车机不直接改写 World State。",
      rows: [
        ["°", "座舱温度", "当前演示环境", "19°C"],
        ["≋", "驾驶辅助", "道路与车辆动画运行中", "正常"]
      ]
    }
  };

  const POIS = [
    ["●", "出发 · 0km", "home"],
    ["↗", "进入主路 · 6km", ""],
    ["▦", "通勤路段 · 13km", ""],
    ["◉", "路线观察 · 28km", ""],
    ["◷", "时间窗口 · 36km", "warning"],
    ["⇄", "任务同步 · 44km", ""],
    ["A", "AURI 接管 · 50km", "processing"],
    ["✓", "方案准备 · 64km", "processing"],
    ["○", "等待确认 · 72km", "warning"],
    ["✓", "处理完成 · 77km", "success"]
  ];

  function shellRows(rows) {
    return rows.map(([icon, title, detail, state]) => `
      <div class="auri-shell-row">
        <span class="auri-shell-row-icon" aria-hidden="true">${icon}</span>
        <span class="auri-shell-row-copy"><strong>${title}</strong><span>${detail}</span></span>
        <span class="auri-shell-row-state">${state}</span>
      </div>
    `).join("");
  }

  function closePanel() {
    const panel = document.getElementById("left-panel");
    panel?.classList.remove("is-visible", "auri-shell-panel");
    document.querySelectorAll("[data-auri-section]").forEach((item) => {
      item.classList.toggle("active", item.dataset.auriSection === "navigation");
    });
  }

  function openPanel(section) {
    if (section === "navigation") {
      closePanel();
      return;
    }
    const config = PANELS[section];
    const panel = document.getElementById("left-panel");
    if (!config || !panel) return;

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
        <p class="auri-shell-lead">${config.lead}</p>
        <p class="auri-shell-copy">${config.copy}</p>
        <span class="auri-shell-status">AURI 正在待命</span>
        ${shellRows(config.rows)}
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

  function prepareTopBar() {
    const play = document.getElementById("playbtn");
    play?.removeAttribute("onclick");
    const microphones = [...document.querySelectorAll(".tb-mic")];
    const source = microphones[0];
    if (source) {
      source.removeAttribute("onclick");
      source.title = "手机语音将在这里同步";
      source.setAttribute("aria-label", "等待手机语音同步");
      source.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="7" y="2.5" width="10" height="19" rx="2"/><path d="M10 6h4M10 18h4"/></svg>';
    }
    const offline = document.getElementById("tb-offline");
    offline?.classList.add("show");
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
      if (["Space", "ArrowLeft", "ArrowRight"].includes(event.code)) {
        event.stopImmediatePropagation();
      }
      if (event.key === "Escape") closePanel();
    }, true);
  }

  function applyShell() {
    prepareTopBar();
    replaceCarBranding();
    replaceRoutePOIs();
    bindDock();
    disableLegacyDemoShortcuts();
    closePanel();
    document.documentElement.dataset.auriShell = "phase-1";
  }

  if (document.readyState === "complete") applyShell();
  else window.addEventListener("load", () => requestAnimationFrame(applyShell), { once: true });
})();
