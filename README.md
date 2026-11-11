# TickTick Patch 2.0.1

基于 libxposed API 102 的 TickTick / 滴答清单模块，版本名 2.0.1，支持 API 102 热重载生命周期。

- [下载正式 APK](https://github.com/jahn2007/ticktick-patch/releases/tag/v2.0.1)
- [安装、工作原理、构建与热重载说明](xposed/README.md)
- [故障排查](xposed/REMINDER_DIAGNOSTICS.md)

2.0.1 以当前用户实例身份限定返回值替换，空白 User 和其他账号保留原值，不直接修改宿主字段或数据库。CI 已通过测试、Lint、编译与签名检查；新版宿主的实际兼容性仍需设备验证。
