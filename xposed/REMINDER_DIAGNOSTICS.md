# 正式版提醒排查

1. 确认安装版本为 1.1、模块作用域正确，并重启 TickTick 进程。
2. 新建普通任务，设置几分钟后的提醒，回到桌面等待；创建后不要强制停止应用。
3. 如果没有通知，收集 LSPosed 和提醒到期前后的 Android 系统日志，注明 Android、TickTick 与框架版本。

正式版保留 TickTickPatch 标签的缺类、方法跳过及替换失败日志。`[reminder-gate-unavailable]` 表示提醒处理器未匹配，需要核对当前版本的类名和方法。

若已有 ADB，执行 `adb logcat -v threadtime > reminder.log`，结束后按 Ctrl+C。首次采集不要只过滤主进程 PID，以免漏掉提醒服务。无需安装本地环境或生成多个诊断版本。
