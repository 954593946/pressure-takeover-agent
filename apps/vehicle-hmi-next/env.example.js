// ─────────────────────────────────────────────────────────────
// SafeDriver™ v2 demo · env template (NO REAL API KEY)
//
// Setup — one file, three steps:
//
//   1. cp env.example.js env.js
//   2. Edit env.js, replace the two YOUR_BOSCH_DASHSCOPE_KEY placeholders
//      with a real Bosch DashScope API key.
//   3. cd bosch_fatigue_monitor && python3 -m http.server 8000
//      open http://localhost:8000/bosch_unified_demo_v2.html
//
// `env.js` is gitignored, so the key you fill in never leaks back to git.
// Without env.js (or with placeholder keys) the demo still runs — UI /
// map / scene progression all work; only Qwen ASR + TTS voice calls are
// skipped, and the console logs "offline-demo mode".
//
// HTML loads this via:  <script src="env.js" onerror="...offline..."></script>
// ─────────────────────────────────────────────────────────────
window.SAFEDRIVER_CONFIG = {
  asrKey: 'YOUR_BOSCH_DASHSCOPE_KEY',
  ttsKey: 'YOUR_BOSCH_DASHSCOPE_KEY',
  asrUrl: 'https://aigc.bosch.com.cn/llmservice/api/v1/audio/transcriptions',
  // Bosch Realtime ASR 必须使用服务方明确提供的 wss:// 地址；不能由 asrUrl 推导。
  // 未配置或未通过真实 partial + final 探测时，应用保持批量 ASR，不显示伪实时字幕。
  asrRealtimeUrl: '',
  asrRealtimeModel: 'qwen3-asr-flash-realtime',
  ttsUrl: 'https://aigc.bosch.com.cn/llmservice/api/v1/audio/speech',
  // —— Agent 判别核心:Bosch 网关 chat/completions(gpt-5.4-nano)——
  llmKey: 'YOUR_BOSCH_DASHSCOPE_KEY',
  llmUrl: 'https://aigc.bosch.com.cn/llmservice/api/v1/chat/completions',
  llmModel: 'gpt-5.4-nano',
  // —— Local unified agent bridge. Start with:
  // python3 "Agent Implementation/bosch_agent_orchestrator.py"
  larkGatewayUrl: 'http://127.0.0.1:8787',
  // —— Local agent-gateway shared secret (NOT a Bosch key) ——
  // The loopback gateway requires this token for file:// (Origin: null) requests,
  // so an arbitrary website cannot drive the gateway. Generate one with:
  //   python3 -c "import secrets; print(secrets.token_hex(24))"
  // and keep the same value in env.js (gitignored). Leave empty only if you run
  // the gateway with SAFEDRIVER_ALLOW_NULL_ORIGIN=1 (less secure, dev only).
  agentToken: 'YOUR_LOCAL_AGENT_TOKEN',
  // —— Luckin MCP demo lookup location. Use coordinates near an open store.
  luckinLocation: { longitude: '121.8050', latitude: '31.1443' },

  // —— 飞书会议冲突调解：协调消息通过群自定义机器人 webhook 发送（后端从 env.js 读取，无需环境变量）。
  //    在飞书群「设置 → 群机器人 → 添加自定义机器人」获取 hook URL，安全设置用关键词 "SafeDriver"。——
  feishuBotWebhookUrl: 'https://open.feishu.cn/open-apis/bot/v2/hook/YOUR_HOOK_ID',
  // 若机器人安全设置选「加签」，填签名密钥(SEC 开头)；选「关键词」则留空、消息含 "SafeDriver" 即可
  feishuBotWebhookSecret: '',
  // —— 钉钉会议冲突调解 · 方式一（推荐）：REST。自建应用 AppKey/AppSecret 直调开放平台，无需登录/扫码/CLI。——
  //    在 open-dev.dingtalk.com 你的组织里「创建应用(企业内部)」→ 拿 AppKey/AppSecret →
  //    「权限管理」开通 日历(个人日程读/写) + Contact.User.Read → 发布。
  //    dingtalkUnionId = 要读谁的日历(你自己的 unionId)。AppSecret 属机密，只放这个 gitignored 文件。
  dingtalkAppKey: 'YOUR_DINGTALK_APP_KEY',
  dingtalkAppSecret: 'YOUR_DINGTALK_APP_SECRET',
  // 可选:直接给一个 access token(约 2h 过期)。有 AppKey/AppSecret 时留空,适配器自动换取更省心。
  dingtalkAccessToken: '',
  dingtalkUnionId: 'YOUR_DINGTALK_UNION_ID',
  // 钉钉群自定义机器人 webhook：改了钉钉侧的会议后，协调消息发到这个群(如 democracy 群)。
  // 群设置→机器人→自定义，安全设置选「自定义关键词」填 SafeDriver，复制 webhook 地址。
  dingtalkBotWebhookUrl: 'https://oapi.dingtalk.com/robot/send?access_token=YOUR_DINGTALK_ROBOT_TOKEN',
  dingtalkBotWebhookKeyword: 'SafeDriver',
  // —— 钉钉 · 方式二（备选）：官方 dingtalk-workspace-cli（命令 dws）——
  //    npm install -g dingtalk-workspace-cli；dws auth login --device（扫码授权，凭证 dws 加密存本机）。
  //    如需指定非默认 dws 路径填 dingtalkDwsCli；用 REST 方式则本行留空。
  dingtalkDwsCli: '',
};
