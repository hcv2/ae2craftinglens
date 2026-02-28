# AE2 Crafting Lens 与 AE2 官方库的依赖关系分析

## 概述
AE2 Crafting Lens 完全依赖于 AE2（Applied Energistics 2）的内部 API 实现。本模组通过反射访问 AE2 的类、字段和方法，实现了与 AE2 的深度集成。

## 依赖方式

### 1. 反射访问（主要方式）
本项目**不使用编译时依赖**，而是通过反射在运行时访问 AE2 的内部 API。

**优点**：
- 不需要在编译时引入 AE2 作为依赖
- 可以访问 AE2 的非公开 API
- 更灵活的版本适配

**缺点**：
- 性能开销较大
- 类型安全性差
- 容易在 AE2 版本更新时失效
- 需要 Access Transformer 来访问某些字段

### 2. Access Transformer
为了访问 AE2 的私有字段和方法，项目在 `META-INF/accesstransformer.cfg` 中声明了访问权限。

## 核心 AE2 类依赖

### 按包分类

#### 1. `appeng.api.stacks` - 物品/流体栈 API

##### `AEKey`
**用途**：表示 AE2 系统中的物品/流体标识
**使用位置**：
- `RequestPatternProvidersPacket.java` - 网络包序列化/反序列化
- `CraftingScreenHandler.java` - 从界面提取合成物品
- `PatternProviderRequestHandler.java` - 匹配 Pattern Provider

**关键方法**：
```java
// 实例方法 - 序列化到网络包
void writeToPacket(FriendlyByteBuf buffer)

// 静态方法 - 从网络包反序列化（通过 AEKeyType）
static AEKey fromPacket(FriendlyByteBuf buffer)
```

##### `AEKeyType`
**用途**：AEKey 的类型标识
**使用位置**：
- `RequestPatternProvidersPacket.java` - 反序列化 AEKey

**关键方法**：
```java
static AEKey fromPacket(FriendlyByteBuf buffer)
```

##### `GenericStack`
**用途**：包装 AEKey 和数量的容器
**使用位置**：
- `CraftingScreenHandler.java` - 从 UI 提取物品信息
- `PatternProviderRequestHandler.java` - 检查 Pattern 的输入/输出

**关键字段/方法**：
```java
AEKey what()  // 获取 AEKey
long amount() // 获取数量
```

#### 2. `appeng.api.crafting` - 合成 API

##### `IPatternDetails`
**用途**：表示 Pattern Provider 中的样板详情
**使用位置**：
- `PatternProviderRequestHandler.java` - 检查 Pattern 是否能处理目标物品

**关键方法**：
```java
AEKey[] getInputs()   // 获取输入物品
AEKey getOutput()     // 获取输出物品
```

#### 3. `appeng.me.cluster.implementations` - 网络集群实现

##### `CraftingCPUCluster`
**用途**：AE2 合成 CPU 集群，管理合成任务
**使用位置**：
- `PatternProviderRequestHandler.java` - 查找当前合成任务的 Pattern Provider

**Access Transformer**：
```
public appeng.me.cluster.implementations.CraftingCPUCluster logic
public appeng.me.cluster.implementations.CraftingCPUCluster f_legacy_logic_
```

**关键字段**：
- `logic` - CraftingCpuLogic，合成 CPU 逻辑
- `f_legacy_logic_` - 旧版逻辑字段

#### 4. `appeng.crafting.execution` - 合成执行

##### `CraftingCpuLogic`
**用途**：合成 CPU 的核心逻辑
**使用位置**：
- `PatternProviderRequestHandler.java` - 获取当前合成任务

**Access Transformer**：
```
public appeng.crafting.execution.CraftingCpuLogic job
```

**关键字段**：
- `job` - ExecutingCraftingJob，当前执行的合成任务

##### `ExecutingCraftingJob`
**用途**：正在执行的合成任务
**使用位置**：
- `PatternProviderRequestHandler.java` - 获取合成任务列表

**Access Transformer**：
```
public appeng.crafting.execution.ExecutingCraftingJob tasks
```

