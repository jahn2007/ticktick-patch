# 正常 APK 与生成脚本的差异

本文保留此前排查的历史证据。文中提及的多版本配置和分析脚本已从正式构建移除；正式版 1.1 固定采用用户确认正常的最终合并方案。下文旧的“待验证”“默认 full”等表述仅反映分析时的状态。

分析对象：用户提供的 `TickTick-Premium-v8.0.0.1_build_8001-Mod OSSSR.apk`。清单确认包名 `com.ticktick.task`，版本 `8.0.0.1`、版本号 `8001`，文件大小 62,864,494 字节。

SHA-256：`845d9c29b6ce47943a7138306eacb8c10d042543a85eaedd8a393d47b2cd0522`。

使用仓库中的 `scripts/inspect_dex.py` 直接读取五个 DEX，不安装反编译环境。每个 DEX 的文件长度、字节序、Adler-32 和 SHA-1 均通过校验。解码遵循 [AOSP DEX 格式](https://source.android.com/docs/core/runtime/dex-format) 和 [Dalvik 指令格式](https://source.android.com/docs/core/runtime/dalvik-bytecode)。下文偏移量均以 16 位代码单元计数，不是 Java 源码行号。

## 遗漏的提醒检查行为

`classes3.dex` 中的 `TaskAlertJobService.e(Intent)` 创建 `qc.e` 并调用其 `g(Intent)`；`classes.dex` 中 `qc.e.e(String)` 的日志字符串明确为 `AlertServiceHandler.onSchedule cost = `。因此可以把 `qc.e` 识别为该样例的提醒处理器，而不是仅凭混淆类名猜测。

它的静态方法 `qc.e.h()Z` 有这些分支（去除无关的常量写入后）：

```java
User probe = new User();
if (probe.isPro()) return false;
if (probe.getProType() != 0) return false;
if (new SignUserInfo().isPro()) return false;
User current = getCurrentUser();
if (current.isLocalMode()) return current.isPro();
if (current.isPro() && current.getProTypeForFake() == 0) return false;
return false;
```

关键指令：

```text
qc.e.h()Z:
000f invoke-virtual User.getProType()I
0012 move-result v0
0014 if-eqz v0 -> 0017
0016 goto -> 0043
0043 const/4 v2, 4
0044 const/4 v0, 0
0045 const/4 v2, 3
0046 return v0

qc.e.e(String):
0006 invoke-static qc.e.h()Z
0009 move-result v2
000b if-eqz v2 -> 000e
000d return-void
000e ...继续调度提醒...
```

本地模式分支在方法体中仍存在，不能把原始方法体简化为“每个 return 都写死 false”。但样例同时把 `User.getProType()` 固定为 1，所以只要探测正常执行，前面的探测就必然返回 false，后面的本地模式分支不会被走到。这说明完整样例的有效行为与只修改 getter 的脚本不同。

`lspilot_main.java` 没有对提醒处理器 `h()` 的 Hook。8220 的设备日志也在 `jd.d.h(AlertServiceHandler.java)` 中调用 `SignUserInfo.isPro()`，但尚未取得 8220 的字节码或 `h()` 的返回值，因此不能把 8001 的全部控制流直接当作 8220 的源码。

## 其他已经验证的差异

| 项目 | 8001 样例实际行为 | 原脚本/旧完整版 |
| --- | --- | --- |
| `User.getProType()` | 1 | 1 |
| `User.getProTypeForFake()` | 0 | 1 |
| `User.getProEndTime()` | 10611728865536 | 253382774400000 |
| `User.isPro()` | 读取原字段，并调用 `isActiveTeamUser()` | 直接将最终结果设为 true |
| `entity.SignUserInfo.getIsProN()` | Boolean 字段为空时补 false，返回字段值 | true |
| 网络模型、JS 模型的日期 getter | 返回存储字段 | 统一修改日期 |
| `sync.db.User` 的会员相关 getter | 返回存储字段 | 修改类型、时间、订阅和团队结果 |
| `LimitHelper.getLimitsFree()` | 取 free 缓存或 `getLimits(false)` | 返回 `getLimitsPro()` |
| 提醒调度前的 `h()` | 探测分支返回 false，调度继续 | 未还原该行为 |

`User.isActiveTeamUser()` 在样例中固定为 true，因此保留 `User.isPro()` 方法体不等于样例实际显示非会员；这是组合行为的一部分。不能只比 getter 名单，也不能仅用某个局部常量判断整个方法的返回值。

## 本地修复候选

- `pro-reminder-gate`：保持原 `pro` 的八个候选 getter，只新增提醒检查返回 false，用于隔离遗漏的检查入口是否解释 8220 的故障。日志会记录该检查的原值与替换值。
- `apk-sample`：仅注册样例中直接观察到的 18 个常量结果 getter，保留计算型 `User.isPro()`、实体/JS/数据库字段 getter 和 free 限额逻辑；新增提醒检查，并使 `getProTypeForFake()` 返回 0、到期时间与样例相同。

处理器只尝试设备日志证明的 `jd.d`（8220）与样例证明的 `qc.e`（8001）。安装前必须匹配静态、无参、boolean 的 `h()` 和 `e(String):void`、`g(Intent):void` 的实例方法形状。不匹配时跳过并记录 `[reminder-gate-unavailable]`，不会搜索或拦截其他任意类的 `h()`。

`apk-sample` 还原的是这些已检查的 getter 与提醒入口，不是整个 APK 的完整差异清单。缺少同版本官方 APK，无法证明哪些指令属于破解者改动；也不能保证 8001 与 8220 的所有会员或提醒逻辑相同。默认配置仍保留旧的 `full`，待真机验证后再决定最终产品配置。新增候选尚未在 GitHub 编译，也没有真机修复证明。

下一轮只需先确认 `none` 对新任务仍正常，再测试 `pro-reminder-gate` 和 `apk-sample`。优先收集 `jd.d.h()` 的 `[hook-call]`（尤其是 `original=true replacement=false`）、任何 `[host-error]` 或 `[reminder-gate-unavailable]`。它们比继续测试全部单 getter 更直接。
