# devices 测试

约定入口：主源码 devices/DeviceContract.kt；验证行为而非替代硬件验收。

- SonicloudFilesTest.kt：文件插件的偏移请求、中断清理、目录验证与删除快照；使用假 DeviceLink，不连接硬件。
- DeviceSync120Test.kt：同步核心的持久化续传、去重、安全删除门槛、迁移及声云原始协议帧。
- Device119Test.kt：设备连接、来源切换及实时帧处理。
- DeviceCards129Test.kt：包内设备说明卡可加载性。
- RemoteDeviceCardsTest.kt：远端设备说明卡更新与信任边界。
- core/CardCodec118Test.kt：通用帧格式、校验和声音解码。

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
