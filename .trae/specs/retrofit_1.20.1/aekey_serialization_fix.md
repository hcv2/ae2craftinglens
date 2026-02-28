# AEKey 序列化修复总结

## 问题诊断

在 AE2 1.20.1 版本中，网络包的 AEKey 序列化存在以下问题：

### 1. 编码/解码顺序不一致
**原始问题**：
- 编码时：`rowIndex` → (如果 what==null 则直接 return) → `hasKey` → AEKey data
- 解码时：`rowIndex` → `hasKey` → AEKey data

**后果**：当 `packet.what == null` 时，编码只写入了 `rowIndex`，但解码会尝试读取 `hasKey`，导致 Buffer 读取错位。

### 2. AEKey 序列化方法调用
在 AE2 1.20.1 (API 12.x) 中：
- **写入**：`void writeToPacket(FriendlyByteBuf buf)` - 实例方法
- **读取**：`static AEKey readFromPacket(FriendlyByteBuf buf)` - 静态方法

**关键点**：
- `readFromPacket` 会先读取一个类型 ID (short/byte)，然后根据 `AEKeyTypes` 注册表调用对应的解析器
- 必须确保编码/解码顺序严格一致，否则类型 ID 会读取错误

## 修复方案

### 修复 1：统一编码/解码顺序

**编码方法**：
```java
public static void encode(RequestPatternProvidersPacket packet, FriendlyByteBuf buffer) {
    try {
        // 严格按照顺序编码：rowIndex -> hasKey -> AEKey data
        buffer.writeInt(packet.rowIndex);
        boolean hasKey = (packet.what != null);
        buffer.writeBoolean(hasKey);
        
        if (hasKey) {
            // 使用反射调用 AEKey 的 writeToPacket 实例方法
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Method writeToPacketMethod = aeKeyClass.getMethod("writeToPacket", FriendlyByteBuf.class);
            writeToPacketMethod.invoke(packet.what, buffer);
        }
    } catch (Exception e) {
        AE2CraftingLens.LOGGER.error("Error encoding AEKey", e);
    }
}
```

**解码方法**：
```java
public static RequestPatternProvidersPacket decode(FriendlyByteBuf buffer) {
    // 严格按照编码顺序解码：rowIndex -> hasKey -> AEKey data
    int rowIndex = buffer.readInt();
    Object what = null;
    
    try {
        boolean hasKey = buffer.readBoolean();
        if (!hasKey) {
            return new RequestPatternProvidersPacket(null, rowIndex);
        }
        
        // 尝试直接调用 AEKey.readFromPacket 静态方法
        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        Method readFromPacketMethod = aeKeyClass.getMethod("readFromPacket", FriendlyByteBuf.class);
        what = readFromPacketMethod.invoke(null, buffer);
        
        return new RequestPatternProvidersPacket(what, rowIndex);
    } catch (Exception e) {
        AE2CraftingLens.LOGGER.error("Error decoding AEKey", e);
        // 如果解码失败，返回 null key 但保留 rowIndex
        return new RequestPatternProvidersPacket(null, rowIndex);
    }
}
```

### 修复 2：简化错误处理

**改进点**：
- 移除了复杂的多层 try-catch 嵌套
- 统一使用简单的异常捕获和日志记录
- 解码失败时返回 null key 而不是抛出异常，保证游戏不崩溃

### 修复 3：反射调用优化

**关键变化**：
- 移除了 `getDeclaredMethod()` + `setAccessible(true)` 的复杂方案
- 统一使用 `getMethod()` 进行反射调用
- 静态方法调用时第一个参数传 `null`

## 技术要点

### 1. Java 17 反射限制
- 对于 Mod 代码（AE2），普通的 `setAccessible(true)` 依然有效
- 但对于接口静态方法，直接使用 `getMethod()` 更稳定

### 2. AE2 1.20.1 序列化机制
- AE2 内部通过 `AEKeyTypes` 注册表管理不同类型的 AEKey（物品、流体等）
- `readFromPacket` 会自动读取类型 ID 并分发到对应的解析器
- 不需要手动注册序列化器到网络包

### 3. 网络包稳定性
- 编码/解码顺序必须严格一致
- 如果编码失败，不应该尝试重新写入 buffer（会导致错位）
- 解码失败时应该返回安全的默认值而不是崩溃

## 测试建议

### 游戏内测试步骤：
1. 启动 Minecraft 1.20.1 + Forge 47.3+
2. 安装 AE2 1.20.1 (15.4.10+)
3. 安装 ae2craftinglens-1.0.2.jar
4. 进入世界并打开 AE2 Crafting Status Screen
5. Shift+Left-click 点击合成任务
6. 观察日志输出

### 预期结果：
- ✅ 游戏启动无崩溃
- ✅ 点击合成任务时无报错
- ✅ Pattern Provider 高亮框正确显示
- ✅ 日志中无 `NoSuchMethodException`
- ✅ 日志中无 `IndexOutOfBoundsException`

### 需要收集的日志：
```
[Render thread/INFO] [AE2CraftingLens/]: 成功编码 AEKey
[Render thread/INFO] [AE2CraftingLens/]: 成功解码 AEKey
```

如果出现问题，请收集完整的错误日志。

## 构建信息

- **构建版本**：ae2craftinglens-1.0.2.jar
- **构建时间**：2026-02-28
- **Minecraft 版本**：1.20.1
- **Forge 版本**：47.4.10 (兼容 [47.3,))
- **AE2 版本**：15.4.10+

## 下一步

如果测试中仍然遇到问题，可能需要：

1. **添加 AE2 依赖**：在 build.gradle 中添加 AE2 的 compileOnly 依赖，避免反射
2. **检查 AT 配置**：确认 accesstransformer.cfg 正确应用到 AE2 的类
3. **使用 SafeReflection**：考虑使用 Forge 的 SafeReflection 工具类
4. **直接 API 调用**：如果反射持续失败，尝试直接使用 AE2 API（需要添加依赖）

## 参考资源

- AE2 1.20.1 源码：https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/forge/1.20.1
- AEKey.java: `appeng/api/stacks/AEKey.java`
- AEKeyType.java: `appeng/api/stacks/AEKeyType.java`
- Forge 网络编程指南：https://docs.minecraftforge.net/en/latest/networking/
