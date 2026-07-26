# Changelog


>v1.2.0
- NeoForge 加载器支持，处理 neoforge.mods.toml
- Quilt 加载器支持，处理 quilt.mod.json
- 自动模式，一键检测项目路径/加载器/版本
- 配置路径时自动列出 Gradle 项目目录供选择
- 加载器菜单扩展到 Forge / Fabric / NeoForge / Quilt
- ANSI 彩色终端输出，INFO/WARN/ERROR 颜色区分
- 迁移进度条，实时显示处理进度
- git diff 统计，迁移后自动输出变更行数
- --in-place 批量迁移不建 git 分支
- .zip 文件输入支持
- 映射下载失败时可选重试/跳过/退出
- 增加 Mojang 官方映射备用下载源
- 硬编码引用自动扫描，替换字符串中的旧类名
- HTML 可视化迁移报告，带 Diff 表格和冲突高亮
- Release CI 自动读取 CHANGELOG + SHA-256 校验表
- Release 只上传 fat JAR，不含 slim jar

>v1.1.0
## 有什么新鲜事正发生？

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


>v1.0.0
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
