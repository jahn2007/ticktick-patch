# TickTick Patch 2.0.1

应用包名 `io.github.ticktickpatch`，版本名 `2.0.1`，版本代码 `5`，使用 libxposed API `102.0.0`。只有一个正式实现，不提供多版本选择或诊断 APK 集合。

## 下载与安装

在 [v2.0.1 Release](https://github.com/jahn2007/ticktick-patch/releases/tag/v2.0.1) 下载 `TickTickPatch-2.0.apk`，安装后在支持 API 102 的框架中启用，勾选 TickTick / 滴答清单。

从不支持热重载的 1.1 首次升级后，需要重启 TickTick。固定 Release 签名与旧 Debug 签名不同时，先卸载旧模块再安装，保留 TickTick 及其数据。模块无桌面入口，不显示注入完成提示。

## 2.0.1 的行为

- 运行期通过 `TickTickApplicationBase.getAccountManager()` 获取实际账号管理器，不硬编码混淆类名。
- 在账号管理器中解析唯一的无参、返回 `data.User` 的实例方法，记录其返回的当前用户对象；身份未初始化或候选不唯一时不启用替换。
- 同时检查对象身份：只有捕获到的同一个当前用户实例才能获得替换值，不能仅靠账号 ID 非空判断。空白 `new User()` 和其他用户对象保留原值。
- 对该实例替换会员判断、订阅提示、会员类型和到期时间；`getProType()` 与 `getProTypeForFake()` 统一返回 1，`isPro()` 返回 true，`getProEndTime()` 返回 `253382774400000L`。保留 `getNoGraceDate()` 原值。
- 本地模式保留全部原结果：当前宿主在本地模式下只要 isPro 为 true 就可能停止提醒，不能用登录账号的修复承诺本地会员功能。isLocalMode 缺失或类型不匹配时保留宿主行为并记录错误。
- 账号管理器其他实例方法执行期间暂停替换并刷新当前用户，使用操作计数与代次防止旧账号调用覆盖新身份。
- 不修改宿主用户字段，不 Hook 网络响应或数据库用户模型，也不改团队状态。
- 完全移除提醒门禁 Hook，不查找任何混淆类名或门禁函数名，也不强制放行调度器；让一致的账号状态通过宿主自己的判断。旧额度方法只在原符号存在时兼容，新版实际额度路径未获得适配证明。

入口或字段变化时，模块保留原行为并记录必要错误。读取路径替换不直接写字段，但尚未证明所有宿主保存、序列化路径都不会读取替换值，不能承诺完全没有间接持久化影响。

## 热重载

`module.prop` 声明 `autoHotReload=true`。旧代在 `onHotReloading()` 同意重载并传递宿主 ClassLoader，新代在 `onHotReloaded()` 清理旧 Hook、重建新 Hook、重新识别账号，不依赖框架重放启动回调。

实际支持取决于框架，声明 API 102 不代表任何框架都能热重载。从 1.1 首次升级需冷启动；之后更新若框架不支持或重载未生效，也应重启宿主进程。设备上的热重载及新版宿主兼容性仍需确认。

## 构建与发布

CI 执行 Release 单元测试、Lint、打包检查和签名验证，通过后才发布正式 APK。这些检查不等同于新版 TickTick 的真机启动、提醒及会员功能验证。

根目录 `.github/workflows/build-xposed.yml` 在 xposed 分支相关变更、PR 或手动运行时构建。非 PR 使用四项 Repository Secrets：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 固定 JKS / PKCS12 签名文件的 Base64 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 私钥别名 |
| `KEY_PASSWORD` | 私钥密码 |

构建产物为 `TickTickPatch-release-2.0.1`，内含 `TickTickPatch-2.0.apk`。推送 v2.0.1 标签时，检查通过后才创建 GitHub Release；该正式标签已发布，不应为修改文档移动标签。PR 不使用 Secrets、不发布 APK，只检查未签名 Release 打包。

CI 使用 JDK 17、Gradle 9.3.1、Android SDK 37.0 和 Build Tools 36.0.0，无需本地安装环境。私钥临时解码到 Runner，构建结束后删除。

`.github/workflows/sync-release-notes.yml` 在发布说明变更时同步已有 v2.0.1 Release 的正文，不更换签名 APK 或移动发布标签。

作用域为 `com.ticktick.task` / `cn.ticktick.task`。2.0.1 不设宿主版本白名单，这不代表所有版本均兼容，服务器权限仍由服务端控制。

历史样例分析见 [APK_SAMPLE_ANALYSIS.md](APK_SAMPLE_ANALYSIS.md)，只用于解释旧版本证据；当前故障排查见 [REMINDER_DIAGNOSTICS.md](REMINDER_DIAGNOSTICS.md)。
