# AE2 Crafting Lens - Mods.toml 依赖声明修复 - 验证清单

- [x] 检查 mods.toml 文件中的依赖声明 section 名称是否使用了实际的模组 ID
- [x] 验证所有依赖声明的 mandatory 字段都存在且格式正确
- [x] 确认依赖声明中包含了所有必需的依赖（forge、minecraft、ae2）
- [x] 验证构建过程无错误
- [x] 测试模组能够成功加载，无依赖解析错误
- [x] 确认游戏启动时无模组加载失败的错误信息