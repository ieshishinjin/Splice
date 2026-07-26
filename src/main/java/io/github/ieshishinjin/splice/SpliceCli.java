package io.github.ieshishinjin.splice;

import io.github.ieshishinjin.splice.mapping.*;
import io.github.ieshishinjin.splice.mapping.local.LocalMappingService;
import io.github.ieshishinjin.splice.model.*;
import io.github.ieshishinjin.splice.transformer.TransformationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Splice - Minecraft Mod Cross-Version Migration Tool.
 * <p>
 * Transfers Minecraft mods between versions by automatically
 * applying MCP/Yarn mapping changes to source code and bytecode.
 */
@Command(
        name = "splice",
        version = "Splice 1.0.0",
        description = "Minecraft Mod Cross-Version Migration Tool",
        mixinStandardHelpOptions = true,
        usageHelpWidth = 100,
        footer = """
                Examples:
                  splice --source 1.20.1 --target 1.21 --loader forge --input ./MyMod
                  splice -s 1.19.2 -t 1.20.4 -l fabric -i ./MyMod.jar -o ./MyMod-migrated
                  splice -s 1.20.1 -t 1.21 -l forge -i ./src --dry-run --verbose
                """)
public class SpliceCli implements Callable<Integer> {

    // Static initializer: must be first to suppress logback noise before any logger is created
    static {
        System.setProperty("logback.statusListenerClass",
                "ch.qos.logback.core.status.NopStatusListener");
    }

    private static final Logger LOG = LoggerFactory.getLogger(SpliceCli.class);

    // ---- Required options ----

    @Option(names = {"-s", "--source-version"},
            description = "Source Minecraft version (e.g., 1.20.1)")
    private String sourceVersion;

    @Option(names = {"-t", "--target-version"},
            description = "Target version(s). 多个: -t 1.21 -t 1.20.4 或用逗号分隔",
            split = ",")
    private List<String> targetVersions;

    @Option(names = {"-l", "--loader"},
            description = "Mod loader type: forge, fabric, neoforge")
    private String loader;

    @Option(names = {"-i", "--input"},
            description = "Input mod directory or .jar file")
    private Path inputPath;

    // ---- Optional options ----

    @Option(names = {"-o", "--output"},
            description = "Output directory (default: <input>-migrated)")
    private Path outputPath;

    @Option(names = {"-c", "--cache"},
            description = "Mappings cache directory (default: ~/.splice/mappings)")
    private Path cacheDir;

    @Option(names = {"-m", "--mappings-dir"},
            description = "Local mappings directory (offline: point to dir with CSV/SRG/TSRG/tiny files)")
    private Path mappingsDir;

    @Option(names = {"-I", "--interactive"},
            description = "交互式向导模式 — 逐步配置，执行后返回菜单")
    private boolean interactive;

    @Option(names = {"--verbose", "-v"},
            description = "Enable verbose logging")
    private boolean verbose;

    @Option(names = {"--dry-run"},
            description = "Preview changes without writing files")
    private boolean dryRun;

    @Option(names = {"--threads"},
            description = "Number of parallel processing threads (default: CPU cores)")
    private Integer threads;

    @Option(names = {"--no-cache"},
            description = "Skip mapping cache (re-download mappings)")
    private boolean noCache;

    @Option(names = {"--clean-deps"},
            description = "清理 Splice 用过的 Gradle 依赖缓存和映射缓存")
    private boolean cleanDeps;

    @Option(names = {"--in-place"},
            description = "批量迁移源码目录时不建 git 分支，直接原地改")
    private boolean inPlace;

    @Parameters(description = "Additional arguments (reserved)")
    private List<String> positionalArgs;

