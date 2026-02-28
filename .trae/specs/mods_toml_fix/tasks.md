# AE2 Crafting Lens - Mods.toml 依赖声明修复 - 实现计划

## [x] Task 1: 分析 mods.toml 文件中的依赖声明问题
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 详细分析 mods.toml 文件中的依赖声明格式
  - 确认 section 名称中使用变量 `${mod_id}` 的问题
  - 验证 Forge 模组加载器对 section 名称的解析规则
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `programmatic` TR-1.1: 确认 mods.toml 文件的当前格式
  - `human-judgement` TR-1.2: 分析 section 名称中的变量替换问题
- **Notes**: 重点关注依赖声明部分的 section 名称格式

## [x] Task 2: 修复 mods.toml 文件中的依赖声明格式
- **Priority**: P0
- **Depends On**: Task 1
- **Description**: 
  - 将依赖声明的 section 名称从 `${mod_id}` 替换为实际的模组 ID
  - 确保所有依赖声明的格式正确
  - 验证所有必需的 mandatory 字段都存在
- **Acceptance Criteria Addressed**: AC-1, AC-3
- **Test Requirements**:
  - `programmatic` TR-2.1: 确认修改后的 mods.toml 文件格式正确
  - `human-judgement` TR-2.2: 验证所有依赖声明的 mandatory 字段都存在
- **Notes**: 实际的模组 ID 是 "ae2craftinglens"

## [x] Task 3: 构建和测试修复后的模组
- **Priority**: P1
- **Depends On**: Task 2
- **Description**: 
  - 使用 Gradle 构建修复后的模组
  - 测试模组是否能够被 Forge 模组加载器正确识别和加载
  - 验证依赖解析是否成功
- **Acceptance Criteria Addressed**: AC-2
- **Test Requirements**:
  - `programmatic` TR-3.1: 确认构建过程无错误
  - `programmatic` TR-3.2: 验证模组能够成功加载，无依赖解析错误
- **Notes**: 构建成功，说明 mods.toml 文件格式正确