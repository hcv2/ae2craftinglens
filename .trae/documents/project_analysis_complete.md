# AE2 Crafting Lens - 项目实现原理完整分析

## 模组功能概述
AE2 Crafting Lens 是一个 AE2（Applied Energistics 2）的辅助模组，用于在 AE2 的合成状态界面中高亮显示 Pattern Provider（样板供应器）的位置。

## 核心工作流程

### 1. 客户端 - 用户交互（CraftingScreenHandler.java）

**触发时机**：玩家在 AE2 CraftingStatusScreen 中按住 Shift + 左键点击合成任务

**处理流程**：
1. **事件监听**：通过 `@SubscribeEvent` 监听 `ScreenEvent.MouseButtonPressed.Pre`
2. **屏幕类型检测**：检查是否为 `CraftingStatusScreen` 或 `ae2wtlib.WCTScreen`
3. **点击位置检测**：判断点击是否在合成任务的物品显示区域
4. **AEKey 提取**：从点击的行中提取 AEKey（要合成的物品）
   - 优先从表格（table）中提取
   - 其次从选中的 CPU 中提取
   - 最后使用上次点击保存的 AEKey
5. **发送请求**：通过 `NetworkHandler.CHANNEL.sendToServer()` 发送 `RequestPatternProvidersPacket` 到服务器

**关键技术点**：
- 使用反射访问 AE2 的内部字段和方法
- 通过 `CraftingStatusMenu.cpuList` 获取 CPU 列表
- 通过 `GenericStack.what()` 获取 AEKey

### 2. 网络通信（NetworkHandler.java）

**网络通道**：
- 通道 ID：`ae2craftinglens:main`
- 协议版本：1
- 注册的网络包：
  - `RequestPatternProvidersPacket`（客户端 → 服务器）
  - `PatternProviderResponsePacket`（服务器 → 客户端）

### 3. 服务器端 - 处理请求（PatternProviderRequestHandler.java）

**接收请求**：
- 从 `RequestPatternProvidersPacket` 获取 `AEKey` 和 `rowIndex`

**查找 Grid**：
- 从 `CraftingStatusMenu` 中查找 `IAEGrid`
- 多种策略：直接方法调用、反射字段查找

**获取 Crafting Service**：
- 通过 `grid.getCraftingService()` 获取

**查找 Pattern Provider**：
- **策略 1**：从当前合成任务中查找（`findProvidersFromCurrentCraftingJob`）
  - 尝试从 Grid 中根据 CPU serial 查找 CraftingCPUCluster
  - 使用深度递归查找作为备用方案（`deepFindCraftingCPUCluster`）
  - 从 cluster 中查找与目标 AEKey 相关的 Pattern Provider
  
- **策略 2**：查找所有活跃的 Pattern Provider（`findAllActivePatternProviders`）
  - 遍历 Grid 中的所有节点
  - 检查是否为 Pattern Provider

**发送响应**：
- 通过 `NetworkHandler.CHANNEL.send()` 发送 `PatternProviderResponsePacket` 回客户端
- 包含所有找到的 Pattern Provider 的位置（dimension + BlockPos）

### 4. 客户端 - 显示高亮（ClientPacketHandler.java + PatternProviderHighlightManager.java）

**接收响应**：
- 从 `PatternProviderResponsePacket` 获取 Pattern Provider 位置列表

**显示消息**：
- 在聊天栏显示找到的 Provider 数量
- 对每个 Provider 显示：
  - 维度（可点击传送）
  - 坐标（可点击传送）
  - 距离（如果在同一维度）

**高亮显示**：
- 使用 `PatternProviderHighlightManager` 管理高亮的 Provider
- 使用 `PatternProviderHighlightRenderer` 渲染高亮框（金色发光效果）

## 关键技术实现

### AEKey 序列化（RequestPatternProvidersPacket.java）

**问题**：AE2 在不同版本中使用不同的序列化 API

**解决方案**（AE2 1.20.1）：
- **编码**：使用 `AEKey.writeToPacket(FriendlyByteBuf)` 实例方法
- **解码**：使用 `AEKeyType.fromPacket(FriendlyByteBuf)` 静态方法

