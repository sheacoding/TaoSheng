# 通用设备接入

约定入口：DeviceContract.kt；产品字段与约束：docs/方案/外设接入字段表.md。

- DeviceContract.kt：说明卡、字节通道、文件插件与界面的公共契约。
- AndroidDeviceLink.kt：Android BLE 通道，处理 GATT 连接、通知和命令写入。
- AndroidDevices.kt：Android 端设备能力装配与平台适配。
- CardAudioSource.kt：按说明卡建立实时声音流，解码、保活及报告状态。
- DeviceCardsWork.kt：设备说明卡后台更新入口。
- DeviceSync.kt：串行持久化下载、校验、导入与安全删除；插件报告 ResumeOffsetRejected 时由核心清零偏移并有限重试，普通断连保留进度。
- DeviceSyncStore.kt：传输记录持久化及设备文件补充信息边界。
- ManagedDevices.kt：已记住设备、选择状态与重连生命周期。
- NotificationFrames.kt：根据声明长度跨通知拼接与拆分协议包。
- RemoteDeviceCards.kt：远端说明卡获取与可信更新。
- core/：说明卡解析、帧校验和音频解码；通用实现不依赖设备品牌。
- plugins/sonicloud/：声云公开 BLE 协议适配，详见局部 CLAUDE.md。

[PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