    // =====================================================
    // Main
    // =====================================================

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SpliceCli())
                .setExecutionStrategy(new CommandLine.RunAll())
                .execute(args);
        System.exit(exitCode);
    }

    // =====================================================
    // Callable
    // =====================================================

    @Override
    public Integer call() throws Exception {
        SpliceBanner.print();

        if (cleanDeps) { CleanDeps.run(cacheDir); return 0; }
        if (interactive) { new InteractiveMode().start(); return 0; }

        if (sourceVersion == null || targetVersions == null || targetVersions.isEmpty() || loader == null || inputPath == null) {
            System.err.println("错误: 需要 -s, -t, -l, -i 四个参数");
            System.err.println("多个目标版本: -t 1.20.4 -t 1.21 或 -t 1.20.4,1.21");
            return 1;
        }

        LOG.info("Splice v1.0.0 - Minecraft Mod Migration Tool");
        validateInputs();

        Version srcVer = new Version(sourceVersion);
        LoaderType loaderType = LoaderType.fromString(loader);
        boolean isDir = inputPath.toFile().isDirectory();
        boolean batch = targetVersions.size() > 1;

        // 加载源映射（所有目标共用）
        Path cache = cacheDir != null ? cacheDir : Path.of(System.getProperty("user.home"), ".splice", "mappings");
        List<MappingEntry> sourceMappings = loadSourceMappings(srcVer, loaderType, cache);

        // 逐个目标执行
        for (int i = 0; i < targetVersions.size(); i++) {
            String tgtStr = targetVersions.get(i);
            Version tgtVer = new Version(tgtStr);
            LOG.info("\n===== [{}/{}] {} → {} =====", i + 1, targetVersions.size(), sourceVersion, tgtStr);

            List<MappingEntry> tgtMappings = loadTargetMappings(tgtVer, loaderType, cache);
            MappingDiff diff = new MappingDiffEngine().computeDiff(srcVer, tgtVer, sourceMappings, tgtMappings, loaderType);

            if (dryRun) { printDryRunSummary(diff); continue; }

            // 输出路径
            Path out;
            if (batch && isDir) out = Path.of(inputPath + "-" + tgtStr);
            else if (isDir) out = outputPath != null ? outputPath : Path.of(inputPath + "-migrated");
            else {
                String base = inputPath.getFileName().toString().replaceAll("\\.jar$", "");
                out = Path.of(base + "-" + tgtStr + ".jar");
            }

            MigrationConfig cfg = MigrationConfig.builder()
                    .sourceVersion(srcVer).targetVersion(tgtVer).loaderType(loaderType)
                    .inputPath(inputPath).outputPath(out).cacheDir(cache)
                    .verbose(verbose).dryRun(dryRun)
                    .threads(threads != null ? threads : Runtime.getRuntime().availableProcessors())
                    .build();

            if (isDir && batch && !inPlace) {
                migrateWithGitBranch(inputPath, out, cfg, diff, tgtStr);
            } else {
                new TransformationEngine(cfg, diff).run();
            }
        }
        return 0;
    }

    // =====================================================
    // Helpers
    // =====================================================

    private void validateInputs() {
        if (!Files.exists(inputPath)) {
            System.err.println("Error: Input path does not exist: " + inputPath); System.exit(1);
        }
        try { new Version(sourceVersion); } catch (Exception e) {
            System.err.println("Error: Invalid source version: " + sourceVersion); System.exit(1);
        }
        for (String tv : targetVersions) {
            try { new Version(tv); } catch (Exception e) {
                System.err.println("Error: Invalid target version: " + tv); System.exit(1);
            }
        }
        try { LoaderType.fromString(loader); } catch (Exception e) {
            System.err.println("Error: " + e.getMessage()); System.exit(1);
        }
        String name = inputPath.toString().toLowerCase();
        if (!name.endsWith(".jar") && !name.endsWith(".zip") && !inputPath.toFile().isDirectory()) {
            System.err.println("Error: Input must be a .jar/.zip file or a directory"); System.exit(1);
        }
        if (outputPath == null && targetVersions.size() == 1 && inputPath.toFile().isDirectory()) {
            outputPath = Path.of(inputPath + "-migrated");
        }
        if (cacheDir == null) {
            cacheDir = Path.of(System.getProperty("user.home"), ".splice", "mappings");
        }
    }

    private MappingService createMappingService(LoaderType lt, MappingDownloader dl) {
        return lt == LoaderType.FORGE ? new MCPMappingService(dl) : new YarnMappingService(dl);
    }

    private List<MappingEntry> loadSourceMappings(Version ver, LoaderType lt, Path cache) {
        MappingType mt = MappingType.fromLoader(lt);
        if (mappingsDir != null) {
            return new LocalMappingService(mappingsDir, ver, new Version("0.0.0"), mt).loadFromDirectory(
                    Path.of(mappingsDir.toString(), ver.getRaw()));
        }
        return createMappingService(lt, new MappingDownloader()).loadMappings(ver, cache);
    }

    private List<MappingEntry> loadTargetMappings(Version ver, LoaderType lt, Path cache) {
        MappingType mt = MappingType.fromLoader(lt);
        if (mappingsDir != null) {
            return new LocalMappingService(mappingsDir, new Version("0.0.0"), ver, mt).loadFromDirectory(
                    Path.of(mappingsDir.toString(), ver.getRaw()));
        }
        return createMappingService(lt, new MappingDownloader()).loadMappings(ver, cache);
    }

    /** 源码目录 + 多个版本：每条目标创建一个 git 分支 */
    private void migrateWithGitBranch(Path srcDir, Path outDir, MigrationConfig cfg, MappingDiff diff, String version) {
        String branchName = "splice/" + cfg.getSourceVersion() + "-to-" + version;
        try {
            LOG.info("创建分支: {}", branchName);
            runGit(srcDir, "stash");
            runGit(srcDir, "checkout", "-b", branchName);
            new TransformationEngine(cfg, diff).run();
            runGit(srcDir, "add", ".");
            runGit(srcDir, "commit", "-m", "Splice: auto-migrate to " + version);
            LOG.info("✓ 分支 {} 已就绪", branchName);
        } catch (Exception e) {
            LOG.error("Git 操作失败: {}", e.getMessage());
        } finally {
            try { runGit(srcDir, "checkout", "-"); } catch (Exception ignored) {}
            try { runGit(srcDir, "stash", "pop"); } catch (Exception ignored) {}
        }
    }

    private void runGit(Path dir, String... args) throws Exception {
        var cmd = new java.util.ArrayList<String>();
        cmd.add("git"); cmd.addAll(java.util.List.of(args));
        new ProcessBuilder(cmd).directory(dir.toFile()).inheritIO().start().waitFor();
    }

    private void printDryRunSummary(MappingDiff diff) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("  DRY RUN SUMMARY");
        System.out.println("=".repeat(60));
        System.out.println("  Source: " + diff.getSourceVersion());
        System.out.println("  Target: " + diff.getTargetVersion());
        System.out.println("  Loader: " + diff.getLoaderType());
        System.out.println();
        System.out.println("  Changes to apply:");
        System.out.println("    Classes: " + diff.getClassMappings().size());
        System.out.println("    Methods: " + diff.getMethodMappings().size());
        System.out.println("    Fields:  " + diff.getFieldMappings().size());
        System.out.println("    Removed: " + diff.getRemovedEntries().size());
        System.out.println("    Added:   " + diff.getAddedEntries().size());
        System.out.println();
        System.out.println("  Input:  " + inputPath);
        System.out.println("  Output: " + outputPath);
        System.out.println();
        System.out.println("  Run without --dry-run to apply these changes.");
        System.out.println("=".repeat(60));
    }

    // =====================================================
    // ASCII Banner
    // =====================================================

    static class SpliceBanner {
        static void print() {
            System.out.println();
            System.out.println("   _____       ___         ");
            System.out.println("  / ___/____  / (_)_______ ");
            System.out.println("  \\__ \\/ __ \\/ / / ___/ _ \\");
            System.out.println(" ___/ / /_/ / / / /__/  __/");
            System.out.println("/____/ .___/_/_/\\___/\\___/ ");
            System.out.println("    /_/                    ");
            System.out.println("  Minecraft Mod Migration Tool  v1.0.0");
            System.out.println();
        }
    }
}
