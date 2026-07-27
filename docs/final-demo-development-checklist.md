# AURI 最终 Demo 待开发功能与分工清单

> 版本：2026-07-27
>
> 核对基线：main 分支 c0d6719（已包含 PR #17）
>
> 用途：项目负责人排期、各模块 Owner 开发，以及团队成员直接交给 AI 编程助手执行。
>
> 目标：稳定跑通一条可解释、可确认、可恢复的多端责任接管闭环。

## 1. 统一完成标准

优先级：

- **P0 阻塞项**：不完成就不能进入正式 Demo。
- **P1 增强项**：主链路稳定后再做，提升说服力或容错能力。
- **P2 后续项**：不影响本轮 Demo，不能挤占 P0 时间。

每个 P0 功能只有同时满足以下条件才算完成：

1. 从真实 API 和 World State 进入，不靠页面内硬编码跳转。
2. 手机、车机、控制台使用同一个 session_id，只接受单调递增的 revision。
3. 刷新、断线、重试或重复点击后，不产生第二份任务、动作或订单。
4. 有可重复的测试步骤、正常结果、失败结果和现场降级方式。
5. PR 写明修改范围、接口影响和验证证据，并由至少一个上下游 Owner Review。
6. 不提交 API Key、团队令牌、真实联系人、地址、支付信息或设备凭据。

## 2. 最终 Demo 唯一主链路

所有 Owner 必须围绕同一条纵向链路开发：

手机创建任务 → Agent 生成 Task 和责任属性 → 控制台注入会议延迟 → L1 提醒和腕上双短震 → 进入车辆并切换主交互端 → 拥堵导致 ETA 18:28 和 L2 → 用户自然语言求助 → Agent 准备消息与模拟采购方案 → 车机一次确认 → 模拟执行并写入 Ledger → 三端同步 → cooldown → 停车后手机复盘。

建议现场使用：手机、车机 HMI、真实腕表、Demo 控制台，以及一个投屏讲解画面。主故事控制在 4 分 20 秒内，技术证明控制在 60 秒内。

## 3. 四个必须最先解决的功能阻塞问题

### BLOCK-01：统一唯一共享后端

- **Owner**：Agent / 后端；项目负责人做最终选择。
- **优先级**：P0，第一顺位。
- **当前情况**：仓库同时保留 auri-agent-api 和 auri-langchain-agent-api 两个 Render 配置；README 推荐地址、手机默认地址、车机公开地址并不一致。两个进程内存中的 World State 不会共享。
- **要做**：
  - 选择一个正式 Demo 后端作为唯一 canonical URL。
  - 手机、车机、控制台、README、脚本和 Render 配置全部使用该地址。
  - 另一个实例标记为“备份，不参与同场联调”，不能让成员自由选择。
  - health 增加构建 SHA、服务名和启动时间，现场能确认连接的是同一版本。
- **验收**：手机创建任务后，控制台和车机在 1 秒内显示相同 session_id、更高 revision 和同一任务；三个端显示的服务版本一致。
- **禁止**：复制状态、手工刷新 JSON，或让两个服务各演各的。

### BLOCK-02：手机任务真正写入共享 World State

- **Owner**：手机开发 A、手机开发 B；Agent Review。
- **优先级**：P0。
- **当前情况**：快速创建先写 LocalTaskStore，再用空 sessionId 异步请求后端，异常还会被吞掉。日历合并本地和后端任务时 ID 不同，可能出现“手机看见、车机和 Agent 不知道”或重复任务。
- **要做**：
  - 后端 World State 成为 Demo 任务的唯一权威来源。
  - 提交前从 Repository 获取当前 session_id，不发送空 Session。
  - 创建按钮进入“提交中”，后端接受后才显示“已同步”。
  - 如保留乐观 UI，必须区分 syncing、failed、synced，并用后端 ID 替换临时 ID。
  - 失败时提供重试；重试复用同一个 event_id。
  - 日历不得把一个业务任务显示为本地和远端两条。
- **验收**：断网创建不能显示成功；恢复后重试只创建一次；车机和控制台能看到同一任务；重启手机后任务仍来自后端。

### BLOCK-03：手机不得自动重置共享 Session

