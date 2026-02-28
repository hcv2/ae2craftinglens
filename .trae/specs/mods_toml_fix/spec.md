# AE2 Crafting Lens - Mods.toml 依赖声明修复

## Overview
- **Summary**: 修复 AE2 Crafting Lens 模组的 mods.toml 文件中的依赖声明问题，确保模组能够正确加载。
- **Purpose**: 解决 Forge 模组加载器在扫描模组过程中发现的无效模组文件问题，原因是依赖声明中缺少必需的 mandatory 字段。
- **Target Users**: 模组开发者和玩家

## Goals
- 修复 mods.toml 文件中的依赖声明格式问题
- 确保模组能够正确加载，无依赖解析错误
- 保持与 Forge 模组加载器的兼容性

## Non-Goals (Out of Scope)
- 不修改模组的功能代码
- 不更改模组的版本号或其他元数据
- 不处理 Forge 核心库文件缺失 mods.toml 的警告（这些属于正常现象）

## Background & Context
- 问题描述：游戏启动时，Forge 模组加载器在扫描模组过程中发现一个无效的模组文件 ae2craftinglens-1.0.2.jar，原因是该模组的依赖声明中缺少必需的 mandatory 字段，导致无法解析其依赖关系，进而使模组加载失败。
- 分析发现：mods.toml 文件中的依赖声明部分使用了变量 `${mod_id}` 作为 section 名称，这种语法在 TOML 文件中可能不会被正确解析。

## Functional Requirements
- **FR-1**: 修复 mods.toml 文件中的依赖声明格式，确保所有必需的 mandatory 字段都存在且格式正确
- **FR-2**: 确保模组能够被 Forge 模组加载器正确识别和加载

## Non-Functional Requirements
- **NFR-1**: 保持与现有 Forge 版本的兼容性
- **NFR-2**: 确保修改后的 mods.toml 文件符合 Forge 模组加载器的规范

## Constraints
- **Technical**: 必须使用 Forge 模组加载器的标准 mods.toml 格式
- **Dependencies**: 依赖于 Forge 模组加载器的解析规则

## Assumptions
- 模组的其他配置和代码都是正确的
- Forge 模组加载器的解析规则是标准的

## Acceptance Criteria

### AC-1: 依赖声明格式正确
- **Given**: mods.toml 文件被修改
- **When**: Forge 模组加载器扫描模组文件
- **Then**: 模组加载器能够正确解析依赖声明，无错误信息
- **Verification**: `programmatic`

### AC-2: 模组能够正确加载
- **Given**: 修复后的 mods.toml 文件
- **When**: 游戏启动时加载模组
- **Then**: 模组能够成功加载，无加载失败的错误信息
- **Verification**: `programmatic`

### AC-3: 依赖关系完整
- **Given**: 修复后的 mods.toml 文件
- **When**: 检查依赖声明
- **Then**: 所有必需的依赖（forge、minecraft、ae2）都被正确声明
- **Verification**: `human-judgment`

## Open Questions
- [ ] 确认 Forge 模组加载器对 mods.toml 文件中 section 名称的解析规则
- [ ] 确认变量替换在 section 名称中的处理方式