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
            description = "Target Minecraft version (e.g., 1.21)")
    private String targetVersion;

    @Option(names = {"-l", "--loader"},
            description = "Mod loader type: forge or fabric")
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

        // 交互式向导模式
        if (interactive) {
            new InteractiveMode().start();
            return 0;
        }

        // 非交互模式：校验必填参数
        if (sourceVersion == null || targetVersion == null || loader == null || inputPath == null) {
            System.err.println("错误: 非交互模式需要 -s, -t, -l, -i 四个参数");
            System.err.println("使用 --help 查看帮助，或使用 -I 进入交互式向导");
            return 1;
        }

        LOG.info("Splice v1.0.0 - Minecraft Mod Migration Tool");

        validateInputs();

        // 2. Build configuration
        Version srcVersion = new Version(sourceVersion);
        Version tgtVersion = new Version(targetVersion);
        LoaderType loaderType = LoaderType.fromString(loader);

        MigrationConfig config = MigrationConfig.builder()
                .sourceVersion(srcVersion)
                .targetVersion(tgtVersion)
                .loaderType(loaderType)
                .inputPath(inputPath)
                .outputPath(outputPath)
                .cacheDir(cacheDir)
                .verbose(verbose)
                .dryRun(dryRun)
                .threads(threads != null ? threads : Runtime.getRuntime().availableProcessors())
                .build();

        LOG.info("Configuration: {}", config);

        // 3. Load mappings (local or remote)
        List<MappingEntry> sourceMappings;
        List<MappingEntry> targetMappings;

        if (mappingsDir != null) {
            // Offline mode: load from local directory
            if (!Files.isDirectory(mappingsDir)) {
                LOG.error("Mappings directory not found: {}", mappingsDir);
                System.exit(1);
            }
            MappingType mt = MappingType.fromLoader(loaderType);
            LocalMappingService localService = new LocalMappingService(mappingsDir, srcVersion, tgtVersion, mt);

            LOG.info("Loading local mappings from: {} (source={}, target={})",
                    mappingsDir, srcVersion, tgtVersion);

            sourceMappings = localService.loadFromDirectory(localService.getSourceDir());
            targetMappings = localService.loadFromDirectory(localService.getTargetDir());

            LOG.info("Loaded {} source + {} target entries from local files",
                    sourceMappings.size(), targetMappings.size());
        } else {
            // Online mode: download from official sources
            MappingDownloader downloader = new MappingDownloader();
            MappingService mappingService = createMappingService(loaderType, downloader);

            LOG.info("Loading {} mappings for {}...", mappingService.getProviderName(), sourceVersion);
            sourceMappings = mappingService.loadMappings(srcVersion, config.getCacheDir());
            LOG.info("Loaded {} entries for {}", sourceMappings.size(), sourceVersion);

            LOG.info("Loading {} mappings for {}...", mappingService.getProviderName(), targetVersion);
            targetMappings = mappingService.loadMappings(tgtVersion, config.getCacheDir());
            LOG.info("Loaded {} entries for {}", targetMappings.size(), targetVersion);
        }

        // 4. Compute diff
        MappingDiffEngine diffEngine = new MappingDiffEngine();
        MappingDiff diff = diffEngine.computeDiff(srcVersion, tgtVersion,
                sourceMappings, targetMappings, loaderType);

        if (!diff.hasChanges() && !diff.getRemovedEntries().isEmpty()) {
            LOG.warn("No direct mapping changes found, but {} symbols were removed. " +
                    "These may break compilation.", diff.getRemovedEntries().size());
        }

        // Print mapping diff summary
        LOG.info("Class mappings: {}", diff.getClassMappings().size());
        LOG.info("Method mappings: {}", diff.getMethodMappings().size());
        LOG.info("Field mappings: {}", diff.getFieldMappings().size());
        if (!diff.getAmbiguousMappings().isEmpty()) {
            LOG.warn("Ambiguous mappings: {}", diff.getAmbiguousMappings().size());
            diff.getAmbiguousMappings().forEach(a ->
                    LOG.warn("  {}: {} -> {} or {}",
                            a.type(), a.source(), a.targetA(), a.targetB()));
        }

        if (dryRun) {
            LOG.info("[DRY-RUN] Migration preview completed. Use --dry-run to preview.");
            printDryRunSummary(diff);
            return 0;
        }

        // 5. Run transformation
        TransformationEngine engine = new TransformationEngine(config, diff);
        int filesProcessed = engine.run();

        // 6. Print final message
        LOG.info("Migration complete! Processed {} files.", filesProcessed);
        LOG.info("Output: {}", config.getOutputPath());
        LOG.info("Report: {}/migration-report.json", config.getOutputPath());

        return 0;
    }

    // =====================================================
    // Helpers
    // =====================================================

    private void validateInputs() {
        // Validate path exists
        if (!Files.exists(inputPath)) {
            System.err.println("Error: Input path does not exist: " + inputPath);
            System.exit(1);
        }

        // Validate version format
        try {
            new Version(sourceVersion);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: Invalid source version format: " + sourceVersion);
            System.err.println("  Expected format: major.minor.patch (e.g., 1.20.1)");
            System.exit(1);
        }

        try {
            new Version(targetVersion);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: Invalid target version format: " + targetVersion);
            System.err.println("  Expected format: major.minor.patch (e.g., 1.20.1)");
            System.exit(1);
        }

        // Validate loader type
        try {
            LoaderType.fromString(loader);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }

        // Validate input type
        boolean isJar = inputPath.toString().toLowerCase().endsWith(".jar");
        boolean isDir = inputPath.toFile().isDirectory();
        if (!isJar && !isDir) {
            System.err.println("Error: Input must be a .jar file or a directory");
            System.exit(1);
        }

        // Set default output path if not specified
        if (outputPath == null) {
            outputPath = Path.of(inputPath + "-migrated");
        }

        // Set default cache dir if not specified
        if (cacheDir == null) {
            cacheDir = Path.of(System.getProperty("user.home"), ".splice", "mappings");
        }
    }

    private MappingService createMappingService(LoaderType loaderType, MappingDownloader downloader) {
        return switch (loaderType) {
            case FORGE -> new MCPMappingService(downloader);
            case FABRIC -> new YarnMappingService(downloader);
        };
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