- **Owner**：手机开发 B。
- **优先级**：P0。
- **当前情况**：ChatViewModel 首次收到非空闲状态时会调用 session reset。手机晚加入或重连可能清空控制台和车机正在演示的状态。
- **要做**：
  - 删除启动自动 reset 逻辑。
  - 重置只允许 Demo 控制台显式执行，并增加二次确认。
  - 手机首次连接直接恢复当前 World State，显示“已加入当前演示”。
  - Session 变化时清理端侧旧缓存并重新拉快照，不创建新 Session。
- **验收**：控制台推进到 waiting_confirmation 后再启动手机，后端 stage、revision 和 confirmation 不变化；手机正确进入 Companion 只读态。

### BLOCK-04：腕表接入真实跨端状态

- **Owner**：手机开发 B + 腕上硬件。
- **优先级**：P0。
- **当前情况**：腕表 UI、触觉、ACK、传感器读取已有基础，真机震动也已验证；但 Side Service 启动后仍按本地 MOCK_COMMANDS 轮播，没有把手机收到的 World State 转成真实 SET_STATE。
- **要做**：
  - 手机收到新 revision 后，根据 worldState.wearable 生成唯一 command_id。
  - 通过手机/Zepp 通信链路发送 SET_STATE，腕表回传 ACK。
  - 手机记录 command_id、发送时间、ACK 结果和耗时。
  - 超时可以有限重试，但同一 command_id 不能重复震动。
  - Side Service 移除自动 Mock 轮播；Mock 只保留在显式 Debug 模式。
- **验收**：控制台注入会议延迟后，真表显示 warning 并只双短震一次；重复命令返回 duplicate 且不再震动；断连不阻塞手机和车机主链路。

## 4. Agent / 后端待开发

### AGENT-P0-01：Chat 使用统一 Runtime 写入路径

- **已有基础**：LangChain 受控工具和 Chat SSE 已存在。
- **问题**：Chat 层直接访问 Runtime 私有状态、锁、事件集合和广播方法，绕过公开事件提交路径；并发时可能覆盖状态，甚至提交失败后仍回复成功。
- **开发**：
  - 在 AgentRuntime 增加公开的自然语言提交方法。
  - 所有自然语言输入使用同一 Session 校验、revision 冲突检测、事件幂等和广播逻辑。
  - Chat 层只做 SSE 格式转换，不直接写 World State。
  - 客户端传 client_event_id，网络重试必须复用。
- **验收**：同时发送聊天和控制台事件不会丢 revision；相同 client_event_id 只执行一次工具；提交失败时不能回复“已完成”。

### AGENT-P0-02：统一确认接口和错误语义

- **已有基础**：标准 confirm 有 owner、过期和幂等校验。
- **问题**：Chat confirm 当前把所有异常转成 HTTP 200 和 accepted=false。
- **开发**：
  - 手机聊天确认复用标准 Runtime confirm。
  - 保留 400、404、409、401 和 WRONG_SURFACE、EXPIRED、NOT_FOUND 等错误码。
  - 确认请求校验当前 Session。
  - 按钮和语音共用同一个 confirmation_id。
- **验收**：车机拥有确认权时，手机确认返回 WRONG_SURFACE；相同确认连续提交 10 次只执行一次。

### AGENT-P0-03：冻结 Chat SSE 契约

- **问题**：contracts/openapi.yaml 尚未正式描述 Chat 接口。
- **开发**：
  - 定义请求字段、事件类型、命名、结束条件、错误事件和重连策略。
  - 冻结 text_delta、tool_call、tool_result、confirmation_required、done、error。
  - 明确 Chat 流负责对话过程，World State 流才是业务状态真相。
  - 更新 OpenAPI、示例和手机接入说明。
- **验收**：手机不再猜字段；示例通过 Schema 测试；Agent 与手机 B 共同 Review。

### AGENT-P0-04：回复智能但不越权

- **开发**：
  - 提示词明确“理解任务—读取状态—选择工具—解释结果—克制回应”。
  - 回复引用真实任务、时间、迟到分钟和已准备动作，不能固定套话。
  - 紧张场景可有一句温和回应，例如“接孩子这件事我会优先保护，你先安全驾驶”，但不能持续陪聊。
  - L0-L3、金额、权限、主交互端和最终执行仍由确定性代码决定。
  - 没有工具结果时不得声称已发消息或已下单。
