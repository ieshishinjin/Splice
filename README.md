# 🧬 Splice — Minecraft Mod Cross-Version Migration Tool

Splice 是一个 Minecraft Mod 跨版本迁移命令行工具，支持 Forge（MCP）和 Fabric（Yarn）加载器，能自动处理映射表差异、代码转换和元数据更新。

## 快速开始

```bash
# 构建 fat JAR
./gradlew shadowJar

# 查看帮助
java -jar build/libs/Splice-1.0.0-all.jar --help

# 源码目录迁移（Forge 1.20.1 → 1.21）
java -jar build/libs/Splice-1.0.0-all.jar \
  -s 1.20.1 -t 1.21 -l forge -i ./MyMod

# .jar 文件迁移（Fabric 1.19.2 → 1.20.4，指定输出目录）
java -jar build/libs/Splice-1.0.0-all.jar \
  -s 1.19.2 -t 1.20.4 -l fabric -i ./MyMod.jar -o ./MyMod-migrated

# 预览模式（不写文件）
java -jar build/libs/Splice-1.0.0-all.jar \
  -s 1.20.1 -t 1.21 -l forge -i ./src --dry-run --verbose
```

## 功能特性

| 功能 | 说明 |
|------|------|
| **多加载器支持** | Forge (MCP) / Fabric (Yarn) |
| **自动映射下载** | 从官方源自动下载并缓存 MCP/Yarn 映射表 |
| **源码转换** | 自动替换 Java 源文件中的类名、方法名、字段名 |
| **字节码转换** | 使用 ASM 对 .class 文件做 remapping |
| **JAR 处理** | 直接处理 .jar 文件中的字节码 |
| **元数据更新** | 更新 `mods.toml` / `fabric.mod.json` 版本字段 |
| **冲突报告** | 输出详细 JSON 报告，标明冲突文件和行号 |
| **并行处理** | 多线程并行转换（默认 CPU 核心数） |
| **预览模式** | `--dry-run` 预览变更不写文件 |
| **增量安全** | 保留原始目录结构，输出到独立目录 |

## 命令行参数

```
-s, --source-version   源版本 (如 1.20.1)
-t, --target-version   目标版本 (如 1.21)
-l, --loader           加载器类型: forge 或 fabric
-i, --input            输入路径: 源码目录或 .jar 文件
-o, --output           输出路径 (默认: <input>-migrated)
-c, --cache            映射表缓存目录 (默认: ~/.splice/mappings)
--verbose              详细日志
--dry-run              预览模式
--threads              并行线程数
```

## 项目结构

```
src/main/java/io/github/ieshishinjin/splice/
├── SpliceCli.java              # CLI 入口 (picocli)
├── model/                      # 数据模型
│   ├── Version.java            # MC 版本号
│   ├── MappingEntry.java       # 映射条目
│   ├── MappingDiff.java        # 版本差异
│   ├── MigrationConfig.java    # 迁移配置
│   └── Conflict.java           # 冲突报告
├── mapping/                    # 映射服务
│   ├── MappingService.java     # 接口
│   ├── MappingDownloader.java  # 下载/缓存
│   ├── MCPMappingService.java  # MCP 解析
│   ├── YarnMappingService.java # Yarn 解析
│   └── MappingDiffEngine.java  # 差异对比
├── transformer/                # 转换引擎
│   ├── SourceTransformer.java  # 源码转换
│   ├── BytecodeTransformer.java# 字节码转换
│   └── TransformationEngine.java # 编排
├── processor/
│   └── FileProcessor.java      # 文件系统操作
├── updater/
│   ├── MetadataUpdater.java    # 接口
│   ├── ForgeMetadataUpdater.java # mods.toml
│   └── FabricMetadataUpdater.java # fabric.mod.json
└── reporter/
    └── ConflictReporter.java   # 报告输出
```

## 映射表原理

### Forge (MCP) 映射链
```
Obfuscated (Notch) ──[joined.tsrg]──▶ SRG (intermediate) ──[CSV]──▶ MCP Names
```
Splice 利用 mcp_config 和 mcp_stable 下载完整映射链，对比源/目标版本的 MCP 名称差异。

### Fabric (Yarn) 映射链
```
Intermediary ──[.tiny]──▶ Named (Yarn)
```
Splice 下载 Yarn 发布包中的 tiny 格式映射，直接对比命名差异。

## 输出

迁移完成后会生成：
- **迁移后的文件**：在输出目录中保持原始结构
- **migration-report.json**：详细的冲突和变更报告
- **~/.splice/logs/**：详细日志文件

## 技术栈

- **语言**: Java 17+
- **构建**: Gradle + Shadow (fat JAR)
- **CLI**: picocli
- **字节码**: ASM 9.7
- **HTTP**: OkHttp
- **JSON**: Gson
- **日志**: SLF4J + Logback
