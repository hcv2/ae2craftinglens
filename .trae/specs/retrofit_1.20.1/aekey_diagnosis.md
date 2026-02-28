# AE2 1.20.1 AEKey 序列化深度诊断与修复指南

## 问题现状

尽管已经将方法名改为 `readFromPacket`，但仍然报告 `NoSuchMethodException`。这说明问题可能不仅仅是方法名称的问题。

## 深度分析

### AE2 1.20.1 中 AEKey 的特殊性

在 AE2 1.20.1 中：
1. **`AEKey` 是一个接口**，不是具体类
2. **静态方法的位置**可能在内部类或特定的实现中
3. **序列化流程**需要先读取类型，再读取数据

### 可能的原因

1. **混淆问题**：生产环境中方法名可能被混淆
2. **类加载问题**：反射时类加载器可能不正确
3. **方法签名问题**：参数类型可能不完全匹配
4. **API 位置问题**：方法可能在其他地方

## 当前代码状态

### encode 方法（第 28-44 行）
```java
public static void encode(RequestPatternProvidersPacket packet, FriendlyByteBuf buffer) {
    buffer.writeInt(packet.rowIndex);  // ✅ 顺序正确：先写 rowIndex
    try {
        if (packet.what == null) {
            buffer.writeBoolean(false);
            return;
        }
        buffer.writeBoolean(true);
        // 使用反射调用 writeToPacket 实例方法
        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        Method writeToPacketMethod = aeKeyClass.getMethod("writeToPacket", FriendlyByteBuf.class);
        writeToPacketMethod.invoke(packet.what, buffer);
    } catch (Exception e) {
        AE2CraftingLens.LOGGER.error("Error encoding AEKey", e);
        buffer.writeBoolean(false);
    }
}
```

### decode 方法（第 46-65 行）
```java
public static RequestPatternProvidersPacket decode(FriendlyByteBuf buffer) {
    int rowIndex = buffer.readInt();  // ✅ 顺序正确：先读 rowIndex
    Object what = null;
    
    try {
        boolean hasKey = buffer.readBoolean();
        if (!hasKey) {
            return new RequestPatternProvidersPacket(null, rowIndex);
        }
        // 使用反射调用 readFromPacket 静态方法
        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        Method readFromPacketMethod = aeKeyClass.getMethod("readFromPacket", FriendlyByteBuf.class);
        what = readFromPacketMethod.invoke(null, buffer);
        
        return new RequestPatternProvidersPacket(what, rowIndex);
    } catch (Exception e) {
        AE2CraftingLens.LOGGER.error("Error decoding AEKey", e);
        return new RequestPatternProvidersPacket(null, rowIndex);
    }
}
```

## 诊断步骤

### 1. 检查完整的错误日志

请在游戏测试后提供**完整的错误堆栈**，特别是：
```
java.lang.NoSuchMethodException: appeng.api.stacks.AEKey.readFromPacket(net.minecraft.network.FriendlyByteBuf)
    at java.lang.Class.getMethod(Class.java:2229)
    at com.ae2craftinglens.mod.network.RequestPatternProvidersPacket.decode(...)
```

### 2. 确认 AE2 版本

检查游戏中实际加载的 AE2 版本：
- 在主菜单按 F3+C 查看模组列表
- 或在日志中搜索 `Loaded AE2 version:`

### 3. 验证方法存在性

如果仍然报错，说明 `readFromPacket` 方法可能不存在于你使用的 AE2 版本中。

## 替代方案

### 方案 A：使用 AE2 内部 API（推荐）

如果 AE2 1.20.1 的 `AEKey` 接口确实没有 `readFromPacket` 方法，可能需要使用内部 API：

```java
// 可能需要通过 AEKeyType 来读取
Class<?> keyTypeClass = Class.forName("appeng.api.stacks.AEKeyType");
Method readTypeMethod = keyTypeClass.getMethod("readFromPacket", FriendlyByteBuf.class);
Object keyType = readTypeMethod.invoke(null, buffer);

// 然后通过 keyType 读取具体的 AEKey
Method readKeyMethod = keyTypeClass.getMethod("readKey", FriendlyByteBuf.class, keyTypeClass);
Object what = readKeyMethod.invoke(null, buffer, keyType);
```

### 方案 B：使用 NBT 序列化

如果网络序列化不可用，可以尝试通过 NBT：

```java
// 编码
Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
Method toTagMethod = aeKeyClass.getMethod("toTag");
Object tag = toTagMethod.invoke(packet.what);
// 写入 NBT 到 buffer

// 解码
// 从 buffer 读取 NBT
Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
Method fromTagMethod = aeKeyClass.getMethod("fromTag", CompoundTag.class);
Object what = fromTagMethod.invoke(null, tag);
```

### 方案 C：直接访问 AE2 内部类

如果 AE2 有具体的实现类，可以直接访问：

```java
// 尝试访问内部实现
try {
    Class<?> keyClass = Class.forName("appeng.api.stacks.AEKey$Impl");
    Method readMethod = keyClass.getMethod("readFromPacket", FriendlyByteBuf.class);
    what = readMethod.invoke(null, buffer);
} catch (ClassNotFoundException e) {
    // 尝试其他路径
}
```

## 检查清单

### 编码/解码顺序
- [x] encode: 先写 `rowIndex`，再写 `what`
- [x] decode: 先读 `rowIndex`，再读 `what`
- [x] 使用相同的布尔值标记判断是否为 null

### 方法签名
- [x] encode: `writeToPacket(FriendlyByteBuf)` - 实例方法
- [x] decode: `readFromPacket(FriendlyByteBuf)` - 静态方法

### 错误处理
- [x] encode 有 try-catch
- [x] decode 有 try-catch
- [x] 失败时返回 null 而不是崩溃

## 下一步行动

### 1. 测试并收集日志
使用 `build/libs/ae2craftinglens-1.0.2.jar` 进行测试，并收集：
- 完整的错误堆栈
- AE2 版本号
- 游戏启动日志的前 100 行

### 2. 分析日志
根据日志确定：
- 是哪个方法不存在
- AE2 的具体版本
- 是否有类加载问题

### 3. 选择正确的方案
根据日志选择：
- 如果 `readFromPacket` 不存在 → 使用方案 A（通过 AEKeyType）
- 如果方法被混淆 → 使用 AT 或直接调用
- 如果类加载有问题 → 检查依赖

### 4. 实施修复
根据选择的方案修改代码并重新测试。

## 联系信息

如果问题持续，请提供以下信息寻求帮助：
1. 完整的错误日志（Pastebin 或 GitHub Gist）
2. AE2 版本号
3. Forge 版本号
4. 模组文件 `build/libs/ae2craftinglens-1.0.2.jar`

## 总结

当前代码已经实现了正确的 encode/decode 顺序和错误处理。如果仍然报错，最可能的原因是：

1. **AE2 版本差异**：不同版本的 AE2 使用不同的 API
2. **混淆问题**：生产环境的方法名与开发环境不同
3. **方法位置**：方法可能在内部类或其他地方

**下一步**：进行游戏内测试，收集完整日志，根据日志确定具体问题并选择相应的解决方案。
