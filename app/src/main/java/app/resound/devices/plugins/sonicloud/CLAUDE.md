# 声云文件与实时协议插件

> L2 | 父级: ../../CLAUDE.md

约定入口：../../DeviceContract.kt；设计依据：docs/方案/外设接入字段表.md。

- SonicloudFiles.kt：复用 FrameCodec，经 DeviceLink 收发公开 BLE 协议；目录快照绑定连接与完整文件元数据，下载发送 uint32 小端偏移，中断时限时请求终止。缓存仅服务当前文件会话，持久化、重试和安全保存由 DeviceSync 负责。
- SonicloudLive.kt：从实时通知中分离音频与电量消息，避免控制/文件数据进入 Opus 解码器。
- 回归入口：app/src/test/java/app/resound/devices/SonicloudFilesTest.kt；核心持久化和删除门槛见 DeviceSync120Test.kt。

不依赖厂家 AAR，不联网，不新增界面或修改公共接口。说明卡仍为 trial，公开协议测试不能替代目标固件的真机验收。

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
