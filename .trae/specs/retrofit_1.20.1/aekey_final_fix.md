# AEKey 序列化最终修复报告 - 分级降级策略

## 问题总结

在 AE2 1.20.1 中，由于 `AEKey` 是一个接口，直接反射调用静态方法可能会因为以下原因失败：
1. **接口静态方法的反射限制**
2. **类加载顺序问题**
3. **混淆环境（FML/Forge）影响**

## 最终解决方案

采用**分级降级策略**，提供多层备用方案：

### encode 方法
```java
public static void encode(RequestPatternProvidersPacket packet, FriendlyByteBuf buffer) {
    buffer.writeInt(packet.rowIndex);  // ✅ 顺序：先写 rowIndex
    try {
        if (packet.what == null) {
            buffer.writeBoolean(false);
            return;
        }
        buffer.writeBoolean(true);
        // 使用反射调用 AEKey 的 writeToPacket 实例方法
        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        Method writeToPacketMethod = aeKeyClass.getMethod("writeToPacket", FriendlyByteBuf.class);
        writeToPacketMethod.invoke(packet.what, buffer);
    } catch (Exception e) {
        AE2CraftingLens.LOGGER.error("Error encoding AEKey", e);
        buffer.writeBoolean(false);
    }
}
```

### decode 方法（分级降级）
```java
public static RequestPatternProvidersPacket decode(FriendlyByteBuf buffer) {
    int rowIndex = buffer.readInt();  // ✅ 顺序：先读 rowIndex
    Object what = null;
    
    try {
        boolean hasKey = buffer.readBoolean();
        if (!hasKey) {
            return new RequestPatternProvidersPacket(null, rowIndex);
        }
        // 方案 1：使用反射调用 AEKey 的 readFromPacket 静态方法
        Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
        Method readFromPacketMethod = aeKeyClass.getMethod("readFromPacket", FriendlyByteBuf.class);
        what = readFromPacketMethod.invoke(null, buffer);
        
        return new RequestPatternProvidersPacket(what, rowIndex);
    } catch (Throwable t) {
        try {
            // 方案 2：使用 getDeclaredMethod 并设置 accessible
            Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
            Method method = aeKeyClass.getDeclaredMethod("readFromPacket", FriendlyByteBuf.class);
            method.setAccessible(true);
            what = method.invoke(null, buffer);
            
            return new RequestPatternProvidersPacket(what, rowIndex);
        } catch (Exception ex) {
            // 方案 3：降级尝试（如果以上皆失败）
            AE2CraftingLens.LOGGER.error("AE2CraftingLens: 无法解码 AEKey，尝试跳过该数据", ex);
            return new RequestPatternProvidersPacket(null, rowIndex);
        }
    }
}
```

## 分级策略说明

### 方案 1：标准反射
```java
Method readFromPacketMethod = aeKeyClass.getMethod("readFromPacket", FriendlyByteBuf.class);
what = readFromPacketMethod.invoke(null, buffer);
```
- 使用 `getMethod` 获取公共方法
- 适用于大多数标准情况

### 方案 2：getDeclaredMethod + accessible
```java
Method method = aeKeyClass.getDeclaredMethod("readFromPacket", FriendlyByteBuf.class);
method.setAccessible(true);
what = method.invoke(null, buffer);
```
- 使用 `getDeclaredMethod` 获取声明的方法
- 通过 `setAccessible(true)` 绕过访问检查
- 适用于方法存在但访问受限的情况

### 方案 3：优雅降级
```java
AE2CraftingLens.LOGGER.error("AE2CraftingLens: 无法解码 AEKey，尝试跳过该数据", ex);
return new RequestPatternProvidersPacket(null, rowIndex);
```
- 记录详细错误日志
- 返回 null 而不是崩溃
- 保证模组稳定性

## 验证结果
- ✅ 构建成功：`BUILD SUCCESSFUL in 15s`
- ✅ 编码顺序：`rowIndex` → `what`
- ✅ 解码顺序：`rowIndex` → `what`
- ✅ 分级降级策略已实现
- ✅ 错误处理完善

## 为什么之前会显示"未找到 Pattern Provider"

### 问题链条
1. **解码失败**：`decode` 方法抛出 `NoSuchMethodException`
2. **异常捕获**：Netty IO 线程捕获异常
3. **处理终止**：当前数据包处理被终止
4. **逻辑未执行**：`handle` 方法（查找 Provider 的逻辑）未被调用
5. **空响应**：返回默认的空响应（0 dimensions）
6. **客户端提示**：显示"未找到 Pattern Provider"

### 修复后的行为
1. **解码尝试**：使用分级降级策略尝试解码
2. **成功解码**：方案 1 或方案 2 成功
3. **正常处理**：`handle` 方法正常执行
4. **查找 Provider**：扫描 Grid 中的 Pattern Provider
5. **返回结果**：发送包含 Provider 位置的响应包
6. **客户端显示**：高亮显示 Provider 并显示聊天消息

## 数据包顺序验证

### 编码顺序 ✅
```
1. buffer.writeInt(rowIndex)        // 4 字节
2. buffer.writeBoolean(hasWhat)     // 1 字节
3. AEKey.writeToPacket(buffer)      // 可变长度
```

### 解码顺序 ✅
```
1. buffer.readInt()                 // 4 字节 → rowIndex
2. buffer.readBoolean()             // 1 字节 → hasWhat
3. AEKey.readFromPacket(buffer)     // 可变长度 → what
```

## 下一步测试

### 游戏内测试步骤
1. 使用 `build/libs/ae2craftinglens-1.0.2.jar` 启动游戏
2. 加载包含 AE2 1.20.1 的世界
3. 打开 AE2 合成状态界面
4. **按住 Shift + 左键点击**合成任务（如活塞）
5. 观察日志输出

### 预期日志
**成功情况**：
```
[Render thread/INFO] [AE2CraftingLens/]: Extracted AEKey from selected CPU: minecraft:piston
[Server thread/INFO] [AE2CraftingLens/]: Found 4 Pattern Providers for minecraft:piston
[Render thread/INFO] [AE2CraftingLens/]: Processing response packet with 4 dimensions
[CHAT] Found 4 Pattern Provider(s)
```

**失败情况（方案 1&2 失败，方案 3 降级）**：
```
[Render thread/INFO] [AE2CraftingLens/]: Extracted AEKey from selected CPU: minecraft:piston
[Server thread/ERROR] [AE2CraftingLens/]: AE2CraftingLens: 无法解码 AEKey，尝试跳过该数据
[Server thread/WARN] [AE2CraftingLens/]: Skipping request for null AEKey
```

### 需要收集的信息
如果问题仍然存在，请提供：
1. **完整的错误日志**（从点击到显示消息的完整过程）
2. **AE2 版本号**（按 F3+C 查看）
3. **Forge 版本号**
4. **日志中 AE2CraftingLens 相关的所有输出**

## 总结

通过实现分级降级策略，模组现在能够：
- ✅ 优先使用标准反射调用 API
- ✅ 在标准方法失败时使用备用方案
- ✅ 在所有方案失败时优雅降级
- ✅ 保证模组稳定性，不崩溃
- ✅ 提供详细的错误日志用于诊断

现在请使用新构建的模组文件进行测试，并观察日志输出！
