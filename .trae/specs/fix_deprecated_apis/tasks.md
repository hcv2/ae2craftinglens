# Tasks - 修复已弃用 API 警告

## [x] Task 1: 评估 AE2CraftingLens.java 中的已弃用 API
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 评估第 28 行和第 33 行的 `FMLJavaModLoadingContext.get()` 已弃用调用
  - **结论**：该 API 在 Minecraft 1.20.1 中仍然是标准做法，无需修复
- **Acceptance Criteria Addressed**: MODIFIED Requirements - 使用新的 API 替换已弃用的 API
- **Test Requirements**:
  - `programmatic` TR-1.1: 代码编译正常
  - `human-judgement` TR-1.2: 代码功能正常
- **Notes**: 保留现有代码，该 API 在当前版本中是安全的

## [x] Task 2: 评估 NetworkHandler.java 中的已弃用 API
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 评估第 15 行的 `ResourceLocation(String, String)` 构造函数已弃用调用
  - **重要发现**：`ResourceLocation.fromNamespaceAndPath()` 在 Minecraft 1.20.1 中不存在！
  - **结论**：必须使用 `new ResourceLocation()` 构造函数
- **Acceptance Criteria Addressed**: MODIFIED Requirements - 使用新的 API 替换已弃用的 API
- **Test Requirements**:
  - `programmatic` TR-2.1: 代码编译正常
  - `programmatic` TR-2.2: 代码运行时不抛出 NoSuchMethodError
  - `human-judgement` TR-2.2: 代码功能正常
- **Notes**: 已恢复使用 `new ResourceLocation()` 构造函数

## [x] Task 3: 评估 PatternProviderResponsePacket.java 中的已弃用 API
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 评估第 45 行的 `ResourceLocation(String)` 构造函数已弃用调用
  - **重要发现**：`ResourceLocation.parse()` 在 Minecraft 1.20.1 中不存在！
  - **结论**：必须使用 `new ResourceLocation()` 构造函数
- **Acceptance Criteria Addressed**: MODIFIED Requirements - 使用新的 API 替换已弃用的 API
- **Test Requirements**:
  - `programmatic` TR-3.1: 代码编译正常
  - `programmatic` TR-3.2: 代码运行时不抛出 NoSuchMethodError
  - `human-judgement` TR-3.2: 代码功能正常
- **Notes**: 已恢复使用 `new ResourceLocation()` 构造函数

## [x] Task 4: 重新构建并验证
- **Priority**: P1
- **Depends On**: Task 1, Task 2, Task 3
- **Description**: 
  - 使用 Gradle 重新构建模组
  - 验证所有警告都已评估，确认无需修复
- **Acceptance Criteria Addressed**: MODIFIED Requirements - 使用新的 API 替换已弃用的 API
- **Test Requirements**:
  - `programmatic` TR-4.1: 构建成功，无错误
  - `programmatic` TR-4.2: 代码运行时不抛出 NoSuchMethodError
  - `human-judgement` TR-4.3: 确认所有警告都是安全的
- **Notes**: 构建成功，所有警告都是安全的，无需修复

# Task Dependencies
- Task 4 depends on Task 1, Task 2, Task 3
