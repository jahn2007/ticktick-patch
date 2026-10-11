# 合并版 merged-no-team

按用户选择，以旧 full 的非团队 Hook 为基础，加入已验证的提醒检查；类型冲突采用样例值，时间冲突采用 full 值。默认构建现为 merged-no-team，普通 Debug / 可选 Release 产物均采用此配置；诊断 APK 集合中的文件名为 `TickTickPatch-merged-no-team.apk`。旧 full 已移除构建声明，下表仅保留其历史行为供比较。当前仅完成本地源码修改，尚未编译或验证此组合的真机行为。

| 项目 | full | apk-sample | merged-no-team |
| --- | --- | --- | --- |
| 团队身份、团队会员与团队日期 | 15 个相关 getter | 7 个相关 getter | 全部不安装，保留宿主原值 |
| 提醒检查 | 不修改 | 匹配到的 h() 返回 false | 与 apk-sample 相同 |
| User.getProType() | 1 | 1 | 1 |
| User.getProTypeForFake() | 1 | 0 | 0 |
| User.getProEndTime() | 253382774400000L | 10611728865536L | 253382774400000L |
| 数据库 getPRO_END_TIME() | 253382774400000L | 不修改 | 253382774400000L |
| 其他非团队到期日期 | 改为 full 远期日期 | 不修改 | 与 full 相同 |
| 会员判断 | 8 项直接设为 true | 3 项直接设为 true，本地 User.isPro() 保留计算 | 与 full 相同 |
| 需要订阅 | 多层 getter 返回 false | 5 项 getter 返回 false | 与 full 相同 |
| 宽限期、相关日期、重复订阅错误 | false / null | 不修改 | 与 full 相同 |
| 免费额度 | 返回会员额度对象 | 不修改 | 与 full 相同 |
| 非团队 JS / 数据库模型 getter | 修改 | 不修改 | 与 full 相同 |
| Debug 数据库观察及方法审计 | 启用，观察不改数据 | 不启用 | 不启用；保留首次调用及错误日志 |
| 配置目标数量 | 49 个值 Hook + 1 个额度 Hook，另有观察 Hook | 18 个值 Hook + 1 个提醒检查 | 34 个值 Hook + 1 个额度 Hook + 1 个提醒检查 |

缺失或返回类型不匹配的方法会跳过，实际安装数量以宿主和日志为准。提醒检查沿用 `jd.d` / `qc.e` 及方法形状校验，只安装第一个匹配入口。原方法仍先执行，成功返回后替换结果，异常原样传播。

删除团队 Hook 表示不主动修改这些 getter，并不保证团队派生状态完全不受其他 Hook 影响。getter 返回值修改也不等同于直接更新数据库或服务器记录。

用户已验证 pro-reminder-gate 与 apk-sample 正常提醒，并以 pro → pro-reminder-gate → pro 稳定复现失败 → 正常 → 失败。merged-no-team 加入更多非团队 Hook，仍需独立验证，不能将前两者的结果视为本组合的运行证明。
