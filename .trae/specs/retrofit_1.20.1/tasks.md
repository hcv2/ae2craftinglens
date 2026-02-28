# Tasks - 从 1.21.1 (NeoForge) 回退适配到 1.20.1 (Forge)

## [ ] Task 1: 配置 Access Transformer (AT)
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 在 `src/main/resources/META-INF/accesstransformer.cfg` 中配置 AT
  - 公开 AE2 1.20.1 的私有字段
- **Acceptance Criteria Addressed**: Requirement: Access Transformer 配置
- **Test Requirements**:
  - `programmatic` TR-1.1: AT 配置文件存在且格式正确
  - `human-judgement` TR-1.2: 包含所有必需的字段声明

## [ ] Task 2: 重写 NetworkHandler 使用 SimpleChannel
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 使用 `NetworkRegistry.newSimpleChannel` 创建通道
  - 为每个 Packet 类注册 encode/decode/handler 方法
- **Acceptance Criteria Addressed**: Requirement: Forge SimpleChannel 网络系统
- **Test Requirements**:
  - `programmatic` TR-2.1: NetworkHandler 编译无错误
  - `human-judgement` TR-2.2: 通道 ID 正确，协议版本正确

## [ ] Task 3: 修复 AEKey 序列化
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 在 `RequestPatternProvidersPacket` 中使用正确的 AE2 1.20.1 API
  - 编码：`AEKey.writeToPacket()`
  - 解码：`AEKey.fromPacket()`
- **Acceptance Criteria Addressed**: Requirement: AEKey 序列化 API
- **Test Requirements**:
  - `programmatic` TR-3.1: 序列化方法调用正确
  - `human-judgement` TR-3.2: 不再有 NoSuchMethodException

## [ ] Task 4: 添加主线程同步
- **Priority**: P1
- **Depends On**: Task 2
- **Description**: 
  - 在所有网络包的 handle 方法中使用 `context.enqueueWork()`
- **Acceptance Criteria Addressed**: Requirement: 主线程同步
- **Test Requirements**:
  - `programmatic` TR-4.1: 所有 handle 方法都使用 enqueueWork
  - `human-judgement` TR-4.2: 不抛出并发异常

## [ ] Task 5: 修复渲染相机偏移
- **Priority**: P1
- **Depends On**: none
- **Description**: 
  - 在 `PatternProviderHighlightRenderer` 中减去相机坐标
- **Acceptance Criteria Addressed**: Requirement: 渲染相机偏移修复
- **Test Requirements**:
  - `programmatic` TR-5.1: 代码包含相机坐标计算
  - `human-judgement` TR-5.2: 高亮框正确显示在世界坐标

## [ ] Task 6: 验证策略模式类名
- **Priority**: P2
- **Depends On**: none
- **Description**: 
  - 在 `canHandle` 方法中添加日志打印实际类名
  - 验证 1.20.1 中 Advanced AE 和 Extended AE 的类名
- **Acceptance Criteria Addressed**: Requirement: 策略模式类名验证
- **Test Requirements**:
  - `programmatic` TR-6.1: 日志包含类名信息
  - `human-judgement` TR-6.2: 类名与 1.20.1 匹配

## [ ] Task 7: 更新 mods.toml 版本要求
- **Priority**: P1
- **Depends On**: none
- **Description**: 
  - 将 Forge 版本降至 `[47.3,)`
  - 将 AE2 版本设为 `[15.0.0,)`
  - 将 Minecraft 版本设为 `[1.20.1,1.21)`
- **Acceptance Criteria Addressed**: Requirement: mods.toml 版本要求
- **Test Requirements**:
  - `programmatic` TR-7.1: mods.toml 版本范围正确
  - `human-judgement` TR-7.2: 模组能够加载

## [ ] Task 8: 更新 build.gradle 映射
- **Priority**: P0
- **Depends On**: none
- **Description**: 
  - 确保使用 `official` 或 `parchment` 映射
  - 更新 Minecraft 和 Forge 版本
- **Acceptance Criteria Addressed**: Requirement: 检查 Mappings
- **Test Requirements**:
  - `programmatic` TR-8.1: build.gradle 映射配置正确
  - `human-judgement` TR-8.2: 构建成功

## [ ] Task 9: 移除硬编码反射
- **Priority**: P2
- **Depends On**: Task 1
- **Description**: 
  - 将 `getDeclaredField("logic")` 改为 `cluster.logic`
  - 提高性能和稳定性
- **Acceptance Criteria Addressed**: Requirement: Access Transformer 配置
- **Test Requirements**:
  - `programmatic` TR-9.1: 代码不包含硬编码反射
  - `human-judgement` TR-9.2: 性能提升

# Task Dependencies
- Task 4 depends on Task 2
- Task 9 depends on Task 1
