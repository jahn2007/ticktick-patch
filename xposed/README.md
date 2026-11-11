# TickTick Patch 2.0

2.0 使用 libxposed API 102，修复全局 User.isPro 返回 true 导致空白哨兵对象异常的问题。账号入口运行期解析，不硬编码 q5a/d，也不做宿主版本号白名单。

只对从当前账号管理器获取、身份已初始化的同一个 User 对象替换会员判断、类型、到期时间和订阅提示；不修改对象字段、网络响应模型、数据库模型或团队字段。账号更新期间暂停替换并刷新实例。入口缺失或不唯一时保留宿主原行为并记录错误。旧版额度与提醒处理器仅在原符号存在且形状匹配时兼容。

已启用 autoHotReload，旧代同意热重载后，新代会清理旧 Hook、恢复宿主 ClassLoader 并重新识别账号。实际是否支持热重载取决于框架；从未支持热重载的 1.1 首次升级时需要重启宿主。模块不显示注入完成提示。

当前没有新版 APK / 真机验证，不能将单元测试与编译成功视为新版宿主的功能验证。读取路径替换不直接写字段，但尚未证明所有宿主序列化路径都不会读取替换值。

构建：配置原有四项签名 Secrets，推送 v2.0 标签；CI 通过 Release 单元测试、Lint、模块元数据和签名检查后，发布 TickTickPatch-2.0.apk。无需本地 JDK / SDK / Gradle。

以下为 1.1 历史说明，2.0 行为以本节及源码为准。

使用 libxposed API 102 的纯 Java 模块。正式版固定采用用户已验证正常的最终合并方案，不再提供多版本选择或诊断 APK 集合。应用包名 `io.github.ticktickpatch`，版本名 `1.1`，版本代码 `3`。

## 正式版行为

- 34 个非团队返回值 Hook，不修改团队身份、团队会员或团队日期。
- `User.getProType()` 为 `1`，`User.getProTypeForFake()` 为 `0`。
- 用户和数据库模型的到期时间均为 `253382774400000L`；其他非团队日期沿用远期日期。
- 免费额度读取会员额度对象。
- 校验提醒处理器 `jd.d` / `qc.e` 的方法形状后，令第一个匹配的 `h()` 返回 `false`。

Hook 行为与最终 `merged-no-team` 测试版相同，用户已在 Android 15、TickTick Play 8.2.2.0（8220）、LSPosed 2.2.1（7912）/ API 102 上确认正常工作。宿主升级后需重新验证。

原方法成功返回后才替换结果，保留原副作用和异常；日期或额度构造失败时保留原结果。缺类、缺方法或类型不匹配会跳过并记录日志。没有首次调用栈、数据库观察、方法审计或注入完成 Toast、通知、弹窗。模块无桌面入口。

## GitHub 构建与发布

工作流 `.github/workflows/build-xposed.yml` 在 xposed 分支修改、PR 和手动运行时执行 Release 单元测试、Lint 及打包检查。非 PR 构建必须配置四项 Repository Secrets：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 固定 JKS / PKCS12 签名文件的 Base64 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 私钥别名 |
| `KEY_PASSWORD` | 私钥密码 |

普通构建上传 `TickTickPatch-release-1.1`，内含 `TickTickPatch-1.1.apk`。推送 v1.1 标签后，测试、模块元数据与签名检查全部通过才自动创建 GitHub Release 并附上 APK。PR 不使用 Secrets、不发布产物，只检查未签名 Release 的打包。

CI 使用 JDK 17、Gradle 9.3.1、Android SDK 37.0、Build Tools 36.0.0，无需安装本地环境。私钥只临时存在 Runner 上，结束时删除，不随产物上传。

安装后在 API 102 框架中启用模块，勾选 TickTick / 滴答清单并重启宿主进程。旧 Debug 签名不同时，先卸载旧模块再安装，保留 TickTick 及其数据。

作用域为 `com.ticktick.task` / `cn.ticktick.task`，目标类沿用 `com.ticktick.*`；已验证的是上述 Play 版本。服务器权限仍由服务端控制。

历史字节码证据见 [APK_SAMPLE_ANALYSIS.md](APK_SAMPLE_ANALYSIS.md)，正式版排查见 [REMINDER_DIAGNOSTICS.md](REMINDER_DIAGNOSTICS.md)。
