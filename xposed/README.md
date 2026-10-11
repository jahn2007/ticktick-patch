# TickTick Patch 1.1

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