- **验收**：10 种自然语言表达的回复有输入差异，Task、Risk、Action 决策保持稳定。

### AGENT-P0-05：可靠的 LLM 降级

- **当前风险**：部署检查曾出现 llm_last_mode=fallback；health 为 200 不能证明模型已真实连通。
- **开发**：
  - health 区分“配置存在”“最近调用成功”“最近 fallback 原因”。
  - LLM 超时不能卡住主线；固定 Demo 语句可由确定性 parser 完成。
  - 对上游 401、429、5xx、超时分别记录安全错误码。
  - 增加真实 smoke test，输入不同指令验证动态差异。
- **验收**：正常时两条不同请求产生不同回复；关闭 LLM 后 happy path 仍能完成，并明确显示降级。

### AGENT-P0-06：补齐自动化测试

至少新增：

- Chat 鉴权、空消息、Session mismatch、SSE done。
- Chat 重试幂等、并发 revision 冲突、提交失败不报告成功。
- Chat confirmation 正确 owner、错误 owner、过期、重复。
- over-budget、out-of-stock 不越权执行。
- Event API 与 Chat API 均产生合法 World State。

**验收**：CI 一条命令运行全部测试；主线和异常测试全部通过。

### AGENT-P0-07：Demo 稳定运行和恢复

- **当前情况**：World State 为进程内存，适合单实例 Demo，但重启会清空。
- **开发**：
  - 正式 Demo 固定一个实例，禁止横向扩容。
  - 二选一：增加轻量状态快照；或冻结“不重启、不部署、演示前 reset”的运行手册。时间紧时优先后者。
  - reset 返回标准初始状态，并清空对应幂等 Ledger。
  - health 输出构建版本和启动时间。
- **验收**：完整脚本连续 10 次至少 9 次成功；服务重启时控制台能识别并在 30 秒内按手册恢复。

### AGENT-P1-01：技术证明和可观察性

- 输出不含敏感信息的最近事件类型、工具名、reason_codes、revision、耗时、fallback 模式和 Ledger 摘要。
- 60 秒内能证明：LLM 负责理解和选工具，确定性后端负责风险、权限和执行。

### AGENT-P2-01：本轮不做

多用户账号、长期记忆、真实短信/微信、真实电商支付、真实车辆控制、开放式陪聊和医学压力判断都不进入冲刺。

## 5. 手机开发 A：业务 UI

### MOBILE-A-P0-01：任务创建和日历单一数据源

- 完成 BLOCK-02。
- 创建结果显示责任类型、时间、状态和同步状态。
- 如果后端尚不支持删除事件，P0 先隐藏删除入口，不能只删本地。
- **验收**：创建“18:10 接孩子，之后去超市”后，手机显示两个结构化任务，车机和控制台字段一致。

### MOBILE-A-P0-02：责任风险卡

- 只按 risk.pressure_level、late_minutes 和 reason_codes 渲染，不在端侧重算 L0-L3。
- L1 表达时间窗口压缩；L2 表达刚性责任预计失约和可接管方案；L3 只表达高负荷保护。
- 状态同时使用文字和图标，不能只靠颜色。
- **验收**：同一快照在手机和车机结论一致；刷新后不跳回 L0。

### MOBILE-A-P0-03：驾驶 Companion 只读态

- primary_surface 为 vehicle_hmi 时，手机隐藏或禁用确认、复杂编辑和 TTS，只显示“请在车机确认”。
- 旧手机确认不能提交成功。
- **验收**：主端切换后 1 秒内手机只读；系统返回或旧页面不能绕过 owner 校验。

### MOBILE-A-P0-04：服务方案和模拟边界

- 手机展示完整消息草稿、商品、金额、配送、预算和替代规则。
- 所有消息和订单显著标注“模拟”或“Demo 数据”。
- 驾驶中只展示摘要；完整明细停车后再看。
- **验收**：效率型和品质型在商品、总价或配送上有稳定差异；超预算时无可执行确认。

### MOBILE-A-P0-05：Profile 真正可修改

- **当前情况**：Profile 页面主要只读取预设值。
- 增加效率型、品质型两个稳定预设，调用 PUT profile。
- 修改后显示已同步；失败不能只改本地文案。
- Profile 影响语气、预算、配送和替代策略，但不影响安全权限。
- **验收**：两个 Profile 运行同一场景，方案和话术有差异，确认 owner 不变。

