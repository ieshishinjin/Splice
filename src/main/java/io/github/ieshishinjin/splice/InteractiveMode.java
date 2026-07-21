package io.github.ieshishinjin.splice;

import io.github.ieshishinjin.splice.mapping.*;
import io.github.ieshishinjin.splice.mapping.local.LocalMappingService;
import io.github.ieshishinjin.splice.model.*;
import io.github.ieshishinjin.splice.reporter.ConflictReporter;
import io.github.ieshishinjin.splice.transformer.TransformationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 交互式向导模式 — 逐步询问配置，:wq 退出。
 */
public class InteractiveMode {

    private static final Logger LOG = LoggerFactory.getLogger(InteractiveMode.class);

    private final Scanner scanner;
    private final ConflictReporter conflictReporter;
    private boolean running;
    private Messages msg;

    private Version sourceVersion;
    private Version targetVersion;
    private LoaderType loaderType;
    private Path inputPath;
    private Path outputPath;
    private Path cacheDir;
    private Path mappingsDir;
    private List<MappingEntry> sourceMappings;
    private List<MappingEntry> targetMappings;
    private MappingDiff lastDiff;

    public InteractiveMode() {
        this.scanner = new Scanner(System.in);
        this.conflictReporter = new ConflictReporter();
        this.running = true;
        this.cacheDir = Path.of(System.getProperty("user.home"), ".splice", "mappings");
        this.msg = Messages.get(); // 默认中文，selectLanguage 中切换
    }

    public void start() {
        // 选语言
        selectLanguage();
        System.out.println("\n  " + msg.get("wizard.hint") + "\n");

        while (running) {
            showMainMenu();
            String input = prompt(msg.get("menu.prompt")).trim();

            if (input.equals(":wq")) {
                running = false;
                System.out.println(msg.get("wizard.exit"));
                continue;
            }

            try {
                switch (input) {
                    case "1" -> configureVersions();
                    case "2" -> configureLoader();
                    case "3" -> configureInput();
                    case "4" -> configureOutput();
                    case "5" -> loadMappings();
                    case "6" -> runMigration();
                    case "7" -> viewReport();
                    default -> System.out.println(msg.get("invalid.choice"));
                }
            } catch (Exception e) {
                System.err.println(msg.get("migrate.failed", e.getMessage()));
                LOG.error("Interactive error", e);
            }

            if (running) {
                System.out.println();
                pressEnter();
            }
        }
        scanner.close();
    }

    // ==================== 语言选择 ====================

    private void selectLanguage() {
        System.out.println("\n" + msg("lang.select"));
        System.out.println("  1. 中文");
        System.out.println("  2. English");
        String c = prompt("1/2").trim();

        String lang;
        if ("2".equals(c)) {
            lang = "en";
        } else {
            lang = "zh";
        }
        Messages.init(lang);
        this.msg = Messages.get();
        System.out.println("  → " + this.msg.get("lang.name"));
    }

    // ==================== 主菜单 ====================

    private void showMainMenu() {
        System.out.println("\n" + "─".repeat(50));
        System.out.println("  " + msg("wizard.title") + "    (:wq " + msg("wizard.exit") + ")");
        System.out.println("─".repeat(50));
        System.out.println("  1. " + pad(20, msg("menu.version")) + " " + status(sourceVersion != null,
                fmt(sourceVersion) + " " + msg("status.arrow") + " " + fmt(targetVersion)));
        System.out.println("  2. " + pad(20, msg("menu.loader")) + " " + status(loaderType != null, fmt(loaderType)));
        System.out.println("  3. " + pad(20, msg("menu.input")) + " " + status(inputPath != null, fmt(inputPath)));
        System.out.println("  4. " + pad(20, msg("menu.output")) + " " + status(outputPath != null, fmt(outputPath)));
        System.out.println("  5. " + pad(20, msg("menu.mappings")) + " " + status(sourceMappings != null,
                sourceMappings != null ? sourceMappings.size() + " " + msg("status.entries") : ""));
        System.out.println("  6. " + pad(20, msg("menu.migrate")) + " " + status(lastDiff != null,
                lastDiff != null ? lastDiff.getTotalChanges() + " " + msg("status.changes") : ""));
        System.out.println("  7. " + pad(20, msg("menu.report")));
        System.out.println("─".repeat(50));
    }

