# AE2 Crafting Lens - AEKey 序列化 API 适配修复计划

## 问题描述
模组在运行时出现 AEKey 序列化错误：
- **客户端编码错误**：`java.lang.NoSuchMethodException: appeng.api.stacks.AEKey.write(net.minecraft.network.FriendlyByteBuf,appeng.api.stacks.AEKey)`
- **服务端解码错误**：`java.lang.NoSuchMethodException: appeng.api.stacks.AEKey.read(net.minecraft.network.FriendlyByteBuf)`

**根本原因**：在 AE2 1.20.1 版本中，AEKey 类**没有**静态的 `write()` 和 `read()` 方法。正确的序列化方式是：
- **编码**：使用 AEKey 实例的 `toPacket(FriendlyByteBuf)` 方法
- **解码**：首先读取 AEKeyType，然后使用该类型的 `fromPacket(FriendlyByteBuf)` 方法

## 解决方案

### 修改 RequestPatternProvidersPacket.java

#### 1. 修改 encode 方法（第 28-43 行）

**当前错误代码**：
```java
public static void encode(RequestPatternProvidersPacket packet, FriendlyByteBuf buffer) {
    buffer.writeInt(packet.rowIndex);
    try {
        if (packet.what == null) {
            buffer.writeBoolean(false);
            return;
        }
        buffer.writeBoolean(true);
        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        Method writeKeyMethod = aeKeyClass.getMethod("write", FriendlyByteBuf.class, aeKeyClass);
        writeKeyMethod.invoke(null, buffer, packet.what);
    } catch (Exception e) {
        AE2CraftingLens.LOGGER.error("Error encoding AEKey", e);
        buffer.writeBoolean(false);
    }
}
```

**修改为**：
```java
public static void encode(RequestPatternProvidersPacket packet, FriendlyByteBuf buffer) {
    buffer.writeInt(packet.rowIndex);
    try {
        if (packet.what == null) {
            buffer.writeBoolean(false);
            return;
        }
        buffer.writeBoolean(true);
        // 使用 AEKey 的 toPacket 实例方法
        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        Method toPacketMethod = aeKeyClass.getMethod("toPacket", FriendlyByteBuf.class);
        toPacketMethod.invoke(packet.what, buffer);
    } catch (Exception e) {
        AE2CraftingLens.LOGGER.error("Error encoding AEKey", e);
        buffer.writeBoolean(false);
    }
}
```

#### 2. 修改 decode 方法（第 45-60 行）

**当前错误代码**：
```java
public static RequestPatternProvidersPacket decode(FriendlyByteBuf buffer) {
    int rowIndex = buffer.readInt();
    try {
        boolean hasKey = buffer.readBoolean();
        if (!hasKey) {
            return new RequestPatternProvidersPacket(null, rowIndex);
        }
        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        Method readKeyMethod = aeKeyClass.getMethod("read", FriendlyByteBuf.class);
        Object what = readKeyMethod.invoke(null, buffer);
        return new RequestPatternProvidersPacket(what, rowIndex);
    } catch (Exception e) {
        AE2CraftingLens.LOGGER.error("Error decoding AEKey", e);
        return new RequestPatternProvidersPacket(null, rowIndex);
    }
}
```

**修改为**：
```java
public static RequestPatternProvidersPacket decode(FriendlyByteBuf buffer) {
    int rowIndex = buffer.readInt();
    try {
        boolean hasKey = buffer.readBoolean();
        if (!hasKey) {
            return new RequestPatternProvidersPacket(null, rowIndex);
        }
        // 首先读取 AEKeyType
        Class<?> keyTypeClass = Class.forName("appeng.api.stacks.AEKeyType");
        Method readTypeMethod = keyTypeClass.getMethod("fromPacket", FriendlyByteBuf.class);
        Object keyType = readTypeMethod.invoke(null, buffer);
        
        // 然后使用该类型的 fromPacket 方法读取具体的 AEKey
        Method fromPacketMethod = keyTypeClass.getMethod("fromPacket", FriendlyByteBuf.class);
        Object what = fromPacketMethod.invoke(keyType, buffer);
        
        return new RequestPatternProvidersPacket(what, rowIndex);
    } catch (Exception e) {
        AE2CraftingLens.LOGGER.error("Error decoding AEKey", e);
        return new RequestPatternProvidersPacket(null, rowIndex);
    }
}
```

## 实施步骤

1. **修改 RequestPatternProvidersPacket.java**
   - 修改 `encode` 方法，使用 `toPacket()` 实例方法
   - 修改 `decode` 方法，使用 `AEKeyType.fromPacket()` 方法

2. **重新构建**
   - 运行 `./gradlew clean build`
   - 验证构建成功

3. **测试验证**
   - 在游戏中测试 AE2 Crafting Lens 功能
   - 确认 Pattern Provider 能够正常显示
   - 确认不再有 NoSuchMethodException 错误

## 注意事项
- AEKey 是一个接口/抽象类，具体的实现类由 AEKeyType 决定
- 序列化时需要先写入/读取类型信息，然后才能正确序列化/反序列化具体的 Key
- 如果 AE2 版本不同，API 可能会有所变化

## 参考资料
- AE2 官方源码：https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/forge/1.20.1
