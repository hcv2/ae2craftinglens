# 从 1.21.1 (NeoForge) 回退适配到 1.20.1 (Forge) Spec

## Why
当前项目代码架构基于 1.21.1 (NeoForge)，需要回退适配到 1.20.1 (Forge)。由于两个版本之间的 API 差异巨大，需要系统性地重构网络系统、反射访问、渲染层等核心组件，以确保模组在 1.20.1 环境中稳定运行。

## What Changes
- **网络系统**：从 NeoForge 的 CustomPacketPayload 回退到 Forge SimpleChannel
- **Access Transformer**：配置 AT 以访问 AE2 1.20.1 的私有字段
- **AEKey 序列化**：使用 AE2 1.20.1 的正确 API（writeToPacket/fromPacket）
- **渲染层**：修复相机坐标偏移问题
- **策略模式**：适配 1.20.1 的第三方模组类名
- **构建配置**：更新 mods.toml 和 build.gradle 以支持 1.20.1

## Impact
- **Affected specs**: 所有现有的 AEKey 序列化 spec、Forge 兼容性 spec
- **Affected code**: 
  - `network/` - 完全重写
  - `PatternProviderHighlightRenderer.java` - 修复相机偏移
  - `network/PatternProviderRequestHandler.java` - 适配 AE2 1.20.1 API
  - `META-INF/accesstransformer.cfg` - 新增配置
  - `META-INF/mods.toml` - 更新版本要求
  - `build.gradle` - 更新映射和依赖

## ADDED Requirements

### Requirement: Access Transformer 配置
The system SHALL configure Access Transformer to expose AE2 1.20.1 private fields:
- `CraftingCPUCluster.logic`
- `CraftingLogic.job`
- `CraftingJob.tasks`
- `CraftingNode.getPattern()`

#### Scenario: AT 配置成功
- **WHEN** 模组启动时
- **THEN** 反射代码能够访问 AE2 的私有字段，不抛出 `NoSuchFieldException`

### Requirement: Forge SimpleChannel 网络系统
The system SHALL use `NetworkRegistry.newSimpleChannel` for network communication.

#### Scenario: 网络包正确编码/解码
- **WHEN** 客户端发送请求
- **THEN** 使用 `AEKey.writeToPacket()` 编码，服务端使用 `AEKey.fromPacket()` 解码

### Requirement: 主线程同步
The system SHALL use `context.enqueueWork()` to ensure network handlers run on main thread.

#### Scenario: 线程安全
- **WHEN** 处理网络包时
- **THEN** 所有访问 AE2 API 的代码都在主线程执行，不抛出并发异常

### Requirement: 渲染相机偏移修复
The system SHALL subtract camera position from PoseStack when rendering highlights.

#### Scenario: 正确的世界坐标渲染
- **WHEN** 渲染高亮框时
- **THEN** 高亮框正确显示在世界坐标，而不是屏幕坐标

## MODIFIED Requirements

### Requirement: AEKey 序列化 API
**Before**: 使用 NeoForge 1.21.1 的序列化方式
**After**: 使用 AE2 1.20.1 的正确方法：
- 编码：`AEKey.writeToPacket(FriendlyByteBuf, AEKey)` (静态方法) 或实例方法
- 解码：`AEKey.fromPacket(FriendlyByteBuf)` (静态方法)

### Requirement: mods.toml 版本要求
**Before**: NeoForge 1.21.1, AE2 15.4.10+
**After**: 
- Forge: `[47.3,)` (对应 1.20.1)
- AE2: `[15.0.0,)`
- Minecraft: `[1.20.1,1.21)`

### Requirement: 策略模式类名验证
**Before**: 假设使用 1.21.1 的类名
**After**: 在 `canHandle` 方法中添加日志，打印实际的类名以验证 1.20.1 的类名

## REMOVED Requirements

### Requirement: NeoForge CustomPacketPayload
**Reason**: 1.20.1 Forge 不支持 CustomPacketPayload API
**Migration**: 使用传统的 SimpleChannel 和手动 encode/decode

### Requirement: 硬编码反射
**Reason**: 性能差且不稳定
**Migration**: 配置 AT 后直接使用字段访问（`cluster.logic` 而不是反射）