    // ==================== 配置步骤 ====================

    private void configureVersions() {
        System.out.println("\n-- " + msg("step.version") + " --");
        String src = prompt(msg("step.version.src"));
        if (":wq".equals(src)) { running = false; return; }
        String tgt = prompt(msg("step.version.tgt"));
        if (":wq".equals(tgt)) { running = false; return; }
        try {
            sourceVersion = new Version(src);
            targetVersion = new Version(tgt);
            System.out.println("✓ " + msg("step.version.done", sourceVersion.toString(), targetVersion.toString()));
            sourceMappings = null; targetMappings = null; lastDiff = null;
        } catch (IllegalArgumentException e) {
            System.err.println(msg("error.version", e.getMessage()));
        }
    }

    private void configureLoader() {
        System.out.println("\n-- " + msg("step.loader") + " --");
        System.out.println("  1. " + msg("step.loader.forge"));
        System.out.println("  2. " + msg("step.loader.fabric"));
        String c = prompt("1/2").trim();
        if (":wq".equals(c)) { running = false; return; }
        loaderType = "2".equals(c) ? LoaderType.FABRIC : LoaderType.FORGE;
        System.out.println("✓ " + msg("step.loader.done", loaderType));
        sourceMappings = null; targetMappings = null; lastDiff = null;
    }

    private void configureInput() {
        System.out.println("\n-- " + msg("menu.input") + " --");
        System.out.println("  " + msg("step.input"));
        String p = prompt(msg("step.input.prompt"));
        if (":wq".equals(p)) { running = false; return; }
        Path path = Path.of(p);
        if (!Files.exists(path)) {
            System.out.println(msg("error.notfound", path));
            return;
        }
        inputPath = path;
        System.out.println("✓ " + msg("step.input.done", inputPath));
        if (outputPath == null) {
            outputPath = Path.of(inputPath + "-migrated");
            System.out.println("  " + msg("step.input.auto", outputPath));
        }
    }

    private void configureOutput() {
        System.out.println("\n-- " + msg("menu.output") + " --");
        String dflt = inputPath != null ? inputPath + "-migrated" : "./splice-output";
        String p = prompt(msg("step.output.prompt") + " (" + dflt + ")");
        if (":wq".equals(p)) { running = false; return; }
        outputPath = p.isBlank() ? Path.of(dflt) : Path.of(p);
        System.out.println("✓ " + msg("step.output.done", outputPath));
    }

    // ==================== 映射加载 ====================

    private void loadMappings() {
        if (sourceVersion == null || targetVersion == null) {
            System.out.println(msg("error.config", msg("menu.version")));
            return;
        }
        if (loaderType == null) {
            System.out.println(msg("error.config", msg("menu.loader")));
            return;
        }
        System.out.println("\n-- " + msg("menu.mappings") + " --");
        System.out.println("  1. " + msg("mappings.load.download"));
        System.out.println("  2. " + msg("mappings.load.local"));
        String c = prompt(msg("mappings.load.choose")).trim();
        if (":wq".equals(c)) { running = false; return; }
        try {
            if ("2".equals(c)) {
                loadLocal();
            } else {
                downloadRemote();
            }
        } catch (Exception e) {
            System.err.println(msg("error.load", e.getMessage()));
        }
    }

    private void downloadRemote() {
        MappingDownloader dl = new MappingDownloader();
        MappingService svc = loaderType == LoaderType.FORGE
                ? new MCPMappingService(dl) : new YarnMappingService(dl);

        System.out.println("  " + msg("mappings.source", sourceVersion));
        sourceMappings = svc.loadMappings(sourceVersion, cacheDir);
        System.out.println("  " + msg("mappings.done", sourceMappings.size()));

        System.out.println("  " + msg("mappings.target", targetVersion));
        targetMappings = svc.loadMappings(targetVersion, cacheDir);
        System.out.println("  " + msg("mappings.done", targetMappings.size()));

        computeDiff();
    }

