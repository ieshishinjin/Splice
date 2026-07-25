# Splice — Minecraft Mod Cross-Version Migration Tool

<div align="center">

<kbd>[English](README.en.md)</kbd> <kbd>[简体中文](README.md)</kbd>

</div>

<div align="center">
  <img src="https://moe-counter.ieshishinjin.workers.dev/github/ieshishinjin/Splice">
</div>

Splice is a command-line tool for migrating Minecraft mods across versions. It supports both Forge (MCP) and Fabric (Yarn) loaders, automatically handling mapping differences, source/bytecode transformation, and metadata updates.

```
   _____       ___
  / ___/____  / (_)_______
  \__ \/ __ \/ / / ___/ _ \
 ___/ / /_/ / / / /__/  __/
/____/ .___/_/_/\___/\___/
    /_/
  Minecraft Mod Migration Tool  v1.1.0
```

## Quick Start

```bash
# Build
./gradlew shadowJar

# Interactive wizard
java -jar build/libs/Splice-1.1.0-all.jar -I

# Single version migration
java -jar build/libs/Splice-1.1.0-all.jar \
  -s 1.20.1 -t 1.21 -l forge -i ./MyMod

# Batch multi-version migration
java -jar build/libs/Splice-1.1.0-all.jar \
  -s 1.16.5 -t 1.20.4 -t 1.21 -l forge -i ./MyMod.jar

# JAR migration
java -jar build/libs/Splice-1.1.0-all.jar \
  -s 1.19.2 -t 1.20.4 -l fabric -i ./MyMod.jar -o ./MyMod-migrated

# Dry-run preview
java -jar build/libs/Splice-1.1.0-all.jar \
  -s 1.20.1 -t 1.21 -l forge -i ./src --dry-run
```

## Features

| Feature | Description |
|---------|-------------|
| **Multi-loader** | Forge (MCP) / Fabric (Yarn) |
| **Batch migration** | Multiple target versions at once `-t 1.20.4,1.21` |
| **Source → git branches** | Auto-creates `splice/from-to` branches for each target |
| **Multi-jar output** | Batch JAR migration produces version-suffixed files |
| **Item model conversion** | Auto-adds `forge:item_layers` for 1.20+ inventory rendering |
| **Auto mapping** | Multi-source download (Forge Maven / MCPConfig GitHub / MCPBot) |
| **AST source transform** | JavaParser AST with regex fallback |
| **Bytecode transform** | ASM remapping, temp-dir based for resource integrity |
| **Metadata updates** | `mods.toml`, `fabric.mod.json`, mixins, AW/AT |
| **Mixin handling** | Remaps target classes in mixin configs |
| **Model updater** | Migrates item model format across Minecraft versions |
| **Conflict report** | JSON + console summary with file:line |
| **i18n** | Chinese / English wizard |
| **Parallel processing** | Multi-threaded |
| **Offline mode** | `--mappings-dir` for local mapping files |
| **Cache cleanup** | `--clean-deps` removes Splice's cached dependencies only |
| **Gradle plugin** | Optional build integration |

## Usage

### Interactive Wizard

```
> java -jar Splice-1.1.0-all.jar -I

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
  > Choose option [1-7]:       ← Type :wq to exit anytime
```

### CLI Arguments

```
-s, --source-version   Source version (e.g. 1.20.1)
-t, --target-version   Multiple: -t 1.21 -t 1.20.4 or comma-separated
-l, --loader           forge or fabric
-i, --input            Source directory or .jar file
-o, --output           Output path
-c, --cache            Cache directory (default ~/.splice/mappings)
-m, --mappings-dir     Local mapping files (offline)
-I, --interactive      Interactive wizard
--verbose              Verbose logging
--dry-run              Preview only
--threads              Parallel threads
--clean-deps           Remove Splice's cached dependencies
```

### Batch Migration Examples

```bash
# Source directory → auto git branches
java -jar Splice-1.1.0-all.jar -s 1.16.5 -t 1.20.4,1.21 -l forge -i ./MyModSrc
# → Creates: splice/1.16.5-to-1.20.4, splice/1.16.5-to-1.21

# JAR → multiple output files
java -jar Splice-1.1.0-all.jar -s 1.16.5 -t 1.20.4,1.21 -l forge -i ./MyMod.jar
# → Output: MyMod-1.20.4.jar, MyMod-1.21.jar

# Interactive mode
java -jar Splice-1.1.0-all.jar -I
# → Enter target versions as: 1.20.4,1.21
```

## How It Works

### Forge (MCP) Mapping Chain
```
Obfuscated (Notch) ──[MCPConfig/TSRG]──▶ SRG ──[CSV]──▶ MCP Names
```
### Fabric (Yarn) Mapping Chain
```
Intermediary ──[.tiny]──▶ Named (Yarn)
```

Splice compares source and target version mappings via intermediate names (SRG / Intermediary), then applies renames to source code, bytecode, and metadata.

### Transformation Pipeline

1. **Mapping diff** via intermediate names
2. **Source transformation** — JavaParser AST or regex
3. **Bytecode transformation** — ASM ClassRemapper, temp-dir repacking
4. **Metadata update** — Version, mixins, models, AW/AT, mod configs
5. **Conflict reporting** — JSON per-file

## Project Structure

```
src/main/java/io/github/ieshishinjin/splice/
├── SpliceCli.java              # CLI entry (picocli)
├── InteractiveMode.java        # Interactive wizard
├── Messages.java               # i18n (zh/en)
├── CleanDeps.java              # Cache cleanup
├── model/                      # Data models
├── mapping/                    # Mapping services
│   ├── MappingDownloader.java  # Multi-source download
│   ├── MCPMappingService.java
│   ├── YarnMappingService.java
│   ├── MappingDiffEngine.java
│   └── local/LocalMappingService.java
├── transformer/                # Transformation
│   ├── SourceTransformer.java  # Regex fallback
│   ├── ASTSourceTransformer.java # JavaParser AST
│   ├── BytecodeTransformer.java # ASM + temp-dir repack
│   └── TransformationEngine.java
├── updater/                    # Metadata updaters
│   ├── ForgeMetadataUpdater.java
│   ├── FabricMetadataUpdater.java
│   ├── MixinConfigUpdater.java
│   ├── ModelUpdater.java       # Item model conversion
│   ├── AccessWidenerUpdater.java
│   └── AccessTransformerUpdater.java
└── reporter/
    └── ConflictReporter.java

splice-gradle-plugin/
```

## Output

- **Migrated files** — Mirrors input structure
- **migration-report.json** — Conflict details
- **~/.splice/logs/** — Operation log
- **Git branches** — `splice/*` for batch source migration

## Gradle Plugin

```kotlin
plugins {
    id("io.github.ieshishinjin.splice") version "1.1.0"
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
- **CI**: GitHub Actions
