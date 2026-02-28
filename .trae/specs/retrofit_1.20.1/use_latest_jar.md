# 重要：请使用最新构建的模组文件

## 问题分析

根据日志，游戏仍然在使用旧版本的代码，导致出现 `readFromPacket` 错误。

**日志显示**：
```
java.lang.NoSuchMethodException: appeng.api.stacks.AEKey.readFromPacket(...)
```

这说明游戏加载的是**旧版本**的模组文件，而不是刚刚构建的新版本。

---

## 解决方案

### 步骤 1：找到最新构建的 JAR 文件

**位置**：`build/libs/ae2craftinglens-1.0.2.jar`
**时间戳**：2026/02/28 下午 04:03:54
**文件大小**：55,309 bytes

### 步骤 2：替换游戏模组文件夹中的文件

**Windows 默认路径**：
```
%APPDATA%\.minecraft\mods\
```

**操作步骤**：
1. 关闭游戏
2. 打开 `%APPDATA%\.minecraft\mods\` 文件夹
3. **删除**旧的 `ae2craftinglens-1.0.2.jar`
4. **复制**新的 `build/libs/ae2craftinglens-1.0.2.jar` 到 mods 文件夹
5. 重新启动游戏

### 步骤 3：验证版本

启动游戏后，检查日志应该看到：
```
[Render thread/INFO] [AE2CraftingLens/]: Using AEKey.fromPacket for AEKey deserialization (1.20.1 method)
```

**而不是**：
```
[Render thread/ERROR] [AE2CraftingLens/]: Failed to initialize AEKey reflection methods
java.lang.NoSuchMethodException: appeng.api.stacks.AEKey.readFromPacket(...)
```

---

## 快速复制命令

### PowerShell（一键复制）

```powershell
# 关闭游戏后，在项目中运行此命令
Copy-Item -Path "build\libs\ae2craftinglens-1.0.2.jar" -Destination "$env:APPDATA\.minecraft\mods\" -Force
Write-Host "模组文件已复制到 %APPDATA%\.minecraft\mods\" -ForegroundColor Green
```

### 批处理文件

创建 `copy_to_mods.bat`：
```batch
@echo off
copy /Y build\libs\ae2craftinglens-1.0.2.jar %APPDATA%\.minecraft\mods\
echo 模组文件已复制！
pause
```

---

## 验证清单

### 构建验证
- [x] 运行 `.\gradlew clean build`
- [x] 检查构建结果：`BUILD SUCCESSFUL`
- [x] 检查 JAR 文件时间戳：应该是最新的

### 游戏验证
- [ ] 关闭游戏
- [ ] 删除旧模组文件
- [ ] 复制新模组文件
- [ ] 启动游戏
- [ ] 检查日志，应该看到：
  - ✅ "Using AEKey.fromPacket for AEKey deserialization"
  - ❌ **不应该看到** "NoSuchMethodException: AEKey.readFromPacket"

### 功能验证
- [ ] 打开 AE2 Crafting Status Screen
- [ ] Shift+Left-click 点击合成任务
- [ ] 观察日志：应该看到 "Extracted AEKey: ..."
- [ ] 检查 Pattern Provider 高亮框是否正确显示

---

## 常见错误

### 错误 1：没有删除旧文件

**症状**：游戏仍然报错 `readFromPacket`

**原因**：旧的 JAR 文件仍然在 mods 文件夹中

**解决**：
```
1. 关闭游戏
2. 删除 %APPDATA%\.minecraft\mods\ae2craftinglens-1.0.2.jar
3. 复制新的 build\libs\ae2craftinglens-1.0.2.jar
4. 重启游戏
```

### 错误 2：复制到错误的文件夹

**症状**：游戏没有加载模组

**原因**：复制到了错误的 mods 文件夹

**解决**：确保复制到正确的路径：
```
%APPDATA%\.minecraft\mods\
```

**注意**：如果你使用多实例启动器（如 HMCL、PCL），每个实例有独立的 mods 文件夹。

### 错误 3：游戏缓存未清除

**症状**：即使替换了文件，仍然使用旧版本

**原因**：Forge 可能缓存了模组文件

**解决**：
```
1. 关闭游戏
2. 删除 %APPDATA%\.minecraft\.forge 文件夹
3. 删除 %APPDATA%\.minecraft\mods 文件夹中的缓存文件
4. 重新复制新模组文件
5. 重启游戏
```

---

## 日志对比

### 旧版本（错误）
```
[Render thread/ERROR] [AE2CraftingLens/]: Failed to initialize AEKey reflection methods
java.lang.NoSuchMethodException: appeng.api.stacks.AEKey.readFromPacket(net.minecraft.network.FriendlyByteBuf)
[Render thread/ERROR] [AE2CraftingLens/]: WRITE_METHOD not initialized, cannot encode AEKey
[Server thread/INFO] [AE2CraftingLens/]: Processing response packet with 0 dimensions
未找到 Pattern Provider
```

### 新版本（正确）
```
[Render thread/INFO] [AE2CraftingLens/]: Using AEKey.fromPacket for AEKey deserialization (1.20.1 method)
[Render thread/INFO] [AE2CraftingLens/]: AEKey reflection methods initialized successfully
[Render thread/INFO] [AE2CraftingLens/]: Sending RequestPatternProvidersPacket with AEKey: ..., rowIndex: 0
[Server thread/INFO] [AE2CraftingLens/]: Received pattern provider request for AEKey: ...
[Server thread/INFO] [AE2CraftingLens/]: Found 4 pattern providers for requested AEKey
[Render thread/INFO] [AE2CraftingLens/]: Received 4 provider positions
```

---

## 总结

**关键步骤**：
1. ✅ 构建新版本的 JAR 文件
2. ✅ **删除**旧版本的模组文件
3. ✅ **复制**新版本的 JAR 文件到 mods 文件夹
4. ✅ 重启游戏并验证日志

**记住**：构建成功只是第一步，**必须替换游戏中的模组文件才能生效**！

如果替换后仍然有问题，请提供完整的游戏日志。
