# AE2 Crafting Lens

## 项目介绍 | Project Introduction

AE2 Crafting Lens 是一个 Minecraft 模组，为 Applied Energistics 2 (AE2) 添加了实用的合成追踪功能。当您在无线终端中查看合成状态时，该模组可以高亮显示正在执行合成任务的样板供应器，并提供详细的位置信息。

AE2 Crafting Lens is a Minecraft mod that adds useful crafting tracking functionality to Applied Energistics 2 (AE2). When viewing crafting status in your wireless terminal, this mod highlights pattern providers that are executing crafting tasks and provides detailed location information.

## 功能特性 | Features

- **世界渲染高亮**：在游戏世界中为正在工作的样板供应器渲染12秒的高亮边框
- **详细信息显示**：在聊天栏中显示供应器的维度、精确坐标和与玩家的距离
- **一键传送**：点击聊天栏中的维度或坐标文本可直接传送到供应器位置
- **无线终端兼容**：完全兼容 AE2 无线终端和相关模组
- **简单操作**：在合成状态页面中直接点击物品即可触发高亮功能

- **World Rendering Highlight**：Renders 12-second glowing borders around active pattern providers in the game world
- **Detailed Information**：Displays provider dimension, exact coordinates, and distance from player in chat
- **One-Click Teleport**：Click on dimension or coordinate text in chat to teleport directly to the provider
- **Wireless Terminal Compatibility**：Fully compatible with AE2 wireless terminals and related mods
- **Simple Operation**：Trigger highlighting by directly clicking on items in the crafting status page

## 安装说明 | Installation

1. **安装 NeoForge**：确保您的 Minecraft 客户端已安装 NeoForge 1.21.1 或更高版本
2. **安装 Applied Energistics 2**：确保已安装 AE2 模组
3. **安装 AE2 Crafting Lens**：将模组的 JAR 文件放入 `mods` 文件夹
4. **启动游戏**：启动 Minecraft 客户端，模组会自动加载

1. **Install NeoForge**：Ensure your Minecraft client has NeoForge 1.21.1 or higher installed
2. **Install Applied Energistics 2**：Ensure AE2 mod is installed
3. **Install AE2 Crafting Lens**：Place the mod JAR file into the `mods` folder
4. **Start Game**：Launch Minecraft client, the mod will load automatically

## 使用方法 | Usage

1. **打开无线终端**：使用 AE2 无线终端或普通终端
2. **进入合成状态页面**：点击终端界面右上角的合成状态按钮
3. **查看合成任务**：在合成状态页面中找到正在执行的合成任务
4. **点击物品**：直接点击计划合成列表中的物品
5. **查看效果**：
   - 相关的样板供应器会在世界中高亮显示12秒
   - 聊天栏会显示供应器的详细信息
   - 点击聊天栏中的维度或坐标可直接传送

1. **Open Wireless Terminal**：Use AE2 wireless terminal or regular terminal
2. **Enter Crafting Status Page**：Click the crafting status button in the top right corner of the terminal interface
3. **View Crafting Tasks**：Find active crafting tasks in the crafting status page
4. **Click Item**：Directly click on items in the planned crafting list
5. **View Results**：
   - Related pattern providers will be highlighted in the world for 12 seconds
   - Detailed provider information will be displayed in chat
   - Click on dimension or coordinate text in chat to teleport directly

## 依赖项 | Dependencies

- **Minecraft**：1.21.1
- **NeoForge**：20.0.0 或更高版本
- **Applied Energistics 2**：19.2.17 或更高版本

- **Minecraft**：1.21.1
- **NeoForge**：20.0.0 or higher
- **Applied Energistics 2**：19.2.17 or higher

## 许可证 | License

本项目使用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## 开发 | Development

### 构建模组 | Build the Mod

```bash
# 构建模组
./gradlew build

# 运行开发环境
./gradlew runClient
```

```bash
# Build the mod
./gradlew build

# Run development environment
./gradlew runClient
```

### 贡献 | Contributing

欢迎提交 Issue 和 Pull Request 来帮助改进这个模组！

Contributions are welcome! Feel free to submit Issues and Pull Requests to help improve this mod.
