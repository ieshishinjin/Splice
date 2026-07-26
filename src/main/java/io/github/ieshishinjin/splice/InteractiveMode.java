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
    private List<String> targetVersions;
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
        if (!running) { System.out.println(msg.get("wizard.exit")); scanner.close(); return; }
        System.out.println("\n  " + msg.get("wizard.hint") + "\n");

        while (running) {
            showMainMenu();
            String input = promptNum(msg.get("menu.prompt"), 1, 7);

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
        String c = promptNum("1/2", 1, 2);
        if (":wq".equals(c)) { running = false; return; }

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
                fmt(sourceVersion) + " " + msg("status.arrow") + " " + fmtTgt()));
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
        String tgt = prompt("目标版本，多个用逗号分隔 (如 1.20.4,1.21)");
        if (":wq".equals(tgt)) { running = false; return; }
        try {
            sourceVersion = new Version(src);
            targetVersions = java.util.Arrays.stream(tgt.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
            System.out.println("✓ " + sourceVersion + " → " + String.join(", ", targetVersions));
            sourceMappings = null; targetMappings = null; lastDiff = null;
        } catch (IllegalArgumentException e) {
            System.err.println(msg("error.version", e.getMessage()));
        }
    }

    private void configureLoader() {
        System.out.println("\n-- " + msg("step.loader") + " --");
        System.out.println("  1. " + msg("step.loader.forge"));
        System.out.println("  2. " + msg("step.loader.fabric"));
        String c = promptNum("1/2", 1, 2);
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
        if (sourceVersion == null || targetVersions == null || targetVersions.isEmpty()) {
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
        String c = promptNum(msg("mappings.load.choose"), 1, 2);
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
    }

    private void loadLocal() {
        String dir = prompt(msg("mappings.local.prompt"));
        if (":wq".equals(dir)) { running = false; return; }
        mappingsDir = Path.of(dir);
        if (!Files.isDirectory(mappingsDir)) {
            System.out.println(msg("mapping.dir.notfound")); return;
        }
        MappingType mt = MappingType.fromLoader(loaderType);
        sourceMappings = new LocalMappingService(mappingsDir, sourceVersion,
                new Version("0.0.0"), mt)
                .loadFromDirectory(Path.of(mappingsDir.toString(), sourceVersion.getRaw()));
        System.out.println("  " + msg("mappings.done", sourceMappings.size()));
    }

    private void downloadTarget(Version ver) {
        try {
            MappingDownloader dl = new MappingDownloader();
            MappingService svc = loaderType == LoaderType.FORGE
                    ? new MCPMappingService(dl) : new YarnMappingService(dl);
            System.out.println("  " + msg("mappings.target", ver));
            targetMappings = svc.loadMappings(ver, cacheDir);
            System.out.println("  " + msg("mappings.done", targetMappings.size()));
        } catch (Exception e) {
            System.err.println("  ✗ 加载 " + ver + " 映射失败: " + e.getMessage());
        }
    }

    // ==================== 执行迁移（支持多版本） ====================

    private void runMigration() {
        if (inputPath == null) { System.out.println(msg("error.input")); return; }
        if (targetVersions == null || targetVersions.isEmpty()) {
            System.out.println(msg("mappings.empty")); return;
        }

        boolean isDir = inputPath.toFile().isDirectory();
        boolean batch = targetVersions.size() > 1;

        System.out.println("\n-- " + msg("migrate.preview") + " --");
        System.out.println("  " + sourceVersion + " → " + String.join(", ", targetVersions));
        System.out.println("  " + msg("step.input.done", inputPath));
        System.out.println("  " + (batch ? "(批量 " + targetVersions.size() + " 个版本)" : ""));
        if (isDir && batch) System.out.println("  源码模式：每个版本建一个 git 分支");

        String confirm = prompt(msg("migrate.confirm"));
        if (!confirm.equalsIgnoreCase("y")) { System.out.println(msg("migrate.cancelled")); return; }

        int ok = 0, fail = 0;
        for (String tgtStr : targetVersions) {
            Version tgtVer;
            try { tgtVer = new Version(tgtStr); } catch (Exception e) {
                System.err.println("✗ 无效版本: " + tgtStr); fail++; continue;
            }

            System.out.println("\n─── " + sourceVersion + " → " + tgtStr + " ───");

            // 加载目标映射 + 计算差异
            downloadTarget(tgtVer);
            if (targetMappings == null) { fail++; continue; }

            System.out.println("  " + msg("mappings.computing"));
            MappingDiff diff = new MappingDiffEngine().computeDiff(sourceVersion, tgtVer,
                    sourceMappings, targetMappings, loaderType);

            // 输出路径
            Path out;
            if (batch && isDir) out = Path.of(inputPath + "-" + tgtStr);
            else if (isDir) out = outputPath != null ? outputPath : Path.of(inputPath + "-migrated");
            else {
                String base = inputPath.getFileName().toString().replaceAll("\\.jar$", "");
                out = Path.of(base + "-" + tgtStr + ".jar");
            }

            MigrationConfig cfg = MigrationConfig.builder()
                    .sourceVersion(sourceVersion).targetVersion(tgtVer)
                    .loaderType(loaderType).inputPath(inputPath).outputPath(out)
                    .cacheDir(cacheDir).build();

            boolean useBranch = false;
            if (isDir && batch) {
                useBranch = "y".equalsIgnoreCase(prompt("git 分支模式？每个版本建一个分支 [y/N]"));
            }

            try {
                if (useBranch) {
                    String branch = "splice/" + sourceVersion + "-to-" + tgtStr;
                    System.out.println("  创建分支: " + branch);
                    runGit("stash"); runGit("checkout", "-b", branch);
                    new TransformationEngine(cfg, diff).run();
                    runGit("add", "."); runGit("commit", "-m", "Splice: auto-migrate to " + tgtStr);
                    System.out.println("  ✓ 分支 " + branch + " 已就绪");
                } else {
                    new TransformationEngine(cfg, diff).run();
                }
                ok++;
            } catch (Exception e) {
                System.err.println("✗ 迁移失败: " + e.getMessage());
                fail++;
            } finally {
                if (isDir && batch) {
                    try { runGit("checkout", "-"); } catch (Exception ignored) {}
                    try { runGit("stash", "pop"); } catch (Exception ignored) {}
                }
            }
        }
        System.out.println("\n✓ 完成: " + ok + " 成功, " + fail + " 失败");
        if (ok > 0) showPostMenu();
    }

    private void runGit(String... args) throws Exception {
        var cmd = new java.util.ArrayList<String>();
        cmd.add("git"); cmd.addAll(java.util.List.of(args));
        new ProcessBuilder(cmd).directory(inputPath.toFile()).inheritIO().start().waitFor();
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

            String ch = promptNum(msg("post.prompt"), 1, 6);
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
        System.out.println("\n-- " + msg("detail.title") + " --");
        System.out.println("  (迁移完成后查看 migration-report.json 获取详细信息)");
    }

    private void exportDiff() {
        System.out.println(msg("report.empty"));
        System.out.println("  迁移报告在输出目录下的 migration-report.json 中");
    }

    private void write(java.io.BufferedWriter w, String s) {
        try { w.write(s + "\n"); } catch (Exception e) { /* ignore */ }
    }

    // ==================== 工具 ====================

    private String prompt(String text) {
        System.out.print("  > " + text + ": ");
        return scanner.nextLine();
    }

    /** 只接受数字和 :wq，其它输入忽略并重新提示 */
    private String promptNum(String text, int min, int max) {
        while (true) {
            System.out.print("  > " + text + ": ");
            String in = scanner.nextLine().trim();
            if (in.equals(":wq")) return in;
            if (in.isEmpty()) continue;
            try {
                int n = Integer.parseInt(in);
                if (n >= min && n <= max) return in;
            } catch (NumberFormatException e) {
                // 非数字，继续循环
            }
            System.out.println("    请输入 " + min + "-" + max + " 之间的数字");
        }
    }

    private String status(boolean ok, String detail) {
        return (ok ? "✓ " : "  ") + detail;
    }

    private String fmt(Object o) {
        return o != null ? o.toString() : "(" + msg("status.unset") + ")";
    }

    private String fmtTgt() {
        if (targetVersions == null || targetVersions.isEmpty()) return "(" + msg("status.unset") + ")";
        return String.join(", ", targetVersions);
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
