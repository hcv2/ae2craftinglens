# AE2 Crafting Lens - AEKey 序列化 API 二次修复计划

## 问题描述
第一次修复尝试使用 `AEKey.toPacket()` 方法，但该方法在 AE2 1.20.1 中也不存在。错误日志显示：
```
java.lang.NoSuchMethodException: appeng.api.stacks.AEKey.toPacket(net.minecraft.network.FriendlyByteBuf)
```

## 根本原因分析
AE2 1.20.1 中的 AEKey 序列化可能需要通过特定的工具类或方法，而不是直接在 AEKey 上调用方法。根据 AE2 的设计模式，可能需要：
1. 使用 `AEKey` 的 `writeToPacket(FriendlyByteBuf)` 实例方法（注意方法名可能不同）
2. 使用 AE2 提供的工具类进行序列化
3. 使用 NBT 标签进行序列化

## 解决方案

### 方案 1：使用 AEKey 的 writeToPacket 方法（如果存在）
尝试调用 `writeToPacket` 而不是 `toPacket`。

### 方案 2：使用 NBT 序列化
AEKey 可能支持通过 NBT 标签进行序列化：
- 编码：`aeKey.writeToNBT(new CompoundTag())`
- 解码：`AEKey.fromNBT(tag)`

### 方案 3：使用 AE2 的工具类
AE2 可能提供了专门的工具类来处理 AEKey 的网络序列化。

### 推荐方案：使用 AEKey 的 `writeToPacket` 和 `AEKey.fromPacket`
根据 AE2 的源码和常见用法，AEKey 应该有：
- 实例方法：`writeToPacket(FriendlyByteBuf buffer)` - 将 key 写入缓冲区
- 静态方法：`fromPacket(FriendlyByteBuf buffer)` - 从缓冲区读取 key（可能需要通过 AEKeyType）

## 实施步骤

1. **修改 encode 方法**
   - 尝试调用 `writeToPacket` 方法而不是 `toPacket`

2. **修改 decode 方法**
   - 使用正确的方法从缓冲区读取 AEKey

3. **重新构建并测试**

## 注意事项
- 如果 AE2 版本不同，API 可能会有所变化
- 可能需要查看 AE2 1.20.1 的具体源码来确定正确的方法名
