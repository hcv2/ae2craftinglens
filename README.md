# AE2 Crafting Lens

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-orange.svg)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

本项目均为AI构建

## 项目介绍 | Introduction

这个模组为 **Applied Energistics 2 (AE2)** 添加了**样板供应器高亮定位**功能，帮助玩家快速找到正在使用的样板供应器。

This mod adds **Pattern Provider Highlighting** functionality to **Applied Energistics 2 (AE2)**, helping players quickly locate the pattern providers being used.

## 功能特性 | Features

### 核心功能 | Core Features

- **一键定位**：在ME合成状态界面点击任意物品，自动高亮显示对应的样板供应器
- **闪烁高亮**：青蓝色边框闪烁效果，持续12秒，清晰可见
- **坐标提示**：聊天栏显示位置坐标和距离，支持点击传送
- **多维度支持**：跨维度后仍可查看高亮信息

### 支持的模组 | Supported Mods

| 模组 | 支持的方块 |
|------|-----------|
| **AE2 (原版)** | ME样板供应器 (方块/部件) |
| **ExtendedAE** | ME扩展样板供应器 |
| **AdvancedAE** | ME高级样板供应器、ME高级扩展样板供应器 |

### 技术特点 | Technical Features

- ✅ **多人支持**：每个玩家只能看到自己的高亮，互不干扰
- ✅ **局域网兼容**：支持单人游戏和对局域网开放
- ✅ **服务器兼容**：支持专用服务器环境
- ✅ **性能优化**：使用反射缓存和高效渲染

## 使用方法 | Usage

1. 打开 **ME合成状态** 界面
2. **点击**任意正在合成的物品
3. 对应的样板供应器会**闪烁高亮**显示
4. 聊天栏显示位置信息，**点击可传送**

## 依赖 | Dependencies

| 依赖 | 版本 |
|------|------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Applied Energistics 2 | 19.0.0+ |

## 构建模组 | Build

```bash
# Windows
.\gradlew.bat build

# Linux/macOS
./gradlew build
```

构建产物位于 `build/libs/` 目录。

## 开发环境 | Development

```bash
# Windows
.\gradlew.bat runClient   # 运行客户端
.\gradlew.bat runServer   # 运行服务器

# Linux/macOS
./gradlew runClient
./gradlew runServer
```

## 许可证 | License

本项目使用 **MIT** 许可证。详见 [LICENSE](LICENSE) 文件。

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

## 致谢 | Credits

- [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) - AE2团队
- [ExtendedAE](https://github.com/GlodBlock/ExtendedAE) - 高亮渲染参考
- [NeoForge](https://neoforged.net/) - 模组加载器

## 贡献 | Contributing

欢迎提交 Issue 和 Pull Request 来帮助改进这个模组！

Contributions are welcome! Feel free to submit Issues and Pull Requests to help improve this mod.
