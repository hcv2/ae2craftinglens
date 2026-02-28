# AE2 15.4.10 序列化方法深度分析

## 问题现状

根据最新日志，所有三种方法都失败了：

```
尝试 1: AEKey.fromPacket() ❌ NoSuchMethodException
尝试 2: AEKeyTypes.read() ❌ NoSuchMethodException  
尝试 3: AEKey.readFromPacket() ❌ NoSuchMethodException
```

这说明 AE2 15.4.10 使用了**完全不同的序列化机制**。

## AE2 15.4.10 的可能情况

### 情况 1：使用特定类型的序列化器

AE2 15.4.10 可能不使用通用的 `AEKey` 序列化，而是使用具体类型的序列化器：

- `AEItemKey` (物品)
- `AEFluidKey` (流体)
- `AEGasKey` (气体，如果有)

**可能的 API**：
```java
// 具体类型的序列化
AEItemKey.fromPacket(buffer)
AEItemKey.writeToPacket(buffer)
```

### 情况 2：使用 AEKey 的 getType() 方法

AE2 可能通过 `AEKeyType` 来管理序列化：

```java
// 伪代码
AEKeyType type = key.getType();
type.write(key, buffer);  // 写入
type.read(buffer);        // 读取
```

### 情况 3：使用接口默认方法

AE2 15.4.10 可能使用 Java 8 的接口默认方法，这些方法不会在 `getMethod()` 中显示。

## 调试方案

### 方案 1：打印 AEKey 的实际类

修改代码，在发送前打印 AEKey 的实际类型：

```java
AE2CraftingLens.LOGGER.info("AEKey class: {}", packet.what.getClass().getName());
AE2CraftingLens.LOGGER.info("AEKey interfaces: {}", Arrays.toString(packet.what.getClass().getInterfaces()));
```

### 方案 2：枚举所有可用方法

在静态块中添加调试代码：

```java
Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
AE2CraftingLens.LOGGER.info("AEKey methods:");
for (Method m : aeKeyClass.getMethods()) {
    if (m.getName().contains("Packet") || m.getName().contains("packet")) {
        AE2CraftingLens.LOGGER.info("  - {} {}", m.getName(), Arrays.toString(m.getParameterTypes()));
    }
}
```

### 方案 3：检查 AEKeyTypes

```java
Class<?> aeKeyTypesClass = Class.forName("appeng.api.stacks.AEKeyTypes");
AE2CraftingLens.LOGGER.info("AEKeyTypes methods:");
for (Method m : aeKeyTypesClass.getMethods()) {
    AE2CraftingLens.LOGGER.info("  - {} {}", m.getName(), Arrays.toString(m.getParameterTypes()));
}
```

## 临时解决方案

### 方案 A：使用反射调用实例方法

如果 `fromPacket` 是实例方法而不是静态方法：

```java
// 不创建新的 AEKey，而是复制现有的
Class<?> aeKeyClass = packet.what.getClass();
Method copyMethod = aeKeyClass.getMethod("copy");
Object copy = copyMethod.invoke(packet.what);
// 然后序列化 copy
```

### 方案 B：直接写入 AEKey 的数据

如果 AEKey 有 `getWhat()` 或类似方法：

```java
// 获取底层数据
Object wrapped = aeKey.getWhat();
// 序列化包装的数据
if (wrapped instanceof ItemStack) {
    buffer.writeItem((ItemStack) wrapped);
}
```

### 方案 C：使用 AE2 的内部 API

AE2 可能有内部的序列化辅助类：

```java
// 可能的类
appeng.util.ByteBufUtils
appeng.api.implementations.api.APIUtils
```

## 建议的下一步

1. **添加调试日志**：打印 AEKey 的实际类型和可用方法
2. **检查 AE2 源码**：查看 15.4.10 版本的 AEKey.java
3. **尝试具体类型**：如果是 AEItemKey，尝试调用其特定的序列化方法
4. **联系 AE2 开发者**：询问 15.4.10 的正确序列化方式

## AE2 源码参考

- AE2 15.4.10 源码：https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/15.4.10
- AEKey.java: `appeng/api/stacks/AEKey.java`
- AEItemKey.java: `appeng/api/stacks/AEItemKey.java`

## 可能的突破点

根据日志，GUI 交互成功提取了 AEKey：
```
Extracted AEKey from selected CPU 1: minecraft:piston
```

这说明 AEKey 对象是存在的，只是序列化方式不同。

**关键问题**：AE2 15.4.10 如何在网络中传输 AEKey？

**可能的答案**：
1. 使用具体的子类（如 AEItemKey）的序列化方法
2. 使用 AEKeyTypes 注册表
3. 使用 ByteBuf 扩展方法
4. 使用 NBT 作为中间格式

需要进一步调试来确定正确的方法。
