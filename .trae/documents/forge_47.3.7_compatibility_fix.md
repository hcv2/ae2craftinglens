# AE2 Crafting Lens - Forge 47.3.7 兼容性修复计划

## 问题描述
模组在 Forge 47.3.7 中加载失败，错误信息为：
```
java.lang.NoSuchMethodError: 'void net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.registerConfig(net.minecraftforge.fml.config.ModConfig$Type, net.minecraftforge.fml.config.IConfigSpec)'
```

**根本原因**：在 Forge 47.3.7（Minecraft 1.20.1）中，`FMLJavaModLoadingContext` 类**没有** `registerConfig()` 方法。正确的方法应该是使用 `ModLoadingContext.get().registerConfig()`。

## 解决方案

### 1. 修改 AE2CraftingLens.java
将错误的 API 调用：
```java
FMLJavaModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
```

修改为正确的 API 调用：
```java
ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
```

### 2. 添加必要的导入
在 AE2CraftingLens.java 中添加：
```java
import net.minecraftforge.fml.ModLoadingContext;
```

### 3. 验证兼容性
- 重新构建模组
- 测试在 Forge 47.3.7 中的加载
- 确保配置功能正常工作

## 实施步骤

1. **修改 AE2CraftingLens.java**
   - 添加 `ModLoadingContext` 导入
   - 修改 `registerConfig` 调用

2. **重新构建**
   - 运行 `./gradlew clean build`
   - 验证构建成功

3. **测试验证**
   - 在 Forge 47.3.7 环境中测试模组加载
   - 确认配置功能正常

## 参考资料
- 根据 CSDN 博客和 Forge 模组开发教程，在 Minecraft 1.19.2/1.20.1 Forge 中，配置注册应使用 `ModLoadingContext.get().registerConfig()`
- 参考链接：
  - https://blog.csdn.net/Jay_fearless/article/details/128395459
  - https://blog.csdn.net/uicdb/article/details/122350444