**关键字段**：
- `tasks` - 合成任务列表

#### 5. `appeng.menu.me.crafting` - 合成菜单

##### `CraftingStatusMenu`
**用途**：AE2 合成状态界面的菜单
**使用位置**：
- `CraftingScreenHandler.java` - 从玩家容器菜单获取
- `PatternProviderRequestHandler.java` - 查找 Grid

**Access Transformer**：
```
public appeng.menu.me.crafting.CraftingStatusMenu selectedCpu
```

**关键字段**：
- `selectedCpu` - 选中的 CPU serial

**关键方法**：
```java
int getSelectedCpuSerial()  // 获取选中的 CPU 序列号
```

##### `CraftingCPUMenu`
**用途**：AE2 CPU 详情菜单
**使用位置**：
- `CraftingScreenHandler.java` - 备用方案获取 AEKey

**Access Transformer**：
```
public appeng.menu.me.crafting.CraftingCPUMenu cpu
```

**关键字段**：
- `cpu` - 当前 CPU 对象

#### 6. `appeng.helpers.patternprovider` - Pattern Provider 辅助

##### `PatternProviderLogic`
**用途**：Pattern Provider 的核心逻辑
**使用位置**：
- `PatternProviderRequestHandler.java` - 检查 Pattern Provider 的能力

**Access Transformer**：
```
public appeng.helpers.patternprovider.PatternProviderLogic host
```

**关键字段**：
- `host` - Pattern Provider 的宿主方块实体

## 使用场景分析

### 场景 1：客户端提取 AEKey

**流程**：
```
CraftingScreenHandler.onMouseDown()
  └─> extractAEKeyFromTable() / extractAEKeyFromSelectedCpu()
      └─> 通过反射访问 CraftingStatusMenu
          └─> 获取 cpuList 字段
              └─> 遍历 CPU 列表
                  └─> 调用 currentJob() 获取 GenericStack
                      └─> 调用 what() 获取 AEKey
```

**涉及的 AE2 类**：
- `CraftingStatusMenu` (菜单)
- `GenericStack` (物品栈)
- `AEKey` (物品标识)

### 场景 2：网络包序列化

**流程**：
```
客户端发送请求
  └─> RequestPatternProvidersPacket.encode()
      └─> 调用 AEKey.writeToPacket()
  
服务器接收请求
  └─> RequestPatternProvidersPacket.decode()
      └─> 调用 AEKeyType.fromPacket()
```

**涉及的 AE2 类**：
- `AEKey` (序列化)
- `AEKeyType` (反序列化)

### 场景 3：服务器查找 Pattern Provider

**流程**：
```
PatternProviderRequestHandler.handle()
  └─> 从 CraftingStatusMenu 获取 Grid
      └─> 获取 CraftingService
          └─> 查找 CraftingCPUCluster
              └─> 访问 CraftingCpuLogic.job
                  └─> 获取 ExecutingCraftingJob.tasks
                      └─> 遍历任务列表
                          └─> 检查 IPatternDetails
                              └─> 匹配 AEKey
                                  └─> 找到 Pattern Provider
```

**涉及的 AE2 类**：
- `CraftingStatusMenu` (菜单)
- `CraftingCPUCluster` (CPU 集群)
- `CraftingCpuLogic` (CPU 逻辑)
- `ExecutingCraftingJob` (合成任务)
- `IPatternDetails` (样板详情)
- `AEKey` (物品匹配)

## 版本兼容性

### 已知兼容的 AE2 版本
- **AE2 15.2.0+** (Forge 1.20.1)
- 最低要求：15.2.0（在 mods.toml 中声明）

### 潜在的版本破坏性变更

#### 1. 类名/包名变更
如果 AE2 重构了包结构或重命名了类，反射调用会失败。

**风险等级**：中
**影响**：`ClassNotFoundException`

#### 2. 字段名变更
如果 AE2 更改了字段名，反射访问会失败。

**风险等级**：高
**影响**：`NoSuchFieldException`

