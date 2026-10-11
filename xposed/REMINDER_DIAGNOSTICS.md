# 提醒失效排查

已收到的复现环境：Android 15、TickTick Play 版 8.2.2.0（8220）、LSPosed 2.2.1（7912）/ API 102。用户报告所有任务在前台、后台都无法提醒，停用模块后恢复。首轮对照结果：`none` 正常、`pass-through` 正常、`pro` 异常。故障已在基础会员判断这一组内复现；团队、日期和额度修改不是复现所必需的条件。尚无宿主日志，不能认定某个具体方法为根因。

GitHub Actions 每次构建额外上传 `TickTickPatch-reminder-diagnostics-运行编号`，包含七个 APK。它们使用同一个 applicationId 和同一轮 CI 的 debug 签名，按顺序覆盖安装模块即可。不同运行的 debug 签名仍可能不同；签名冲突时只卸载旧模块，不卸载 TickTick、不清除其数据。

| APK 后缀 | 行为 | 用途 |
| --- | --- | --- |
| `none` | 模块加载，不查找宿主模型，不注册任何 Hook | 区分框架/模块加载影响与 Hook 影响 |
| `pass-through` | 注册原先的值和额度 Hook，每次调用只执行并返回原调用；不执行数据库观察 Hook | 区分拦截行为与返回值修改 |
| `pro` | 只修改 `isPro` / `getIsPro` / `getIsProN` / `getPro` | 测试最小会员判断 |
| `pro-network` | 只修改四个网络模型的会员判断 | 对 `pro` 做第一组二分 |
| `pro-local` | 只修改本地用户、工具、内核账号、JS 的四个会员判断 | 对 `pro` 做第二组二分 |
| `no-team` | 保留其他状态、日期、额度 Hook，跳过所有名字含 `team` 的 getter；无数据库观察 Hook | 隔离团队状态改写 |
| `full` | 原先的完整 Hook，Debug 包含原先的数据库观察与审计 | 与旧版本对照 |

建议先测试 `none` → `pass-through` → `pro` → `no-team` → `full`。不要把多个 APK 当成多个可同时启用的模块；它们会互相替换。模块管理器的版本名会显示配置，例如 `1.0.1-pro`。覆盖安装后检查模块作用域，并强制停止、重新打开 TickTick，确保旧进程中的 Hook 已清除。不要只依赖框架热重载。

目前首轮三个配置已经有结果，下一轮优先测试 `pro-network` 与 `pro-local`。两组与原 `pro` 使用相同的拦截逻辑和调用日志，唯一变化是各自只安装其中四项：

| 分组 | 类（省略共同前缀 `com.ticktick.`） | 方法 |
| --- | --- | --- |
| network | `task.network.sync.entity.SignUserInfo` | `getIsProN()` |
| network | `task.network.sync.common.model.SignUserInfo` | `isPro()` |
| network | `task.network.sync.model.User7ProModel` | `isPro()` |
| network | `task.network.sync.payment.model.SubscriptionInfo` | `getIsPro()` |
| local | `task.data.User` | `isPro()` |
| local | `task.helper.pro.ProHelper` | `isPro()` |
| local | `kernel.account.impl.AccountManager` | `isPro()` |
| local | `task.javascript.CommonJavascriptObject$UserProfile` | `getPro()` |

这八项都是候选方法，缺失的方法会跳过，实际安装数量以宿主版本和日志为准。每项原方法成功返回后将结果设为 `true`。完整版还修改团队/订阅状态、日期、会员类型、数据库字段、额度和额外 JS 字段；`pro` 两组不修改这些内容。

若只有一组失败，下一轮继续拆该组；若两组单独正常而 `pro` 仍失败，应查组合状态和交互。若两组都失败，可能有多个独立入口。不要仅凭 `none` 和透传正常就断言框架在任何 Hook 子集上都没有问题。

每轮在重新打开宿主后，新建一个三分钟后提醒的普通任务，明确设置提醒时间，然后回到桌面等待。创建任务后不要再强制停止应用。记录亮屏/锁屏、提醒是否出现、是否有声音以及实际到期时刻。复测异常配置后再用 `none` 复测，排除任务变化和同步状态造成的干扰。

`pro`、`pro-network`、`pro-local`、`no-team`、`pass-through` 会在每个 Hook 首次被实际调用时记录 `[hook-call]`：配置名、方法、原值、返回值和调用栈。若原方法抛出异常，记录 `[host-error]` 后原样抛出，绝不重复执行原方法或吞掉宿主异常。标签为 `TickTickPatch`，没有 Toast、通知或注入完成消息。`none` 没有模块调用日志是正常的；以安装版本名确认配置。

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
