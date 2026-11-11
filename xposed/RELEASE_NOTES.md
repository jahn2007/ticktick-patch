TickTick Patch 2.0，libxposed API 102，版本代码 4。

- 以当前账号实例身份限定会员返回值，避免全局 User.isPro 替换将空白哨兵对象变成会员。
- 动态解析账号管理器和唯一当前用户入口，不硬编码混淆类名或宿主版本号。
- 账号管理器操作期间暂停替换，刷新当前用户，并防止旧调用覆盖新账号身份。
- 不修改宿主用户字段、网络响应、数据库模型或团队状态；保留 getNoGraceDate 原值。
- 当前用户的 getProType 返回 1，getProTypeForFake 返回 0，到期时间沿用原固定值。
- 启用 API 102 自动热重载回调，清理旧 Hook 并在新一代重建。实际热重载需框架支持。
- 沿用固定 Release 签名，不显示注入完成提示。

下载附件 TickTickPatch-2.0.apk。从不支持热重载的 1.1 首次升级后，请重启 TickTick；与旧 Debug 签名不同时，只卸载旧模块再安装，保留 TickTick 及其数据。

Release 单元测试、Lint、编译、打包及签名检查已通过。没有取得当前新版 TickTick APK，尚无新版宿主启动、提醒、功能及设备热重载的验证结果；CI 成功不代表这些运行行为已经确认。旧额度和提醒处理器只在原符号及形状匹配时兼容，不承诺新版全部功能等同于旧版。

读取路径替换没有直接写字段，但尚未排除宿主保存流程读取替换值的可能性。

最新使用与排查说明：https://github.com/jahn2007/ticktick-patch/blob/xposed/xposed/README.md