**示例**：
- `CraftingStatusMenu.selectedCpu` → `CraftingStatusMenu.selectedCPU`
- `CraftingCpuLogic.job` → `CraftingCpuLogic.currentJob`

#### 3. 方法签名变更
如果 AE2 更改了方法签名，反射调用会失败。

**风险等级**：高
**影响**：`NoSuchMethodException`

**示例**：
- `GenericStack.what()` 返回类型变更
- `AEKey.writeToPacket()` 参数变更

#### 4. API 结构变更
如果 AE2 重构了内部 API 结构。

**风险等级**：极高
**影响**：整个模组可能无法工作

**示例**：
- `CraftingCPUCluster` 被移除或替换
- `ExecutingCraftingJob.tasks` 结构变更

## Access Transformer 依赖

项目依赖以下 AE2 内部字段的公开访问：

```cfg
# CraftingCPUCluster
public appeng.me.cluster.implementations.CraftingCPUCluster logic
public appeng.me.cluster.implementations.CraftingCPUCluster f_legacy_logic_

# CraftingCpuLogic
public appeng.crafting.execution.CraftingCpuLogic job

# ExecutingCraftingJob
public appeng.crafting.execution.ExecutingCraftingJob tasks

# CraftingStatusMenu
public appeng.menu.me.crafting.CraftingStatusMenu selectedCpu

# CraftingCPUMenu
public appeng.menu.me.crafting.CraftingCPUMenu cpu

# PatternProviderLogic
public appeng.helpers.patternprovider.PatternProviderLogic host
```

**注意**：这些 Access Transformer 必须在 `META-INF/accesstransformer.cfg` 中正确声明，否则在运行时访问这些字段会抛出 `IllegalAccessException`。

## 与 AE2 官方库的关系

### AE2 官方库信息
- **项目名称**：Applied Energistics 2
- **GitHub**：https://github.com/AppliedEnergistics/Applied-Energistics-2
- **分支**：`forge/1.20.1`
- **Maven**：
  - 官方：GitHub Packages（需要认证）
  - 镜像：Modmaven（无需认证）

### 本项目与 AE2 的关系
1. **非官方扩展**：AE2 Crafting Lens 是 AE2 的非官方扩展模组
2. **运行时依赖**：需要 AE2  installed 才能运行
3. **深度集成**：通过反射访问 AE2 的内部 API
4. **无编译依赖**：不编译时依赖 AE2，只依赖 Forge API

### 建议的依赖声明
在 `mods.toml` 中：
```toml
[[dependencies.ae2craftinglens]]
modId="ae2"
mandatory=true
type="required"
versionRange="[15.2.0,)"  # 支持 AE2 15.2.0 及以上
ordering="AFTER"          # 在 AE2 之后加载
side="BOTH"
```

## 未来改进建议

### 1. 使用 AE2 官方 API（如果可用）
检查 AE2 是否提供了公开的 API 来替代反射调用。

### 2. 版本检测
在运行时检测 AE2 版本，使用不同的反射策略。

### 3. 降级策略
当某些反射调用失败时，提供备用的查找策略。

### 4. 文档化依赖
维护一个 AE2 版本兼容性表，记录哪些版本是已知兼容的。

## 总结

AE2 Crafting Lens 通过反射深度集成了 AE2 的内部 API，主要依赖以下核心类：

**核心依赖**：
- `AEKey` / `AEKeyType` - 物品标识和序列化
- `GenericStack` - 物品栈包装
- `CraftingStatusMenu` - 合成状态界面
- `CraftingCPUCluster` - 合成 CPU 集群
- `CraftingCpuLogic` / `ExecutingCraftingJob` - 合成任务管理
- `IPatternDetails` - Pattern 详情
- `PatternProviderLogic` - Pattern Provider 逻辑

**关键特性**：
- 运行时反射访问（无编译依赖）
- Access Transformer 支持
- 深度集成 AE2 内部 API
- 版本敏感性高

这种设计使得模组能够灵活地适配不同版本的 AE2，但也带来了维护成本和版本兼容性的挑战。
