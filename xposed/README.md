# TickTick Patch · libxposed API 102

按仓库根目录的 `lspilot_main.java` 移植的纯 Java Xposed 模块。使用已发布的 `io.github.libxposed:api:102.0.0`（`compileOnly`），不打包框架 API，也不依赖旧版 XposedBridge 或 LSPilot。

## 在 GitHub 上直接生成 APK

1. 将**整个仓库**上传到 GitHub，保留根目录 `.github/workflows/build-xposed.yml` 和 `xposed/` 的相对位置。工作流不能只放在 `xposed/.github/` 中。
2. 在仓库 **Actions → Build Xposed APK → Run workflow** 手动运行；修改模块或工作流后的 push、pull request 也会自动触发。手动运行按钮需要工作流已存在于默认分支。
3. 成功后，在本次运行的 **Artifacts** 下载 `TickTickPatch-debug-运行编号`，解压得到可直接安装的 `app-debug.apk`。无须配置 Secrets。
4. 安装 APK，在支持 **libxposed API 102** 的框架管理器中启用模块，勾选 TickTick / 滴答清单，强制停止并重新打开目标应用。模块没有桌面入口和设置界面。

CI 自动准备 JDK 17、Gradle 9.3.1、AGP 9.1.1、Android SDK 37 / Build Tools 36.0.0，执行单元测试、Android Lint、APK 编译、Xposed 元数据检查及签名验证。**不需要在你的电脑上安装开发环境。** Gradle 由 Actions 固定版本供应，本项目没有 Gradle Wrapper。

libxposed 102.0.0 发布的 AAR 元数据要求 `minCompileSdk=37`，因此不能直接用 SDK 35/36 编译。[AGP 9.1.1 官方兼容表](https://developer.android.com/build/releases/agp-9-1-0-release-notes)要求 Gradle 9.3.1、JDK 17，并支持 API 37。模块自身最低安装版本为 Android 8.0（API 26）；还需满足所用框架和宿主应用的版本要求。

每次新的 GitHub Runner 可能生成不同的 debug 签名，覆盖安装出现签名不一致时，需要先卸载旧模块。需要长期更新请配置下面的固定 release 签名。

## 可选：生成固定签名的 Release APK

在仓库 Settings → Secrets and variables → Actions 中配置以下四个 Repository secrets：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 你持有的 JKS / PKCS12 签名文件的 Base64 内容 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 签名密钥别名 |
| `KEY_PASSWORD` | 签名密钥密码 |

四项全部配置后，push 或手动运行还会生成 `TickTickPatch-release-运行编号`，内含可安装的 `app-release.apk`。PR 只构建 debug，不使用签名 Secrets。四项都不配置时跳过 release；仅配置部分时工作流明确报错，debug 产物仍已上传。私钥仅临时写入 Runner，结束时删除，不随产物上传。工作流不会自动创建 GitHub Release。

## Hook 覆盖与兼容处理

- 响应模型：新旧 `SignUserInfo`、`User7ProModel`、`SubscriptionInfo`。
- 用户状态：`data.User`、`sync.db.User`、`ProHelper`、内核 `AccountManager`。
- 额度：`LimitHelper.getLimitsFree()` 在原调用完成后读取 `getLimitsPro()`。
- JS 桥：`CommonJavascriptObject$UserProfile`。
- 到期时间沿用脚本的 `253382774400000` / `9999-05-20T00:00:00.000+0000`。自动匹配 `Date`、`String` 或带单参数 `long` 构造器的日期类，每次调用生成新的可变日期对象。

通过 `onPackageReady()` 取得宿主 ClassLoader，并在创建 Application 前安装 Hook，无须反射 `ActivityThread.currentApplication()`。包名限定为 `com.ticktick.task` / `cn.ticktick.task`；类名沿用参考脚本的 `com.ticktick.*`，并不意味着已验证所有国内版。每个进程各自安装，同一 ClassLoader 不重复安装。

使用 API 102 的 `hook(method).setId(...).intercept(...)`，在 `chain.proceed()` 成功返回后修改结果，保留原方法副作用和异常。只匹配无参 getter；缺类、缺方法、抽象方法、返回类型不符分别跳过并记录日志，不阻止其余 Hook。日期构造或专业额度读取失败时保留原结果。

Debug 版本保留会员方法清单审计，以及每个 `Database.updateUser` 重载首次调用的状态字段日志。Release 关闭这两类调试日志。未迁移依赖 Application 的延迟账户探针和 Toast；状态与额度 Hook 已迁移。未启用热重载，更新模块后需要重启目标应用进程。

## 验证与排查

模块不显示注入完成的 Toast、通知或弹窗，也不输出加载成功及安装完成的汇总消息；参考脚本末尾的完成提示已一并移除。故障与调试日志仍使用 `TickTickPatch` 标签。多个版本的候选模型不会全部同时存在，部分跳过属正常现象；功能未生效时应检查作用域、框架版本及宿主类名是否变化。

应用图标使用 `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`，尺寸依次为 48、72、96、144、192 像素，由提供的 `asset/icon.png` 等比缩小并迁移而来；Manifest 通过 `android:icon="@mipmap/ic_launcher"` 引用。无需把图标放入 Xposed 的 `META-INF/xposed` 注册目录。

参考脚本标注针对 TickTick 8.2.2.0；本模块尚未在真机上验证。CI 成功代表测试、静态检查、编译和签名验证通过，不代表宿主运行时 Hook 已验证。模块修改客户端方法返回值，服务器端权限及接口限制仍由服务端决定。

接口依据：[libxposed API 102.0.0](https://central.sonatype.com/artifact/io.github.libxposed/api/102.0.0)、[现代 Xposed 模块入口说明](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)。实现以 Maven 发布的 102.0.0 源码签名为准。
