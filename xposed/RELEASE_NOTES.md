TickTick Patch 1.1 正式版采用已验证正常的最终合并方案，使用 libxposed API 102。

- 保留非团队会员状态、到期时间及会员额度逻辑。
- 修复提醒调度检查，getProTypeForFake 返回 0。
- 移除多版本配置、诊断 APK 构建及测试调用日志。
- 使用固定 Release 签名，不显示注入完成提示。

已测试环境：Android 15、TickTick Play 8.2.2.0（8220）、LSPosed 2.2.1（7912）/ API 102。

从旧 Debug 模块切换时，若签名不一致，卸载旧模块后安装正式版；无需卸载 TickTick 或清除其数据。
