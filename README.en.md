# Splice — Minecraft Mod Cross-Version Migration Tool

![](https://moe-counter.ieshishinjin.workers.dev/github/ieshishinjin/Splice)

Splice is a command-line tool for migrating Minecraft mods across versions. It supports both Forge (MCP) and Fabric (Yarn) loaders, automatically handling mapping differences, source/bytecode transformation, and metadata updates.

```
   _____       ___
  / ___/____  / (_)_______
  \__ \/ __ \/ / / ___/ _ \
 ___/ / /_/ / / / /__/  __/
/____/ .___/_/_/\___/\___/
    /_/
  Minecraft Mod Migration Tool  v1.0.0
```

## Quick Start

```bash
# Build
./gradlew shadowJar

# Interactive wizard
java -jar build/libs/Splice-1.0.0-all.jar -I

# One-shot migration (Forge 1.20.1 → 1.21)
java -jar build/libs/Splice-1.0.0-all.jar \
  -s 1.20.1 -t 1.21 -l forge -i ./MyMod

# JAR migration (Fabric 1.19.2 → 1.20.4)
java -jar build/libs/Splice-1.0.0-all.jar \
  -s 1.19.2 -t 1.20.4 -l fabric -i ./MyMod.jar -o ./MyMod-migrated

# Dry-run preview
java -jar build/libs/Splice-1.0.0-all.jar \
  -s 1.20.1 -t 1.21 -l forge -i ./src --dry-run --verbose
```

## Features

| Feature | Description |
|---------|-------------|
| **Multi-loader** | Forge (MCP) / Fabric (Yarn) |
| **Auto mapping** | Downloads & caches MCP/Yarn mappings from official sources |
| **Source transformation** | AST-based (JavaParser) with regex fallback — renames classes, methods, fields |
| **Bytecode transformation** | ASM-powered remapping for .class files |
| **JAR processing** | Direct .jar bytecode transformation |
| **Metadata updates** | Auto-updates `mods.toml` / `fabric.mod.json` version fields |
| **Mixin handling** | Remaps target classes in mixin config JSON files |
| **Access Widener / AT** | Updates Fabric `.accesswidener` and Forge `accesstransformer.cfg` |
| **Conflict report** | JSON report with file:line for unresolved issues |
| **Parallel processing** | Multi-threaded (default: CPU core count) |
| **i18n** | Chinese / English interactive wizard |
| **Dry-run** | Preview changes without writing |
| **Safe output** | Preserves original directory structure, writes to separate output |
| **Offline mode** | Use local mapping files via `--mappings-dir` |
| **Gradle plugin** | Optional `splice-gradle-plugin` for build integration |

## Usage

### Interactive Wizard

```
> java -jar Splice-1.0.0-all.jar -I

选择语言 / Choose language:
  1. 中文
  2. English

  Type :wq to exit

── SPLICE Interactive Migration Wizard ──
  1. Configure Versions     (unset) → (unset)
  2. Configure Loader       (unset)
  3. Configure Input Path   (unset)
  4. Configure Output Path   (unset)
  5. Load Mappings
  6. ▶ Run Migration
  7. View Migration Report
───
  > Choose option [1-7]:
```

### CLI Arguments

```
-s, --source-version   Source version (e.g. 1.20.1)
-t, --target-version   Target version (e.g. 1.21)
-l, --loader           Loader type: forge or fabric
-i, --input            Input: source directory or .jar file
-o, --output           Output directory (default: <input>-migrated)
-c, --cache            Mapping cache directory (default: ~/.splice/mappings)
-m, --mappings-dir     Local mapping files (offline mode)
-I, --interactive      Interactive wizard mode
--verbose              Verbose logging
--dry-run              Preview only, no writes
--threads              Parallel threads
--no-cache             Re-download mappings
```

## How It Works

### Forge (MCP) Mapping Chain
```
Obfuscated (Notch) ──[joined.tsrg]──▶ SRG (intermediate) ──[CSV]──▶ MCP Names
```
Splice downloads `mcp_config` and `mcp_stable` to build the full mapping chain, then diffs MCP names between source and target versions.

### Fabric (Yarn) Mapping Chain
```
Intermediary ──[.tiny]──▶ Named (Yarn)
```
Splice downloads Yarn's tiny-format mappings and compares named entries directly.

### Transformation Pipeline

1. **Mapping diff** — Compare source/target mappings via intermediate names (SRG / Intermediary)
2. **Source transformation** — JavaParser AST visitor renames types, methods, fields, annotations, imports
3. **Bytecode transformation** — ASM ClassRemapper rewrites .class constant pool references
4. **Metadata update** — Version bump + remapped class references in mod configs, mixins, AW/AT
5. **Conflict reporting** — Collected per-file, output as JSON + console summary

## Project Structure

```
src/main/java/io/github/ieshishinjin/splice/
├── SpliceCli.java              # CLI entry (picocli)
├── InteractiveMode.java        # Interactive wizard
├── Messages.java               # i18n (zh/en)
├── model/                      # Data models
│   ├── Version.java            # MC version
│   ├── MappingEntry.java       # Mapping entry
│   ├── MappingDiff.java        # Version diff
│   ├── MigrationConfig.java    # Migration config
│   └── Conflict.java           # Conflict report
├── mapping/                    # Mapping services
│   ├── MappingService.java     # Interface
│   ├── MappingDownloader.java  # Download/cache
│   ├── MCPMappingService.java  # MCP parser
│   ├── YarnMappingService.java # Yarn parser
│   ├── MappingDiffEngine.java  # Diff engine
│   └── local/
│       └── LocalMappingService.java # Offline files
├── transformer/                # Transformation
│   ├── SourceTransformer.java  # Regex fallback
│   ├── ASTSourceTransformer.java # JavaParser AST
│   ├── BytecodeTransformer.java # ASM remapper
│   └── TransformationEngine.java # Orchestrator
├── processor/
│   └── FileProcessor.java      # File I/O
├── updater/
│   ├── MetadataUpdater.java    # Interface
│   ├── ForgeMetadataUpdater.java # mods.toml
│   ├── FabricMetadataUpdater.java # fabric.mod.json
│   ├── MixinConfigUpdater.java # Mixin configs
│   ├── AccessWidenerUpdater.java # Fabric AW
│   └── AccessTransformerUpdater.java # Forge AT
└── reporter/
    └── ConflictReporter.java   # Report output

splice-gradle-plugin/           # Gradle plugin module
├── build.gradle.kts
└── src/.../gradle/
    ├── SplicePlugin.java
    ├── SpliceExtension.java
    └── SpliceMigrationTask.java
```

## Output

After migration:
- **Migrated files** — Output directory mirrors input structure
- **migration-report.json** — Detailed conflict report
- **~/.splice/logs/** — Full operation log

## Gradle Plugin

```kotlin
// build.gradle.kts
plugins {
    id("io.github.ieshishinjin.splice") version "1.0.0"
}

splice {
    sourceVersion = "1.20.1"
    targetVersion = "1.21"
    loader = "forge"
    input = file("src/main/java")
}
```

```bash
./gradlew spliceMigrate
./gradlew spliceDryRun
```

## Tech Stack

- **Language**: Java 17+
- **Build**: Gradle + Shadow (fat JAR)
- **CLI**: picocli
- **AST**: JavaParser 3.26
- **Bytecode**: ASM 9.7
- **HTTP**: OkHttp
- **JSON**: Gson
- **Logging**: SLF4J + Logback
