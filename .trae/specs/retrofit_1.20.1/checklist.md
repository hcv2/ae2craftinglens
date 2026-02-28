# Verification Checklist - 从 1.21.1 (NeoForge) 回退适配到 1.20.1 (Forge)

## Access Transformer 配置
- [x] `src/main/resources/META-INF/accesstransformer.cfg` 文件存在
- [x] AT 配置包含 `CraftingCPUCluster.logic`
- [x] AT 配置包含 `CraftingLogic.job`
- [x] AT 配置包含 `CraftingJob.tasks`
- [x] AT 配置包含 `CraftingNode.getPattern()`

## 网络系统适配
- [x] NetworkHandler 使用 `NetworkRegistry.newSimpleChannel`
- [x] 通道 ID 为 `ae2craftinglens:main`
- [x] 协议版本为 "1"
- [x] 所有 Packet 类都有 encode/decode/handle 方法

## AEKey 序列化
- [x] `RequestPatternProvidersPacket.encode()` 使用 `AEKey.writeToPacket()`
- [x] `RequestPatternProvidersPacket.decode()` 使用 `AEKey.fromPacket()`
- [x] 通过反射调用方法

## 主线程同步
- [x] 所有 handle 方法都使用 `context.enqueueWork()`

## 渲染层修复
- [x] `PatternProviderHighlightRenderer` 计算相机坐标
- [x] 从 PoseStack 减去相机坐标
- [x] 使用 AFTER_PARTICLES 渲染阶段

## 构建配置
- [x] `gradle.properties` 中 Minecraft 版本为 `1.20.1`
- [x] `gradle.properties` 中 Forge 版本为 `47.4.10`
- [x] `gradle.properties` 中使用 `official` 映射
- [x] `mods.toml` 中 Forge 版本范围为 `[47.3,)`
- [x] `mods.toml` 中 AE2 版本范围为 `[15.2.0,)`
- [x] `mods.toml` 中 Minecraft 版本范围为 `[1.20.1,1.21)`

## 代码质量
- [x] 构建成功无错误
- [ ] 游戏启动无崩溃（待测试）
- [ ] 模组功能正常工作（待测试）
- [ ] 日志中无 NoSuchFieldException（待测试）
- [ ] 日志中无 NoSuchMethodException（待测试）
- [ ] 高亮框正确显示在世界坐标（待测试）

## 待测试项目（游戏内）
- [ ] 启动游戏并加载包含 AE2 的世界
- [ ] 打开 AE2 合成状态界面
- [ ] Shift+ 点击合成任务
- [ ] 检查 Pattern Provider 高亮显示
- [ ] 检查聊天栏消息
- [ ] 检查日志无错误
