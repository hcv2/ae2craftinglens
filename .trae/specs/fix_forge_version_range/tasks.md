# Tasks - 修改 Forge 依赖版本范围

## [x] Task 1: 修改 mods.toml 中的 Forge 版本范围
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 将 mods.toml 文件中 Forge 依赖的 versionRange 从 `[47.4.10,)` 修改为 `[47.3.7,)`
  - 确保格式正确
- **Acceptance Criteria Addressed**: MODIFIED Requirements - Forge 依赖版本范围
- **Test Requirements**:
  - `programmatic` TR-1.1: 确认 mods.toml 文件中的 versionRange 已修改为 `[47.3.7,)`
  - `human-judgement` TR-1.2: 验证文件格式正确，无语法错误

## [x] Task 2: 重新构建模组并验证
- **Priority**: P1
- **Depends On**: Task 1
- **Description**: 
  - 使用 Gradle 重新构建模组
  - 验证构建后的 JAR 文件中 mods.toml 的版本范围正确
- **Acceptance Criteria Addressed**: MODIFIED Requirements - Forge 依赖版本范围
- **Test Requirements**:
  - `programmatic` TR-2.1: 构建成功，无错误
  - `programmatic` TR-2.2: 构建后的 mods.toml 文件中 versionRange 为 `[47.3.7,)`
- **Notes**: 构建成功，验证通过

# Task Dependencies
- Task 2 depends on Task 1
