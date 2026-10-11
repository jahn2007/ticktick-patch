# 提醒失效排查

已收到的复现环境：Android 15、TickTick Play 版 8.2.2.0（8220）、LSPosed 2.2.1（7912）/ API 102。用户报告所有任务在前台、后台都无法提醒，通知栏也没有任务通知，停用模块后恢复。首轮对照结果：`none` 正常、`pass-through` 正常、`pro` 异常。第二轮：`pro-network` 异常、`pro-local` 异常。两组测试均复现，但日志提示跨测试状态残留，尚不能确定是单个方法、组内组合还是残留状态影响。团队、日期和额度修改不是这些测试中复现所必需的条件。已收到 LSPosed 日志，证据见下文；尚不能认定具体业务条件为根因。

用户进一步澄清：可正常提醒的是修改过的 APK；`lspilot_main.java` 是经 LSPilot 分析该 APK 后得到的脚本，脚本自身运行也无法正常提醒。因此不存在“相同脚本在 LSPilot 中正常、移植到 API 102 后异常”的有效对照。脚本注释中的“实测可用”不能作为提醒链路已验证的证据，也没有证据证明脚本还原了 APK 的全部修改。

已取得用户提供的 8001 修改版 APK，没有匹配的官方 8001 或正常工作的修改版 8220。直接读取 DEX 后发现：样例的提醒处理器 `qc.e.h()` 将前面的对象探测/状态不一致分支返回 false，调度方法在 false 时继续；样例 `User.getProTypeForFake()` 为 0，而生成脚本设为 1。原脚本遗漏了前者，也写错了后者。完整证据、指令偏移、SHA-256 和版本边界见 [APK 样例分析](APK_SAMPLE_ANALYSIS.md)。这证明脚本未准确复刻样例，但 8220 的相应检查还需候选的调用日志和真机提醒结果验证。

本地工作流已配置 GitHub Actions 构建时额外上传 `TickTickPatch-reminder-diagnostics-运行编号`，包含十八个 APK；新增候选尚未推送/编译。它们使用同一个 applicationId 和同一轮 CI 的 debug 签名，按顺序覆盖安装模块即可。不同运行的 debug 签名仍可能不同；签名冲突时只卸载旧模块，不卸载 TickTick、不清除其数据。

| APK 后缀 | 行为 | 用途 |
| --- | --- | --- |
| `none` | 模块加载，不查找宿主模型，不注册任何 Hook | 区分框架/模块加载影响与 Hook 影响 |
| `pass-through` | 注册原先的值和额度 Hook，每次调用只执行并返回原调用；不执行数据库观察 Hook | 区分拦截行为与返回值修改 |
| `pro` | 只修改 `isPro` / `getIsPro` / `getIsProN` / `getPro` | 测试最小会员判断 |
| `pro-network` | 只修改四个网络模型的会员判断 | 对 `pro` 做第一组二分 |
| `pro-local` | 只修改本地用户、工具、内核账号、JS 的四个会员判断 | 对 `pro` 做第二组二分 |
| `pro-reminder-safe` | 不修改网络模型；本地 Pro 判断在已识别的提醒调用栈中返回原值，其他调用仍返回 `true` | 根据实际日志编写的修复候选，尚待真机验证 |
| `pro-reminder-gate` | 与 `pro` 相同，额外令已验证形状的提醒检查入口 `h()` 返回 false | 优先验证脚本遗漏的提醒入口 |
| `apk-sample` | 只修改样例中确认的 18 个常量 getter，并加入提醒检查行为 | 测试依据 APK 写出的候选；不是 APK 全部改动的等价证明 |
| `pro-n1` 至 `pro-n4` | 分别只修改一个网络模型 getter，详见下表 | 隔离网络组内的单项影响 |
| `pro-l1` 至 `pro-l4` | 分别只修改一个本地模型 getter，详见下表 | 隔离本地组内的单项影响 |
| `no-team` | 保留其他状态、日期、额度 Hook，跳过所有名字含 `team` 的 getter；无数据库观察 Hook | 隔离团队状态改写 |
| `full` | 原先的完整 Hook，Debug 包含原先的数据库观察与审计 | 与旧版本对照 |

不要把多个 APK 当成多个可同时启用的模块；它们会互相替换。模块管理器的版本名会显示配置，例如 `1.0.1-pro-l1`。覆盖安装后检查模块作用域，并强制停止、重新打开 TickTick，确保旧进程中的 Hook 已清除。不要只依赖框架热重载。

新增候选编译后，下一轮优先测试 `pro-reminder-gate` 与 `apk-sample`，并检查 `jd.d.h()` 的原值/返回值；此前仅凭调用栈编写的 `pro-reminder-safe` 保留为备用实验。如果提醒检查无法匹配或两版仍异常，再根据新日志继续隔离。暂不必测试全部单方法包。单方法配置与原 `pro` 使用相同的拦截逻辑和调用日志，按目标类名和方法名同时筛选，每个配置只尝试安装下面的一项：

| APK 后缀 | 类（省略共同前缀 `com.ticktick.`） | 方法 |
| --- | --- | --- |
| `pro-n1` | `task.network.sync.entity.SignUserInfo` | `getIsProN()` |
| `pro-n2` | `task.network.sync.common.model.SignUserInfo` | `isPro()` |
| `pro-n3` | `task.network.sync.model.User7ProModel` | `isPro()` |
| `pro-n4` | `task.network.sync.payment.model.SubscriptionInfo` | `getIsPro()` |
| `pro-l1` | `task.data.User` | `isPro()` |
| `pro-l2` | `task.helper.pro.ProHelper` | `isPro()` |
| `pro-l3` | `kernel.account.impl.AccountManager` | `isPro()` |
| `pro-l4` | `task.javascript.CommonJavascriptObject$UserProfile` | `getPro()` |