### MOBILE-A-P0-06：停车后复盘

- parked_review 时展示原始任务、风险原因、任务调整、消息结果、订单结果、错误/降级和 Ledger 时间线。
- 不展示内部 prompt、API Key、令牌或难以理解的原始 JSON。
- **验收**：每条复盘都对应后端真实 Task、Action、ServiceOrder 或 Ledger。

### MOBILE-A-P0-07：错误、空态和重试

- 覆盖未连接、401、Session 变化、提交超时、LLM fallback、无确认权、订单阻断和无复盘数据。
- 文案告诉用户下一步，不显示异常堆栈。
- **验收**：断网、错 Token、超预算都不崩溃、不假成功。

### MOBILE-A-P1-01：视觉收口

- 统一 AURI 名称、深蓝/象牙白/品牌金 Token，一屏一个主 CTA。
- 移除开发占位和重复入口；目标真机投屏后字号仍清楚。

## 6. 手机开发 B：网络、语音和设备能力

### MOBILE-B-P0-01：统一 API 地址

- 完成 BLOCK-01 的移动端部分。
- Debug、Release 必须连接项目负责人公布的同一个 canonical Base URL。
- 地址缺失或错误时，启动页给出明确提示，不能静默切到另一个 Render 实例。

### MOBILE-B-P0-02：Repository 的 Session 和 revision

- 移除自动 reset；初次连接先取快照，再连 SSE。
- 相同 Session 只接受更高 revision；新 Session 重新取快照。
- Event 统一由 Repository 填 Session、时间和稳定 event ID，ViewModel 不再发送空 Session。
- SSE 指数退避重连，并用快照对账。
- **验收**：断网 15 秒再恢复，手机追上最新状态，无重复任务和重复聊天卡。

### MOBILE-B-P0-03：Chat SSE 错误和取消

- 与 AGENT-P0-03 对齐。
- 同时只允许一个活动请求；取消旧请求后，旧回复不能覆盖新回复。
- HTTP 非 2xx、SSE error、流中断和缺少 done 都要有真实状态。
- Chat 文本可渐进展示，业务卡片只认 World State。
- **验收**：快速连续发送两条话不会串回复；401、超时和中断可恢复。

### MOBILE-B-P0-04：ASR 真机验收和文本兜底

- 处理权限、无语音、部分结果、最终结果、主动停止和服务不可用。
- 固定 Demo 语句 10 次至少成功 9 次；提交前能看到识别文本。
- ASR 失败时文本输入始终可用。

### MOBILE-B-P0-05：TTS 输出预算和去重

- 只有主交互端播报；车机为主时手机 TTS 停止。
- 同一 revision 和 conclusion 只播一次；重连和旋转屏幕不重复。
- 驾驶阶段最多一条结论，不朗读商品明细或内部工具名。

### MOBILE-B-P0-06：腕表网关

- 完成 BLOCK-04：World State → SET_STATE → ACK → 日志。
- 用 session_id、revision、mode 生成稳定 command ID。
- 处理 watch unavailable、ACK timeout、duplicate、unsupported、error 和 PONG 超时。
- **验收**：手机调试页显示腕表在线状态、最后 ACK、耗时和降级状态。

### MOBILE-B-P0-07：日志和隐私

- 记录最近连接、事件、Chat、腕表 ACK 和错误码。
- 默认隐藏完整自然语言、联系人和地址。
- CrashHandler 和日志导出不得包含 Token、API Key 或认证 Header。
- 提供一键复制诊断摘要。

### MOBILE-B-P0-08：可复现 APK

- 在干净环境完成 Debug/Release 构建，记录 JDK、Gradle、SDK。
- APK 显示版本号和 commit SHA，提前安装到主机和备用手机。
- **验收**：另一位成员按 README 可从零构建；主设备冷启动、授权、联网和投屏通过。

### MOBILE-B-P1-01：离线演示模式

- 只在明显显示“离线 Demo”时使用固定 fixture，不能与在线状态混用。
- 切换模式后清理旧 Session 和缓存，防止误以为仍在实时调用 Agent。

## 7. 腕上硬件

### WATCH-P0-01：真实消息链路

