#  Splice — Minecraft Mod Cross-Version Migration Tool

<div align="center">

<kbd>[English](README.en.md)</kbd> <kbd>[简体中文](README.md)</kbd>

</div>

![](https://moe-counter.ieshishinjin.workers.dev/github/ieshishinjin/Splice)


Splice 是一个 Minecraft Mod 跨版本迁移命令行工具，支持 Forge（MCP）和 Fabric（Yarn）加载器，能自动处理映射表差异、代码转换和元数据更新。

## 快速开始

```bash
# 构建 fat JAR
./gradlew shadowJar

# 交互式向导（推荐）
java -jar build/libs/Splice-1.1.0-all.jar -I

# 单版本迁移
java -jar build/libs/Splice-1.1.0-all.jar \
  -s 1.20.1 -t 1.21 -l forge -i ./MyMod

# 多版本批量迁移（逗号分隔或多次 -t）
java -jar build/libs/Splice-1.1.0-all.jar \
  -s 1.16.5 -t 1.20.4 -t 1.21 -l forge -i ./MyMod.jar

# jar 文件迁移
java -jar build/libs/Splice-1.1.0-all.jar \
  -s 1.19.2 -t 1.20.4 -l fabric -i ./MyMod.jar

# 预览模式
java -jar build/libs/Splice-1.1.0-all.jar \
  -s 1.20.1 -t 1.21 -l forge -i ./src --dry-run
```

## 功能特性

| 功能 | 说明 |
|------|------|
| **多加载器支持** | Forge (MCP) / Fabric (Yarn) |
| **多版本批量迁移** | 一次指定多个目标版本 `-t 1.20.4,1.21` |
| **源码目录 → git 分支** | 批量模式每个版本自动建 `splice/源-to-目标` 分支 |
| **JAR 多版本输出** | jar 文件批量迁移自动生成多个带版本后缀的文件 |
| **物品模型转换** | 1.16 → 1.20+ 自动添加 `forge:item_layers` 修复物品栏贴图 |
| **自动映射下载** | 从 Forge Maven / MCPConfig GitHub 等多源下载 MCP/Yarn 映射表 |
| **源码转换 (AST)** | JavaParser AST 精确替换，含正则降级 |
| **字节码转换** | ASM remapping，解压到目录再打包确保资源完整 |
| **元数据更新** | 更新 `mods.toml` / `fabric.mod.json` 版本和类引用 |
| **Mixin 处理** | 自动更新 mixin 配置中的目标类引用 |
| **Access Widener / AT** | 更新 Fabric `.accesswidener` 和 Forge `accesstransformer.cfg` |
| **冲突报告** | JSON 报告 + 控制台摘要，标明文件和行号 |
| **i18n 中英双语** | 启动时选择语言，交互式向导文字全部可切换 |
| **并行处理** | 多线程并行转换 |
| **离线模式** | `--mappings-dir` 指定本地映射文件 |
| **清理缓存** | `--clean-deps` 只删除 Splice 用过的依赖和缓存 |
| **Gradle 插件** | 可选插件集成到构建流程 |

## 使用方式

### 交互式向导

```
> java -jar Splice-1.1.0-all.jar -I

选择语言 / Choose language:
  1. 中文
  2. English

  输入 :wq 退出

── SPLICE 交互式迁移向导 ──
  1. 配置版本           (未设置) → (未设置)
  2. 配置加载器          (未设置)
  3. 配置输入路径         (未设置)
  4. 配置输出路径         (未设置)
  5. 加载映射表
  6. ▶ 执行迁移
  7. 查看迁移报告
───
  > 选择操作 [1-7]:       ← :wq 随时退出
```

### 命令行参数

```
-s, --source-version   源版本 (如 1.20.1)
-t, --target-version   目标版本，多个: -t 1.21 -t 1.20.4 或逗号分隔
-l, --loader           加载器: forge 或 fabric
-i, --input            输入: 源码目录或 .jar 文件
-o, --output           输出路径
-c, --cache            映射缓存目录 (默认 ~/.splice/mappings)
-m, --mappings-dir     本地映射文件 (离线模式)
-I, --interactive      交互式向导
--verbose              详细日志
--dry-run              预览
--threads              并行线程数
--clean-deps           清理 Splice 用过的依赖缓存
```

### 批量迁移示例

```bash
# 源码目录 → 自动 git 分支
java -jar Splice-1.1.0-all.jar -s 1.16.5 -t 1.20.4,1.21 -l forge -i ./MyModSrc
# → 建分支: splice/1.16.5-to-1.20.4, splice/1.16.5-to-1.21

# jar → 多个文件
java -jar Splice-1.1.0-all.jar -s 1.16.5 -t 1.20.4,1.21 -l forge -i ./MyMod.jar
# → 输出: MyMod-1.20.4.jar, MyMod-1.21.jar

# 交互模式
java -jar Splice-1.1.0-all.jar -I
# → 配置版本时输入: 1.20.4,1.21
```

## 映射原理

### Forge (MCP)
```
Obfuscated (Notch) ──[MCPConfig/TSRG]──▶ SRG ──[CSV]──▶ MCP Names
```
### Fabric (Yarn)
```
Intermediary ──[.tiny]──▶ Named (Yarn)
```

Splice 通过中间名 (SRG / Intermediary) 对比源/目标版本命名差异，自动生成替换映射。

## 项目结构

```
src/main/java/io/github/ieshishinjin/splice/
├── SpliceCli.java              # CLI 入口 (picocli)
├── InteractiveMode.java        # 交互式向导
├── Messages.java               # i18n 中英双语
├── CleanDeps.java              # 缓存清理
├── model/                      # 数据模型
│   ├── Version.java
│   ├── MappingEntry.java
│   ├── MappingDiff.java
│   ├── MigrationConfig.java
│   └── Conflict.java
├── mapping/                    # 映射服务
│   ├── MappingService.java
│   ├── MappingDownloader.java  # 多源下载
│   ├── MCPMappingService.java
│   ├── YarnMappingService.java
│   ├── MappingDiffEngine.java
│   └── local/LocalMappingService.java  # 离线文件
├── transformer/                # 转换引擎
│   ├── SourceTransformer.java  # 正则降级
│   ├── ASTSourceTransformer.java # JavaParser AST
│   ├── BytecodeTransformer.java # ASM + 目录打包
│   └── TransformationEngine.java
├── updater/                    # 元数据更新
│   ├── ForgeMetadataUpdater.java  # mods.toml
│   ├── FabricMetadataUpdater.java # fabric.mod.json
│   ├── MixinConfigUpdater.java
│   ├── ModelUpdater.java         # 物品模型格式转换
│   ├── AccessWidenerUpdater.java
│   ├── AccessTransformerUpdater.java
│   └── MetadataUpdater.java
└── reporter/
    └── ConflictReporter.java

splice-gradle-plugin/           # Gradle 插件
```

## 输出

- **迁移后的文件** — 输出目录保持原始结构
- **migration-report.json** — 详细冲突报告
- **~/.splice/logs/** — 操作日志
- **Git 分支** — 源码批量模式建 `splice/*` 分支

## 技术栈

- **语言**: Java 17+
- **构建**: Gradle + Shadow (fat JAR)
- **CLI**: picocli
- **AST**: JavaParser 3.26
- **字节码**: ASM 9.7
- **HTTP**: OkHttp
- **JSON**: Gson
- **日志**: SLF4J + Logback
- **CI**: GitHub Actions