```java
// 编码
Method writeToPacketMethod = aeKeyClass.getMethod("writeToPacket", FriendlyByteBuf.class);
writeToPacketMethod.invoke(packet.what, buffer);

// 解码
Class<?> keyTypeClass = Class.forName("appeng.api.stacks.AEKeyType");
Method fromPacketMethod = keyTypeClass.getMethod("fromPacket", FriendlyByteBuf.class);
Object what = fromPacketMethod.invoke(null, buffer);
```

### 反射访问 AE2 内部 API

**原因**：AE2 的某些内部类和方法不是公开的，需要通过反射访问

**示例**：
```java
// 获取选中的 CPU serial
Method getSelectedCpuSerialMethod = menu.getClass().getMethod("getSelectedCpuSerial");
int selectedCpuSerial = (int) getSelectedCpuSerialMethod.invoke(menu);

// 访问 cpuList 字段
Field cpuListField = menu.getClass().getDeclaredField("cpuList");
cpuListField.setAccessible(true);
Object cpuList = cpuListField.get(menu);
```

### 深度递归查找 CraftingCPUCluster

**使用场景**：当标准方法无法找到 CraftingCPUCluster 时作为备用方案

**实现**：
- 递归遍历对象图，深度限制为 5 层
- 通过反射访问所有字段和方法
- 查找类名包含 "CraftingCPUCluster" 的对象

**缺点**：
- 性能开销大
- 类型安全性差
- 可能在不同 AE2 版本中失效

## 数据流图

```
客户端                                    服务器
  |                                         |
  |--- Shift+Click 合成任务 ---------------->|
  |                                         |
  |--- 提取 AEKey ------------------------->|
  |                                         |
  |--- RequestPatternProvidersPacket ------>|
  |     (AEKey, rowIndex)                   |
  |                                         |
  |                                         |--- 查找 Grid
  |                                         |
  |                                         |--- 获取 Crafting Service
  |                                         |
  |                                         |--- 查找 Pattern Provider
  |                                         |
  |<-- PatternProviderResponsePacket -------|
  |     (List<Dimension, BlockPos>)         |
  |                                         |
  |--- 显示高亮框 ------------------------->|
  |--- 显示聊天消息 ----------------------->|
```

## 依赖关系

### 硬依赖
- **Minecraft**: 1.20.1
- **Forge**: 47.3.7+（版本范围 [47.3,)）
- **AE2**: 15.2.0+

### 软依赖（可选）
- **AE2 Wireless Terminals (ae2wtlib)**: 15.0.0+
  - 支持在 WCTScreen 中使用相同功能

## 已知问题和解决方案

### 1. Forge 版本兼容性
**问题**：`FMLJavaModLoadingContext.registerConfig()` 方法在 47.3.7 中不存在

**解决**：使用 `ModLoadingContext.get().registerConfig()`

### 2. AEKey 序列化 API 变化
**问题**：AE2 在不同版本中使用不同的序列化方法

**解决**：在 AE2 1.20.1 中使用 `writeToPacket()` 和 `AEKeyType.fromPacket()`

### 3. 深度递归查找性能问题
**问题**：`deepFindCraftingCPUClusterRecursive` 性能差、类型安全性低

**建议**：未来版本中改用更精确的查找策略

## 文件结构

```
src/main/java/com/ae2craftinglens/mod/
├── AE2CraftingLens.java                    # 模组主类
├── Config.java                             # 配置管理
├── CraftingScreenHandler.java              # 客户端屏幕事件处理
├── PatternProviderHighlightManager.java    # 高亮管理器
├── PatternProviderHighlightRenderer.java   # 高亮渲染器
└── network/
    ├── NetworkHandler.java                 # 网络注册
    ├── ClientPacketHandler.java            # 客户端包处理
    ├── RequestPatternProvidersPacket.java  # 请求包
    ├── PatternProviderResponsePacket.java  # 响应包
    └── PatternProviderRequestHandler.java  # 服务器请求处理
```

## 总结

AE2 Crafting Lens 通过以下技术实现其功能：
1. **客户端事件监听**：捕获玩家在 AE2 界面的操作
2. **反射访问**：访问 AE2 内部 API 获取合成信息
3. **网络通信**：在客户端和服务器之间传输数据
4. **服务端查找**：在服务器端查找 Pattern Provider 位置
5. **客户端渲染**：在客户端显示高亮框和信息

整个模组的核心是**通过反射访问 AE2 的内部 API**，这使得它能够与 AE2 深度集成，但也带来了版本兼容性的挑战。