- 完成 BLOCK-04，移除 Side Service 自动 Mock 轮播。
- 固定方向：后端 World State → 手机网关 → Side Service → 腕表；ACK 反向返回。
- **验收**：不按腕表 Debug 按钮，只操作控制台就能进入 warning、handover、processing、completed。

### WATCH-P0-02：状态和触觉冻结

- idle 无震动；warning 双短震；handover 单短震；processing 三拍；completed 柔和短震；error 有限组合后停止。
- 只在新 command_id 首次生效时震动。
- 腕表最多两行核心文字，不显示确认、商品列表或长对话。

### WATCH-P0-03：ACK、去重和重试

- ACK 包含 command_id、result、reason、timestamp。
- 重复 ID 返回 duplicate；unsupported 和 error 能在手机看到。
- 手机只能有限重试，腕表保留最近已处理 ID。
- **验收**：相同命令发 10 次只震动一次。

### WATCH-P0-04：断连兜底

- 约 45 秒无消息时显示“请看手机 / 连接已中断”，不连续震动。
- 重连后只接受最新 revision，不补播过期状态。
- 腕表故障不能阻断车机确认和 Agent 执行。

### WATCH-P0-05：真机冻结和现场准备

- 最终包提前 24 小时安装，关闭自动更新。
- 检查电量、亮屏、勿扰、蓝牙、演示账号、充电器和备用设备。
- 记录型号、Zepp OS、构建 SHA 和安装步骤。

### WATCH-P1-01：传感器只是辅助证据

- 心率和睡眠只作为可选 signal，带来源、时间和 confidence。
- 不能由单次心率直接决定 L2/L3，不能显示情绪诊断。
- 传感器不可用时主流程不受影响。

### WATCH-P2-01：止损路线

- 若 Zepp 通信在冻结日前仍不稳定，公开切 ESP32-S3 或本地状态原型；不能把本地轮播说成实时联动。

## 8. 车机 HMI

### HMI-P0-01：统一后端和连接预检

- 默认连接 canonical URL，Token 由现场配置输入。
- 启动显示 health、连接、session、revision 和重连状态。
- 401、断网、服务休眠和 Schema 不兼容分别提示。

### HMI-P0-02：完全按 World State 渲染

- 只读取 stage、primary_surface、risk、tasks、actions、confirmation 和 output。
- 当前 STAGE_VIEW 中硬编码的 ETA、消息、动作结果和导航数据改为真实字段，或明确标注视觉占位。
- 不在 HMI 推演下一 stage，不自行创建 confirmation。
- **验收**：相同快照渲染稳定；切换 Session 后不保留上一轮草稿。

### HMI-P0-03：唯一主交互端

- 仅当 primary_surface 和 confirmation owner 都为 vehicle_hmi，且 status 为 pending 时显示确认。
- 一屏最多一条结论、一个动作组、一个确认入口。
- 手机为主时车机只读；腕表永远无确认入口。

### HMI-P0-04：确认幂等和按钮状态

- 点击后立即进入提交中并禁用。
- 处理 accepted、rejected、expired、wrong surface、not found 和网络未知结果。
- 网络结果未知时先拉 World State，不直接再确认。
- **验收**：按钮双击和语音并发只执行一次。

### HMI-P0-05：实时同步和重连

- 使用带 Header 的 streaming fetch；断线重连并用 state 快照对账。
- 相同 Session 只接受更高 revision；新 Session 清空旧 UI。
- SSE 不可用时轮询降级，并显示“轮询模式”。

### HMI-P0-06：Demo 控件与正式 HMI 分离

- 正式展示时隐藏 HMI 内的事件控制按钮，现场只由独立 Demo 控制台推进外部事件。
- HMI 不能同时扮演用户界面和导演台。

### HMI-P0-07：目标屏幕适配

- 按实际分辨率验证全屏、缩放、字体、点击目标和 kiosk 模式。
- 结论 32 至 40px，正文不低于 18px，主按钮至少 56px。
- 去掉长列表和无关导航装饰。

### HMI-P1-01：浏览器回归

- 覆盖快照渲染、主端切换、pending confirm、401、SSE 中断、重复 revision 和 Session 切换。

## 9. Demo 控制台

### CONSOLE-P0-01：现场预检

- 一键检查 health、鉴权、canonical URL、build SHA、Session、revision、LLM 最近模式和 SSE。
- 失败时显示具体处理方式。
- 演示前 10 分钟用它唤醒 Render。