    private void loadLocal() {
        String dir = prompt(msg("mappings.local.prompt"));
        if (":wq".equals(dir)) { running = false; return; }
        mappingsDir = Path.of(dir);
        if (!Files.isDirectory(mappingsDir)) {
            System.out.println(msg("mapping.dir.notfound"));
            return;
        }
        MappingType mt = MappingType.fromLoader(loaderType);
        LocalMappingService local = new LocalMappingService(mappingsDir, sourceVersion, targetVersion, mt);
        sourceMappings = local.loadFromDirectory(local.getSourceDir());
        targetMappings = local.loadFromDirectory(local.getTargetDir());
        System.out.println("  " + msg("mappings.local.done", sourceMappings.size(), targetMappings.size()));
        computeDiff();
    }

    private void computeDiff() {
        if (sourceMappings == null || targetMappings == null) return;
        System.out.println("  " + msg("mappings.computing"));
        MappingDiffEngine engine = new MappingDiffEngine();
        lastDiff = engine.computeDiff(sourceVersion, targetVersion, sourceMappings, targetMappings, loaderType);

        System.out.println("  ✓ " + msg("mappings.class") + ": " + lastDiff.getClassMappings().size());
        System.out.println("  ✓ " + msg("mappings.method") + ": " + lastDiff.getMethodMappings().size());
        System.out.println("  ✓ " + msg("mappings.field") + ": " + lastDiff.getFieldMappings().size());
        if (!lastDiff.getAmbiguousMappings().isEmpty()) {
            System.out.println("  ⚠ " + msg("mappings.ambiguous", lastDiff.getAmbiguousMappings().size()));
        }
    }

    // ==================== 执行迁移 ====================

    private void runMigration() {
        if (lastDiff == null || !lastDiff.hasChanges()) {
            System.out.println(msg("mappings.empty"));
            return;
        }
        if (inputPath == null) {
            System.out.println(msg("error.input"));
            return;
        }

        System.out.println("\n-- " + msg("migrate.preview") + " --");
        System.out.println("  " + sourceVersion + " → " + targetVersion);
        System.out.println("  " + lastDiff.getTotalChanges() + " " + msg("status.changes"));
        System.out.println("  " + msg("step.input.done", inputPath));
        System.out.println("  " + msg("step.output.done", outputPath));
        System.out.println();

        System.out.println("  " + msg("migrate.scope"));
        System.out.println("  1. " + msg("migrate.scope.all"));
        System.out.println("  2. " + msg("migrate.scope.source"));
        System.out.println("  3. " + msg("migrate.scope.bytecode"));
        System.out.println("  4. " + msg("migrate.scope.meta"));
        System.out.println("  5. " + msg("migrate.scope.report"));
        String choice = prompt("1-5").trim();
        if (":wq".equals(choice)) { running = false; return; }

        String confirm = prompt(msg("migrate.confirm"));
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println(msg("migrate.cancelled"));
            return;
        }

