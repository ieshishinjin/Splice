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
 * 交互式向导模式 — 逐步询问配置，执行后返回菜单继续操作。
 */
public class InteractiveMode {

    private static final Logger LOG = LoggerFactory.getLogger(InteractiveMode.class);

    private final Scanner scanner;
    private final ConflictReporter conflictReporter;
    private boolean running;

    // 当前配置，可以跨操作复用
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
    }

    /** 启动交互式向导 */
    public void start() {
        System.out.println("  交互式迁移向导已启动 (输入 exit 退出)\n");

        while (running) {
            showMainMenu();
            String choice = prompt("选择操作 [1-8]").trim();

            try {
                switch (choice) {
                    case "1" -> configureVersions();
                    case "2" -> configureLoader();
                    case "3" -> configureInput();
                    case "4" -> configureOutput();
                    case "5" -> loadMappings();
                    case "6" -> runMigration();
                    case "7" -> viewReport();
                    case "8", "exit", "q" -> {
                        running = false;
                        System.out.println("再见！");
                    }
                    default -> System.out.println("无效选项，请重试。");
                }
            } catch (Exception e) {
                System.err.println("出错: " + e.getMessage());
                LOG.error("Interactive error", e);
            }

            if (running) {
                System.out.println();
                pressEnter();
            }
        }
        scanner.close();
    }

    // =====================================================
    // 菜单
    // =====================================================

    private void showMainMenu() {
        System.out.println("\n" + "─".repeat(50));
        System.out.println("  SPLICE 交互式迁移向导");
        System.out.println("─".repeat(50));
        System.out.println("  1. 配置版本         " + status(sourceVersion != null,
                fmt(sourceVersion) + " → " + fmt(targetVersion)));
        System.out.println("  2. 配置加载器       " + status(loaderType != null, fmt(loaderType)));
        System.out.println("  3. 配置输入路径     " + status(inputPath != null, fmt(inputPath)));
        System.out.println("  4. 配置输出路径     " + status(outputPath != null, fmt(outputPath)));
        System.out.println("  5. 加载映射表       " + status(sourceMappings != null,
                (sourceMappings != null ? sourceMappings.size() + " 条目" : "")));
        System.out.println("  6. ▶ 执行迁移       " + status(lastDiff != null,
                (lastDiff != null ? lastDiff.getTotalChanges() + " 处变更" : "")));
        System.out.println("  7. 查看迁移报告");
        System.out.println("  8. 退出");
        System.out.println("─".repeat(50));
    }

    // =====================================================
    // 配置步骤
    // =====================================================

    private void configureVersions() {
        System.out.println("\n-- 配置版本 --");
        String src = prompt("源版本 (如 1.20.1)");
        if (src.equalsIgnoreCase("exit")) return;
        String tgt = prompt("目标版本 (如 1.21)");
        if (tgt.equalsIgnoreCase("exit")) return;

        try {
            sourceVersion = new Version(src);
            targetVersion = new Version(tgt);
            System.out.println("✓ 版本已配置: " + sourceVersion + " → " + targetVersion);
            // 清空旧映射
            sourceMappings = null;
            targetMappings = null;
            lastDiff = null;
        } catch (IllegalArgumentException e) {
            System.err.println("✗ 版本格式错误: " + e.getMessage());
        }
    }

    private void configureLoader() {
        System.out.println("\n-- 配置加载器 --");
        System.out.println("  1. Forge");
        System.out.println("  2. Fabric");
        String choice = prompt("选择 [1/2]");
        if (choice.equalsIgnoreCase("exit")) return;

        loaderType = switch (choice) {
            case "1" -> LoaderType.FORGE;
            case "2" -> LoaderType.FABRIC;
            default -> {
                System.out.println("无效选择，使用 Forge");
                yield LoaderType.FORGE;
            }
        };
        System.out.println("✓ 加载器: " + loaderType);
        sourceMappings = null;
        targetMappings = null;
        lastDiff = null;
    }

    private void configureInput() {
        System.out.println("\n-- 配置输入路径 --");
        System.out.println("  输入可以是: 源码目录 或 .jar 文件");
        String path = prompt("路径");
        if (path.equalsIgnoreCase("exit")) return;

        Path p = Path.of(path);
        if (!Files.exists(p)) {
            System.out.println("✗ 路径不存在: " + p);
            return;
        }
        inputPath = p;
        System.out.println("✓ 输入: " + inputPath);

        // 自动设置默认输出
        if (outputPath == null) {
            outputPath = Path.of(inputPath + "-migrated");
            System.out.println("  (自动设置输出: " + outputPath + ")");
        }
    }

    private void configureOutput() {
        System.out.println("\n-- 配置输出路径 --");
        String path = prompt("路径 (留空使用默认: " +
                (inputPath != null ? inputPath + "-migrated" : "./splice-output") + ")");
        if (path.equalsIgnoreCase("exit")) return;

        if (path.isBlank()) {
            outputPath = inputPath != null
                    ? Path.of(inputPath + "-migrated")
                    : Path.of("./splice-output");
        } else {
            outputPath = Path.of(path);
        }
        System.out.println("✓ 输出: " + outputPath);
    }

    // =====================================================
    // 映射加载
    // =====================================================

    private void loadMappings() {
        if (sourceVersion == null || targetVersion == null) {
            System.out.println("✗ 请先配置版本 (选项 1)");
            return;
        }
        if (loaderType == null) {
            System.out.println("✗ 请先配置加载器 (选项 2)");
            return;
        }

        System.out.println("\n-- 加载映射表 --");
        System.out.println("  1. 从网络自动下载");
        System.out.println("  2. 从本地目录加载");
        String choice = prompt("选择 [1/2]");
        if (choice.equalsIgnoreCase("exit")) return;

        try {
            if ("2".equals(choice)) {
                loadLocalMappings();
            } else {
                downloadMappings();
            }
        } catch (Exception e) {
            System.err.println("✗ 加载失败: " + e.getMessage());
        }
    }

    private void downloadMappings() {
        System.out.println("正在下载映射表...");
        MappingDownloader downloader = new MappingDownloader();
        MappingService service = loaderType == LoaderType.FORGE
                ? new MCPMappingService(downloader)
                : new YarnMappingService(downloader);

        System.out.println("  下载 " + sourceVersion + " 映射...");
        sourceMappings = service.loadMappings(sourceVersion, cacheDir);
        System.out.println("  ✓ " + sourceMappings.size() + " 条");

        System.out.println("  下载 " + targetVersion + " 映射...");
        targetMappings = service.loadMappings(targetVersion, cacheDir);
        System.out.println("  ✓ " + targetMappings.size() + " 条");

        computeDiff();
    }

    private void loadLocalMappings() {
        String dir = prompt("映射目录路径");
        if (dir.equalsIgnoreCase("exit")) return;
        mappingsDir = Path.of(dir);

        if (!Files.isDirectory(mappingsDir)) {
            System.out.println("✗ 目录不存在");
            return;
        }

        MappingType mt = MappingType.fromLoader(loaderType);
        LocalMappingService local = new LocalMappingService(mappingsDir, sourceVersion, targetVersion, mt);

        sourceMappings = local.loadFromDirectory(local.getSourceDir());
        targetMappings = local.loadFromDirectory(local.getTargetDir());
        System.out.println("  ✓ 源: " + sourceMappings.size() + " 条, 目标: " + targetMappings.size() + " 条");

        computeDiff();
    }

    private void computeDiff() {
        if (sourceMappings == null || targetMappings == null) return;

        System.out.println("正在计算差异...");
        MappingDiffEngine engine = new MappingDiffEngine();
        lastDiff = engine.computeDiff(sourceVersion, targetVersion, sourceMappings, targetMappings, loaderType);

        System.out.println("  ✓ 类变更: " + lastDiff.getClassMappings().size());
        System.out.println("  ✓ 方法变更: " + lastDiff.getMethodMappings().size());
        System.out.println("  ✓ 字段变更: " + lastDiff.getFieldMappings().size());

        if (!lastDiff.getAmbiguousMappings().isEmpty()) {
            System.out.println("  ⚠ 歧义映射: " + lastDiff.getAmbiguousMappings().size() + " 条（需手动检查）");
        }
    }

    // =====================================================
    // 执行迁移
    // =====================================================

    private void runMigration() {
        if (lastDiff == null || !lastDiff.hasChanges()) {
            System.out.println("⚠ 没有可应用的变更，请先加载映射表 (选项 5)");
            return;
        }
        if (inputPath == null) {
            System.out.println("✗ 请先配置输入路径 (选项 3)");
            return;
        }

        System.out.println("\n-- 迁移预览 --");
        System.out.println("  " + sourceVersion + " → " + targetVersion);
        System.out.println("  " + lastDiff.getTotalChanges() + " 处变更");
        System.out.println("  输入: " + inputPath);
        System.out.println("  输出: " + outputPath);
        System.out.println();

        // 选择迁移范围
        System.out.println("  迁移范围:");
        System.out.println("  1. 全部");

        boolean hasSrc = inputPath != null && inputPath.toFile().isDirectory()
                && hasJavaFiles(inputPath);
        boolean hasJar = inputPath.toString().toLowerCase().endsWith(".jar");

        List<String> options = new ArrayList<>();
        options.add("1. 全部迁移（源码 + 字节码 + 元数据）");
        if (hasSrc) options.add("2. 仅源码转换 (.java)");
        if (hasJar) options.add("3. 仅字节码转换 (.class)");
        options.add("4. 仅更新元数据 (mods.toml / fabric.mod.json)");
        options.add("5. 仅生成差异报告");
        options.forEach(System.out::println);

        String choice = prompt("选择 [1-5]");
        if (choice.equalsIgnoreCase("exit")) return;

        String confirm = prompt("确认执行？[y/N]");
        if (!confirm.equalsIgnoreCase("y")) {
            System.out.println("已取消");
            return;
        }

        try {
            MigrationConfig config = MigrationConfig.builder()
                    .sourceVersion(sourceVersion)
                    .targetVersion(targetVersion)
                    .loaderType(loaderType)
                    .inputPath(inputPath)
                    .outputPath(outputPath)
                    .cacheDir(cacheDir)
                    .verbose(false)
                    .dryRun(false)
                    .build();

            TransformationEngine engine = new TransformationEngine(config, lastDiff);
            int count = engine.run();

            // 迁移完成后提供后续选项
            System.out.println("\n✓ 迁移完成! 处理了 " + count + " 个文件");
            showPostMenu();

        } catch (Exception e) {
            System.err.println("✗ 迁移失败: " + e.getMessage());
            LOG.error("Migration failed", e);
        }
    }

    // =====================================================
    // 后续菜单
    // =====================================================

    private void showPostMenu() {
        while (true) {
            System.out.println("\n── 后续操作 ──");
            System.out.println("  1. 查看迁移报告");
            System.out.println("  2. 查看变更详情");
            System.out.println("  3. 导出差异文件");
            System.out.println("  4. 重新迁移（修改配置）");
            System.out.println("  5. 返回主菜单");
            System.out.println("  6. 退出");

            String ch = prompt("选择 [1-6]");
            switch (ch) {
                case "1" -> viewReport();
                case "2" -> viewDiffDetails();
                case "3" -> exportDiff();
                case "4" -> {
                    System.out.println("返回配置步骤，请修改后重新执行。");
                    return;
                }
                case "5", "" -> { return; }
                case "6", "exit" -> {
                    running = false;
                    return;
                }
                default -> System.out.println("无效选项");
            }
        }
    }

    private void viewReport() {
        if (outputPath != null) {
            Path report = outputPath.resolve("migration-report.json");
            if (Files.exists(report)) {
                try {
                    String content = Files.readString(report);
                    System.out.println("\n-- 迁移报告 --");
                    // 只显示摘要部分
                    String[] lines = content.split("\n");
                    for (int i = 0; i < Math.min(lines.length, 30); i++) {
                        System.out.println("  " + lines[i]);
                    }
                    if (lines.length > 30) {
                        System.out.println("  ... (共 " + lines.length + " 行，完整报告见文件)");
                    }
                } catch (Exception e) {
                    System.out.println("读取报告失败: " + e.getMessage());
                }
            } else {
                System.out.println("报告文件尚未生成。");
            }
        } else {
            // 命令行模式已有完整报告输出
            if (lastDiff != null) {
                conflictReporter.printSummary(0, List.of(), lastDiff);
            }
        }
    }

    private void viewDiffDetails() {
        if (lastDiff == null) {
            System.out.println("没有差异数据。");
            return;
        }

        System.out.println("\n-- 变更详情 --");
        if (!lastDiff.getClassMappings().isEmpty()) {
            System.out.println("\n类名变更:");
            lastDiff.getClassMappings().entrySet().stream()
                    .limit(20)
                    .forEach(e -> System.out.println("  " + e.getKey() + " → " + e.getValue()));
        }
        if (!lastDiff.getMethodMappings().isEmpty()) {
            System.out.println("\n方法名变更 (前20):");
            lastDiff.getMethodMappings().entrySet().stream()
                    .limit(20)
                    .forEach(e -> System.out.println("  " + e.getKey() + " → " + e.getValue()));
        }
        if (!lastDiff.getFieldMappings().isEmpty()) {
            System.out.println("\n字段名变更 (前20):");
            lastDiff.getFieldMappings().entrySet().stream()
                    .limit(20)
                    .forEach(e -> System.out.println("  " + e.getKey() + " → " + e.getValue()));
        }
        if (!lastDiff.getRemovedEntries().isEmpty()) {
            System.out.println("\n已移除符号 (前10):");
            lastDiff.getRemovedEntries().stream()
                    .limit(10)
                    .forEach(e -> System.out.println("  [" + e.getType() + "] " + e.getIntermediateName()));
        }
    }

    private void exportDiff() {
        if (lastDiff == null) {
            System.out.println("没有差异数据可导出。");
            return;
        }
        String path = prompt("导出路径 (留空: ./splice-diff.txt)");
        if (path.equalsIgnoreCase("exit")) return;
        if (path.isBlank()) path = "./splice-diff.txt";

        try (var writer = java.nio.file.Files.newBufferedWriter(Path.of(path))) {
            writer.write("Splice Migration Diff: " + sourceVersion + " -> " + targetVersion + "\n");
            writer.write("Loader: " + loaderType + "\n\n");

            writer.write("=== Class Changes ===\n");
            for (var e : lastDiff.getClassMappings().entrySet()) {
                writer.write(e.getKey() + " -> " + e.getValue() + "\n");
            }

            writer.write("\n=== Method Changes ===\n");
            for (var e : lastDiff.getMethodMappings().entrySet()) {
                writer.write(e.getKey() + " -> " + e.getValue() + "\n");
            }

            writer.write("\n=== Field Changes ===\n");
            for (var e : lastDiff.getFieldMappings().entrySet()) {
                writer.write(e.getKey() + " -> " + e.getValue() + "\n");
            }

            System.out.println("✓ 差异已导出到: " + path);
        } catch (Exception e) {
            System.err.println("导出失败: " + e.getMessage());
        }
    }

    // =====================================================
    // 工具
    // =====================================================

    private String prompt(String msg) {
        System.out.print("  > " + msg + ": ");
        return scanner.nextLine();
    }

    private String status(boolean set, String detail) {
        return (set ? "✓ " : "  ") + detail;
    }

    private String fmt(Object o) {
        return o != null ? o.toString() : "(未设置)";
    }

    private void pressEnter() {
        System.out.print("  按 Enter 继续...");
        scanner.nextLine();
    }

    private boolean hasJavaFiles(Path dir) {
        try (var walk = java.nio.file.Files.walk(dir, 10)) {
            return walk.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (Exception e) {
            return false;
        }
    }
}