### CONSOLE-P0-02：引导式 happy path

- 按顺序显示“下一步”，避免操作者在许多按钮中寻找。
- 每一步仍发送真实标准 Event。
- 成功后高亮下一步；失败时保留当前步，重试复用 event ID。

### CONSOLE-P0-03：事件前置条件和防误操作

- 无任务时不能注入后续拥堵；无 pending confirmation 时不能确认。
- stage 不匹配时禁用按钮并解释原因。
- Reset 二次确认，并说明会影响所有成员。
- 主故事开始后锁定 Profile、mock mode 和 Session。

### CONSOLE-P0-04：可解释状态和日志

- 展示 tasks、reason_codes、primary_surface、actions、confirmation、service order 和 Ledger 摘要。
- 每次操作记录 event ID、HTTP 状态、duplicate、revision 和耗时。
- 日志复制时自动脱敏。

### CONSOLE-P0-05：异常区和主线分离

- 缺货、超预算、断流和重复确认放入“技术验证”折叠区。
- 故障测试后能一键回到标准初始状态。
- 技术证明只选一至两个场景，避免拖长主 Demo。

### CONSOLE-P0-06：导演模式

- 显示当前讲解阶段、下一句主持提示和预计时间。
- 支持简洁导演视图与详细日志切换，投屏时不显示 Token。
- 导演模式仍只调用正式 API，不能直接改 World State。

### CONSOLE-P1-01：回放与导出

- 导出本轮事件、revision、关键 World State 和耗时。
- Fixture 回放必须明确标注“回放”，不能冒充实时 LLM 调用。

## 10. Contracts、测试夹具和文档

### CONTRACT-P0-01：冻结实际接口

- Agent 主责，手机 B、车机、腕表各派一人 Review。
- 对齐 OpenAPI、Pydantic、Kotlin model、Web JS 和腕表协议。
- 新增 Chat SSE、Chat confirm 和部署版本字段。
- 冻结后字段变更必须同步 Schema、示例、fixture 和消费者说明。

### CONTRACT-P0-02：正常和异常夹具

- 保留完整 happy path。
- 新增 wrong surface、duplicate event、duplicate confirmation、over budget、out of stock、new session、offline/reconnect。
- 每个夹具写明输入、预期 stage、revision、主交互端和可确认动作。

### CONTRACT-P0-03：文档与代码同步

- 各模块 README 增加启动、配置、依赖接口、已完成、待完成、测试和降级。
- 每次功能完成时更新本清单状态，避免旧文档继续误导 AI。
- AI 开工只按最新清单和真实 contracts，不从旧页面猜接口。

## 11. 跨端联调、测试和发布

### INTEGRATION-P0-01：同一个 World State

- 参与：Agent、手机 B、车机。
- 手机创建任务；控制台和 HMI 同步相同 Session、revision 和 tasks。
- 暂时不接语音和腕表，先证明共享状态闭环。

### INTEGRATION-P0-02：主交互端交接

- 参与：手机 A/B、车机、Agent。
- 会议延迟时手机可交互；进入车辆后手机只读、车机成为唯一确认 owner。
- 验证旧手机按钮、浏览器后退和重复页面都不能确认。

### INTEGRATION-P0-03：真实腕表

- 参与：手机 B、腕上硬件。
- 验证核心状态、单次触觉、ACK、duplicate 和断连兜底。
- 腕表失败时主链路继续，控制台显示降级。

### INTEGRATION-P0-04：自然语言和生活服务

- 参与：Agent、手机 A/B、车机。
- 用至少三种表达创建任务和求助，Agent 调用正确工具。
- 消息和订单先准备，车机确认后才模拟完成并写 Ledger。

### INTEGRATION-P0-05：自动化验收矩阵

| 场景 | 预期 |
|---|---|
| 相同 Event 重试 | duplicate 为 true，revision 不增加，不重复建任务 |
| 按钮双击加语音确认 | 只执行一次，只生成一个订单结果 |
| 车机主控时手机确认 | 409 WRONG_SURFACE |
| 超预算 | 订单 blocked，不生成可执行确认 |
| 缺货 | 展示降级或替代，不虚构成功 |
| SSE 断线重连 | 拉最新快照，不回放过期震动或播报 |
| 手机晚启动 | 加入当前 Session，不自动 reset |
| LLM 不可用 | 明确 fallback，固定主场景仍可完成 |
| 腕表断连 | 主流程继续，腕表静默提示离线 |
| Render 重启 | 控制台识别并按手册恢复 |

