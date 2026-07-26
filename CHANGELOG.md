# Changelog

## v1.1.0

- 批量多版本迁移，支持 -t 指定多个目标版本
- 源码目录批量迁移自动建 git 分支
- JAR 批量迁移自动输出多个版本文件
- 物品模型格式自动转换，修复 1.20+ 物品栏贴图
- JAR 打包重写，确保贴图等资源完整保留
- MCP 映射下载源修复，支持老版本和新版本
- 交互模式同步支持多版本
- i18n 中英双语启动选择
- 数字输入校验
- --clean-deps 清理缓存

## v1.0.0

- 首次发布
- 支持 Forge (MCP) / Fabric (Yarn) 加载器
- 自动下载 MCP/Yarn 映射表
- 源码转换（JavaParser AST + 正则降级）
- 字节码转换（ASM remapping）
- JAR 直接处理
- 元数据更新（mods.toml / fabric.mod.json）
- Mixin 配置更新
- Access Widener / Access Transformer 更新
- 冲突报告 JSON 输出
- 并行多线程处理
- 离线映射文件支持
- 交互式向导
- Gradle 插件
- GitHub Actions 构建