这八项都是候选方法，缺失的方法会跳过，实际安装数量以宿主版本和日志为准。每项原方法成功返回后将结果设为 `true`。完整版还修改团队/订阅状态、日期、会员类型、数据库字段、额度和额外 JS 字段；`pro` 两组不修改这些内容。

用户提供的 `LSPosed_20261011_125341.zip` 中有以下直接证据（2026-10-11，本地时间）：

- 12:48:32 和 12:50:25，`pro-network` 把 `network.sync.common.model.SignUserInfo.isPro()` 的 `false` 改成 `true`；调用栈均经过 `jd.d.h(AlertServiceHandler.java:25)` 和 `TaskAlertJobService.c()`。提醒服务确实读取了被修改的网络模型，不是只用于会员界面显示。
- 12:38:27，`pro` 的同一网络 getter 经过 `TickTickAccountManager.saveUserStatus()`；12:49:36 的 `pro-local` 日志中，`User.isPro()` 已是 `original=true`。这支持跨测试状态残留的可能性，但日志不能证明具体持久化字段。
- `ProHelper.isPro()` 的无参重载不存在，`AccountManager` 类不存在；没有这两项有效 Hook。该日志中实际出现的 Pro 调用只有 `SignUserInfo.isPro()` 和 `User.isPro()`，其余未出现不能等同于永远不会调用。
- 没有 `[host-error]`。`full.log` 的 TickTick 系统日志主要覆盖 12:52 以后的透传测试，没有覆盖完整的异常测试到期窗口。其中存在一条 GAv4 接收器注册异常，但随后提醒通知发布且有振动，不能直接把它归为本次故障的根因。

`pro-reminder-safe` 仅作为实验候选：所有网络模型不注册值 Hook，避免会员结果进入账号状态保存路径；本地四个 Pro 候选只在非提醒路径改值。在执行原方法之前检查调用栈，识别 `TaskAlertJobService`（含内部类）或源文件名 `AlertServiceHandler.java`，提醒路径中只执行并返回原方法，嵌套 getter 也适用。每个方法首次走保护分支时记录 `[reminder-original]`。保护依据当前 8.2.2.0 的同步调用栈；不保证覆盖未来版本或未识别的异步提醒入口，不恢复之前已保存的账号状态。应先用 `none` 和新任务确认基线恢复，再测试该候选。此配置不包含完整版的日期、团队、会员类型和额度修改。

单项失败时优先查看该方法的调用栈和宿主系统日志；若八个单项都正常而组配置失败，应查组内组合状态和交互。单项没有 `[hook-call]` 时，不要把正常结果当作该方法无影响的证据：它可能不存在，或未在这轮测试中被调用。日志中的方法声明类也能帮助识别两个候选类是否继承了同一个实现。不要仅凭 `none` 和透传正常就断言框架在任何 Hook 子集上都没有问题。

每轮在重新打开宿主后，新建一个三分钟后提醒的普通任务，明确设置提醒时间，然后回到桌面等待。创建任务后不要再强制停止应用。记录亮屏/锁屏、提醒是否出现、是否有声音以及实际到期时刻。每个异常配置测试后都用 `none` 和新任务复测；如果 `none` 也异常，先停止对下一单项的归因，记录状态残留和同步情况，不清除宿主数据。

诊断配置（`none` 和原版 `full` 除外）会在每个 Hook 首次被实际调用时记录 `[hook-call]`：配置名、方法、原值、返回值和调用栈。若原方法抛出异常，记录 `[host-error]` 后原样抛出，绝不重复执行原方法或吞掉宿主异常。标签为 `TickTickPatch`，没有 Toast、通知或注入完成消息。`none` 没有模块调用日志是正常的；以安装版本名确认配置。

请同时导出 LSPosed 日志和从保存任务到提醒到期后一分钟的 Android 系统日志。系统日志比模块安装日志更能定位宿主提醒接收器/服务中的崩溃。

如果已有 ADB，可在宿主重新打开前启动录制：

```powershell
adb logcat -v threadtime > reminder-pro.log
```

测试完成后按 Ctrl+C 停止。将每份日志和配置名、提醒到期时刻一起对应。只读取主进程 PID 可能漏掉提醒相关的其他进程，第一次抓取不要做 PID 过滤。

判断结果：

- `none` 也失败：先检查模块作用域、是否存在其他模块共同作用、宿主重启是否生效；再比较框架对目标包的加载行为。
- `none` 正常、`pass-through` 失败：重点检查框架 Hook 兼容性和原方法调用异常，暂不归咎于会员返回值。
- `pass-through` 正常、`pro` 失败：从会员判断首次调用栈和宿主提醒异常继续定位。
- `pro` 正常、`no-team` 失败：继续对订阅、日期、数据库模型、额度和 JS 分层二分；团队状态并不是这个结果的唯一原因。
- `no-team` 正常、`full` 失败：团队 Hook 或原先的数据库观察 Hook 是下一轮隔离目标，尚不能仅凭这一组结果认定团队 Hook。

默认构建配置仍是 `full`。这些 APK 是用于收集对照证据的诊断版本，不代表已修复真机提醒问题。无需在本地安装 JDK、SDK 或 Gradle。