### RELEASE-P0-01：最终版本冻结

- 创建 Demo release 分支或 tag，记录后端、APK、HMI、控制台、腕表包和 contracts 版本。
- 展示前 24 小时停止自动部署和非阻塞修改。
- Render 只运行一个正式实例；提前唤醒并保持健康。

### RELEASE-P0-02：完整彩排

- 标准脚本连续 10 次至少 9 次无需改代码或手工改状态。
- 场地至少做 3 次全设备彩排，覆盖网络、投屏、音量、腕表镜头和讲解节奏。
- 重复失败两次的问题必须修复或明确降级。

### RELEASE-P0-03：现场角色和备份

- 固定 1 名讲解、1 名手机用户、1 名控制台导演、1 名技术排障。
- 准备录屏、离线 fixture、备用手机、充电器、热点、静态架构图和 API 截图。
- 降级时诚实标注模拟、回放和真实部分；核心风险、权限和状态闭环不能伪造。

## 12. 推荐执行顺序

### 第一批：先消除架构阻塞

1. BLOCK-01 统一后端。
2. AGENT-P0-01/02/03 收口 Chat、确认和契约。
3. BLOCK-02/03 修复手机任务和自动 Reset。

### 第二批：形成三端最小闭环

1. 手机真实创建任务。
2. 控制台按标准事件推进。
3. 车机按 World State 渲染并一次确认。
4. Agent 执行后广播并写 Ledger。

### 第三批：接入真实设备和完整交互

1. 腕表真实链路与 ACK。
2. 手机 ASR/TTS、Profile 和复盘。
3. HMI 屏幕适配和控制台导演模式。

### 第四批：异常、回归和冻结

1. 自动化异常矩阵。
2. 10 次完整回归。
3. 发布冻结、现场彩排和备份。

关键依赖：

- canonical URL 和 Chat 契约冻结前，各端不能自建私有协议。
- 腕表真实链路依赖手机 B 的网关，不依赖 HMI。
- Profile 和订单 UI 可先用共享 fixture 并行，但最终必须接 Agent 字段。
- 视觉、动画和 P1 功能不能阻塞 Session、幂等、确认 owner 和跨端同步。

## 13. 可直接交给 AI 的任务模板

> 你正在开发 AURI 多端 Agent 项目。开始前完整阅读 AGENTS.md、README.md、docs/final-demo-development-checklist.md、负责模块的 README/TEAM_GUIDE，以及 contracts 下的实际接口。
>
> 本次只实现任务 ID：填写任务 ID。Owner：填写 Owner。目标与验收直接复制该任务条目。
>
> 约束：后端是 World State 唯一写入者；客户端只提交 Event、Confirm 或 Profile；不让 LLM 决定 L0-L3、权限、金额、主交互端或最终执行；不提交 API Key、团队令牌或真实个人数据；不顺手修改其他模块；接口不足时先提 contracts 变更；必须提供测试、启动说明和失败降级。
>
> 交付时列出修改文件、接口影响、测试命令、测试结果、未解决问题和需要谁 Review。

## 14. 项目负责人每日检查

- [ ] 所有人连接同一个 canonical 后端。
- [ ] Render health、真实 LLM 调用、Session 和 SSE 正常。
- [ ] 每个 P0 PR 有上下游 Review 和验收证据。
- [ ] 没有人在客户端创建第二套 stage、risk 或 confirmation 逻辑。
- [ ] 手机启动不会 reset；手机任务能被 HMI 读取。
- [ ] 车机是驾驶中唯一确认端。
- [ ] 腕表状态来自真实链路，不是自动 Mock 轮播。
- [ ] 故障和 fallback 被真实标注，没有假成功。
- [ ] 每天至少跑一遍完整 happy path，并记录失败点。

最终完成的判断不是“每个人的页面都能打开”，而是：**同一个用户任务经过 Agent 的自然语言理解、确定性风险和权限控制，在正确设备上只确认一次，随后三端同步到同一个可复盘结果。**
