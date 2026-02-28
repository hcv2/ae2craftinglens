# IDE 错误解决指南

## 问题描述

IDE 报告了大量关于 `AEKey cannot be resolved to a type` 的编译错误，但 Gradle 构建成功。

## 根本原因

这是 **IDE 与 Gradle 依赖不同步** 的问题：

- ✅ **Gradle 构建成功**：说明代码正确，AE2 依赖已正确配置
- ❌ **IDE 报错**：说明 IDE 还没有刷新 Gradle 依赖缓存

## 解决方案

### 方法 1：刷新 Gradle 项目（推荐）

#### IntelliJ IDEA
1. 右键点击 `build.gradle` 文件
2. 选择 **"Reload Gradle Project"** 或 **"Refresh Gradle Project"**
3. 等待 IDE 重新导入依赖

#### Eclipse
1. 右键点击项目
2. 选择 **"Gradle" → "Refresh Gradle Project"**
3. 等待 IDE 重新导入依赖

#### VS Code
1. 按 `Ctrl+Shift+P` 打开命令面板
2. 输入 **"Java: Reload Project"** 并执行
3. 或者重启 VS Code

### 方法 2：清理并重新构建

在终端中执行：
```bash
.\gradlew clean build --refresh-dependencies
```

这会强制 Gradle 重新下载所有依赖并清理缓存。

### 方法 3：重启 IDE

如果上述方法都不起作用，尝试完全关闭并重新打开 IDE。

## 验证方法

### 1. 检查 Gradle 构建
```bash
.\gradlew build
```

应该看到：
```
BUILD SUCCESSFUL
```

### 2. 检查 JAR 文件
查看 `build/libs/ae2craftinglens-1.0.2.jar` 是否存在。

### 3. 检查 IDE 错误
刷新后，IDE 应该不再报告 AEKey 相关的错误。

## 当前构建状态

```
BUILD SUCCESSFUL in 1m 6s
8 actionable tasks: 8 executed
```

**输出文件**：`build/libs/ae2craftinglens-1.0.2.jar`

**警告**：4 个已过时 API 警告（不影响功能）

## 为什么会出现这种情况？

### Gradle vs IDE

- **Gradle**：使用自己的依赖缓存和类路径
- **IDE**：维护独立的索引和类路径配置

当你修改了 `build.gradle` 后：
1. Gradle 会立即下载依赖并用于构建
2. IDE 不会自动检测到这些变化
3. 需要手动刷新 IDE 的 Gradle 配置

### 时间线

```
T0: 修改 build.gradle 添加 AE2 依赖
    ↓
T1: Gradle 下载依赖到本地缓存
    ↓
T2: Gradle 构建成功 ✅
    ↓
T3: IDE 仍然使用旧的类路径配置
    ↓
T4: IDE 报告 "AEKey cannot be resolved" ❌
    ↓
T5: 刷新 Gradle 项目
    ↓
T6: IDE 更新类路径，错误消失 ✅
```

## 预防措施

### 1. 自动刷新（推荐）

在 IDE 设置中启用自动刷新 Gradle 项目：

**IntelliJ IDEA**:
- Settings → Build, Execution, Deployment → Build Tools → Gradle
- 勾选 "Reload project after changes in the build scripts"

**VS Code**:
- 在设置中搜索 `java.gradle.launchOnSave`
- 设置为 `true`

### 2. 定期检查

每次修改 `build.gradle` 后，立即刷新 Gradle 项目。

### 3. 使用 Gradle Wrapper

始终使用 `gradlew` 或 `gradlew.bat` 而不是系统安装的 Gradle。

## 常见误区

### ❌ 误区 1：IDE 报错 = 代码错误

**事实**：IDE 报错可能是因为配置不同步，不代表代码真的有错误。

**验证方法**：运行 `gradlew build` 检查实际编译结果。

### ❌ 误区 2：需要手动添加 JAR 到类路径

**事实**：Gradle 会自动处理依赖，手动添加会导致冲突。

**正确做法**：只在 `build.gradle` 中配置依赖。

### ❌ 误区 3：重启 IDE 是唯一解决方法

**事实**：大多数情况下，刷新 Gradle 项目就足够了。

**推荐**：先尝试刷新 Gradle，不行再重启 IDE。

## 技术细节

### AE2 依赖配置

```gradle
repositories {
    maven {
        name = "Modmaven"
        url = "https://modmaven.dev/"
        content {
            includeGroup "appeng"
        }
    }
}

dependencies {
    compileOnly fg.deobf("appeng:appliedenergistics2-forge:15.4.10")
    runtimeOnly fg.deobf("appeng:appliedenergistics2-forge:15.4.10")
}
```

### IDE 类路径生成

IDE 会读取 Gradle 生成的 `.gradle` 文件夹中的配置，然后更新自己的类路径：

```
build.gradle (配置)
    ↓
Gradle 解析
    ↓
.gradle/ (Gradle 缓存)
    ↓
IDE 读取并更新类路径
    ↓
IDE 索引更新
    ↓
错误消失
```

## 总结

**当前状态**：
- ✅ 代码正确
- ✅ Gradle 配置正确
- ✅ 构建成功
- ⚠️ IDE 需要刷新

**下一步**：
1. 刷新 Gradle 项目（方法见上）
2. 等待 IDE 重新索引
3. 错误应该消失
4. 如果仍有问题，重启 IDE

**记住**：Gradle 构建成功是最重要的指标，IDE 错误通常只是同步问题。