        try {
            MigrationConfig config = MigrationConfig.builder()
                    .sourceVersion(sourceVersion).targetVersion(targetVersion)
                    .loaderType(loaderType).inputPath(inputPath).outputPath(outputPath)
                    .cacheDir(cacheDir).build();
            TransformationEngine engine = new TransformationEngine(config, lastDiff);
            int count = engine.run();
            System.out.println("\n" + msg("migrate.done", count));
            showPostMenu();
        } catch (Exception e) {
            System.err.println(msg("migrate.failed", e.getMessage()));
            LOG.error("Migration failed", e);
        }
    }

    // ==================== 后续菜单 ====================

    private void showPostMenu() {
        while (true) {
            System.out.println("\n── " + msg("post.title") + " ──");
            System.out.println("  1. " + msg("post.report"));
            System.out.println("  2. " + msg("post.detail"));
            System.out.println("  3. " + msg("post.export"));
            System.out.println("  4. " + msg("post.retry"));
            System.out.println("  5. " + msg("post.back"));
            System.out.println("  6. " + msg("post.exit"));

            String ch = prompt(msg("post.prompt")).trim();
            if (":wq".equals(ch) || "6".equals(ch)) {
                running = false;
                System.out.println(msg("wizard.exit"));
                return;
            }
            switch (ch) {
                case "1" -> viewReport();
                case "2" -> viewDetails();
                case "3" -> exportDiff();
                case "4" -> { return; }
                case "5" -> { return; }
                default -> System.out.println(msg("invalid.choice"));
            }
        }
    }

    private void viewReport() {
        if (outputPath != null) {
            Path report = outputPath.resolve("migration-report.json");
            if (Files.exists(report)) {
                try {
                    System.out.println("\n-- " + msg("report.title") + " --");
                    String[] lines = Files.readString(report).split("\n");
                    for (int i = 0; i < Math.min(lines.length, 30); i++) {
                        System.out.println("  " + lines[i]);
                    }
                    if (lines.length > 30) System.out.println("  ... (total " + lines.length + " lines)");
                } catch (Exception e) {
                    System.out.println(msg("export.failed", e.getMessage()));
                }
            } else {
                System.out.println(msg("report.empty"));
            }
        } else if (lastDiff != null) {
            conflictReporter.printSummary(0, List.of(), lastDiff);
        }
    }

    private void viewDetails() {
        if (lastDiff == null) { System.out.println(msg("report.empty")); return; }
        System.out.println("\n-- " + msg("detail.title") + " --");
        if (!lastDiff.getClassMappings().isEmpty()) {
            System.out.println("\n" + msg("detail.class"));
            lastDiff.getClassMappings().entrySet().stream().limit(20)
                    .forEach(e -> System.out.println("  " + e.getKey() + " → " + e.getValue()));
        }
        if (!lastDiff.getMethodMappings().isEmpty()) {
            System.out.println("\n" + msg("detail.method"));
            lastDiff.getMethodMappings().entrySet().stream().limit(20)
                    .forEach(e -> System.out.println("  " + e.getKey() + " → " + e.getValue()));
        }
        if (!lastDiff.getFieldMappings().isEmpty()) {
            System.out.println("\n" + msg("detail.field"));
            lastDiff.getFieldMappings().entrySet().stream().limit(20)
                    .forEach(e -> System.out.println("  " + e.getKey() + " → " + e.getValue()));
        }
        if (!lastDiff.getRemovedEntries().isEmpty()) {
            System.out.println("\n" + msg("detail.removed"));
            lastDiff.getRemovedEntries().stream().limit(10)
                    .forEach(e -> System.out.println("  [" + e.getType() + "] " + e.getIntermediateName()));
        }
    }

    private void exportDiff() {
        if (lastDiff == null) { System.out.println(msg("report.empty")); return; }
        String path = prompt(msg("export.prompt"));
        if (":wq".equals(path)) { running = false; return; }
        if (path.isBlank()) path = "./splice-diff.txt";
        try (var w = Files.newBufferedWriter(Path.of(path))) {
            w.write("Splice Diff: " + sourceVersion + " -> " + targetVersion + "\n\n");
            w.write("=== Classes ===\n"); lastDiff.getClassMappings().forEach((k, v) -> write(w, k + " -> " + v));
            w.write("\n=== Methods ===\n"); lastDiff.getMethodMappings().forEach((k, v) -> write(w, k + " -> " + v));
            w.write("\n=== Fields ===\n"); lastDiff.getFieldMappings().forEach((k, v) -> write(w, k + " -> " + v));
            System.out.println(msg("export.done", path));
        } catch (Exception e) {
            System.err.println(msg("export.failed", e.getMessage()));
        }
    }

    private void write(java.io.BufferedWriter w, String s) {
        try { w.write(s + "\n"); } catch (Exception e) { /* ignore */ }
    }

    // ==================== 工具 ====================

    private String prompt(String text) {
        System.out.print("  > " + text + ": ");
        return scanner.nextLine();
    }

    private String status(boolean ok, String detail) {
        return (ok ? "✓ " : "  ") + detail;
    }

    private String fmt(Object o) {
        return o != null ? o.toString() : "(" + msg("status.unset") + ")";
    }

    private String msg(String key, Object... args) {
        return msg.get(key, args);
    }

    private String pad(int len, String s) {
        return s.length() < len ? s + " ".repeat(len - s.length()) : s;
    }

    private void pressEnter() {
        System.out.print("  " + msg("press.enter"));
        scanner.nextLine();
    }
}
