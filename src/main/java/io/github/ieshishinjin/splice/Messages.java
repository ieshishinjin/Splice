package io.github.ieshishinjin.splice;

import java.util.*;

/**
 * 国际化支持 — 管理所有面向用户的字符串。
 * 支持中文 (zh) 和英文 (en)，启动时选择。
 */
public class Messages {

    private static Messages instance;
    private final String lang;
    private final Map<String, String> data;

    public Messages(String lang) {
        this.lang = lang;
        this.data = lang.startsWith("zh") ? zh() : en();
    }

    public static Messages get() {
        if (instance == null) instance = new Messages("zh");
        return instance;
    }

    public static void init(String lang) {
        instance = new Messages(lang);
    }

    public String get(String key, Object... args) {
        String msg = data.getOrDefault(key, data.getOrDefault(key, "?" + key));
        return args.length > 0 ? String.format(msg, args) : msg;
    }

    // ============== 中文 ==============

    private static Map<String, String> zh() {
        var m = new LinkedHashMap<String, String>();
        m.put("lang.name", "中文");
        m.put("lang.select", "选择语言 / Choose language:");
        m.put("lang.zh", "中文");
        m.put("lang.en", "English");
        m.put("wizard.title", "SPLICE 交互式迁移向导");
        m.put("wizard.hint", "输入 :wq 退出");
        m.put("wizard.exit", "再见！");
        m.put("menu.version", "配置版本");
        m.put("menu.loader", "配置加载器");
        m.put("menu.input", "配置输入路径");
        m.put("menu.output", "配置输出路径");
        m.put("menu.mappings", "加载映射表");
        m.put("menu.migrate", "▶ 执行迁移");
        m.put("menu.report", "查看迁移报告");
        m.put("menu.prompt", "选择操作 [1-7]");
        m.put("status.unset", "未设置");
        m.put("status.arrow", "→");
        m.put("status.entries", "条");
        m.put("status.changes", "处变更");
        m.put("step.version", "配置版本");
        m.put("step.version.src", "源版本 (如 1.20.1)");
        m.put("step.version.tgt", "目标版本 (如 1.21)");
        m.put("step.version.done", "版本已配置: %s → %s");
        m.put("step.loader", "配置加载器 — 选择 [1/2]:");
        m.put("step.loader.forge", "Forge");
        m.put("step.loader.fabric", "Fabric");
        m.put("step.loader.done", "加载器: %s");
        m.put("step.input", "输入可以是: 源码目录 或 .jar 文件");
        m.put("step.input.prompt", "路径");
        m.put("step.input.done", "输入: %s");
        m.put("step.input.auto", "自动设置输出: %s");
        m.put("step.output.prompt", "路径 (留空使用默认)");
        m.put("step.output.done", "输出: %s");
        m.put("mappings.source", "下载 %s 映射...");
        m.put("mappings.target", "下载 %s 映射...");
        m.put("mappings.done", "✓ %s 条");
        m.put("mappings.computing", "正在计算差异...");
        m.put("mappings.class", "类变更");
        m.put("mappings.method", "方法变更");
        m.put("mappings.field", "字段变更");
        m.put("mappings.ambiguous", "歧义映射: %s 条（需手动检查）");
        m.put("mappings.load.local", "从本地目录加载");
        m.put("mappings.load.download", "从网络自动下载");
        m.put("mappings.load.choose", "选择 [1/2]");
        m.put("mappings.local.prompt", "映射目录路径");
        m.put("mappings.local.done", "✓ 源: %s 条, 目标: %s 条");
        m.put("mappings.empty", "没有可应用的变更，请先加载映射表");
        m.put("mapping.dir.notfound", "✗ 目录不存在");
        m.put("migrate.preview", "迁移预览");
        m.put("migrate.scope", "迁移范围:");
        m.put("migrate.scope.all", "全部迁移");
        m.put("migrate.scope.source", "仅源码转换");
        m.put("migrate.scope.bytecode", "仅字节码转换");
        m.put("migrate.scope.meta", "仅更新元数据");
        m.put("migrate.scope.report", "仅生成差异报告");
        m.put("migrate.confirm", "确认执行？[y/N]");
        m.put("migrate.cancelled", "已取消");
        m.put("migrate.done", "✓ 迁移完成! 处理了 %s 个文件");
        m.put("migrate.failed", "✗ 迁移失败: %s");
        m.put("post.title", "后续操作");
        m.put("post.report", "查看迁移报告");
        m.put("post.detail", "查看变更详情");
        m.put("post.export", "导出差异文件");
        m.put("post.retry", "重新迁移（修改配置）");
        m.put("post.back", "返回主菜单");
        m.put("post.exit", "退出");
        m.put("post.prompt", "选择 [1-6]");
        m.put("report.title", "迁移报告");
        m.put("report.empty", "报告文件尚未生成。");
        m.put("detail.title", "变更详情");
        m.put("detail.class", "类名变更:");
        m.put("detail.method", "方法名变更 (前20):");
        m.put("detail.field", "字段名变更 (前20):");
        m.put("detail.removed", "已移除符号 (前10):");
        m.put("export.prompt", "导出路径 (留空: ./splice-diff.txt)");
        m.put("export.done", "✓ 差异已导出到: %s");
        m.put("export.failed", "导出失败: %s");
        m.put("error.version", "✗ 版本格式错误: %s");
        m.put("error.notfound", "✗ 路径不存在: %s");
        m.put("error.load", "✗ 加载失败: %s");
        m.put("error.config", "✗ 请先配置 %s");
        m.put("error.input", "✗ 请先配置输入路径");
        m.put("press.enter", "按 Enter 继续...");
        m.put("invalid.choice", "无效选项，请重试。");
        return m;
    }

    // ============== English ==============

    private static Map<String, String> en() {
        var m = new LinkedHashMap<String, String>();
        m.put("lang.name", "English");
        m.put("lang.select", "选择语言 / Choose language:");
        m.put("lang.zh", "中文");
        m.put("lang.en", "English");
        m.put("wizard.title", "SPLICE Interactive Migration Wizard");
        m.put("wizard.hint", "Type :wq to exit");
        m.put("wizard.exit", "Goodbye!");
        m.put("menu.version", "Configure Versions");
        m.put("menu.loader", "Configure Loader");
        m.put("menu.input", "Configure Input Path");
        m.put("menu.output", "Configure Output Path");
        m.put("menu.mappings", "Load Mappings");
        m.put("menu.migrate", "▶ Run Migration");
        m.put("menu.report", "View Migration Report");
        m.put("menu.prompt", "Choose option [1-7]");
        m.put("status.unset", "unset");
        m.put("status.arrow", "→");
        m.put("status.entries", "entries");
        m.put("status.changes", "changes");
        m.put("step.version", "Configure Versions");
        m.put("step.version.src", "Source version (e.g. 1.20.1)");
        m.put("step.version.tgt", "Target version (e.g. 1.21)");
        m.put("step.version.done", "Versions set: %s → %s");
        m.put("step.loader", "Mod Loader — choose [1/2]:");
        m.put("step.loader.forge", "Forge");
        m.put("step.loader.fabric", "Fabric");
        m.put("step.loader.done", "Loader: %s");
        m.put("step.input", "Input: source directory or .jar file");
        m.put("step.input.prompt", "Path");
        m.put("step.input.done", "Input: %s");
        m.put("step.input.auto", "Auto-set output: %s");
        m.put("step.output.prompt", "Path (leave empty for default)");
        m.put("step.output.done", "Output: %s");
        m.put("mappings.source", "Downloading %s mappings...");
        m.put("mappings.target", "Downloading %s mappings...");
        m.put("mappings.done", "✓ %s entries");
        m.put("mappings.computing", "Computing diff...");
        m.put("mappings.class", "Class changes");
        m.put("mappings.method", "Method changes");
        m.put("mappings.field", "Field changes");
        m.put("mappings.ambiguous", "Ambiguous mappings: %s (needs manual review)");
        m.put("mappings.load.local", "Load from local directory");
        m.put("mappings.load.download", "Download from network");
        m.put("mappings.load.choose", "Choose [1/2]");
        m.put("mappings.local.prompt", "Mappings directory path");
        m.put("mappings.local.done", "✓ Source: %s entries, Target: %s entries");
        m.put("mappings.empty", "No changes to apply. Load mappings first.");
        m.put("mapping.dir.notfound", "✗ Directory not found");
        m.put("migrate.preview", "Migration Preview");
        m.put("migrate.scope", "Scope:");
        m.put("migrate.scope.all", "Full migration");
        m.put("migrate.scope.source", "Source only (.java)");
        m.put("migrate.scope.bytecode", "Bytecode only (.class)");
        m.put("migrate.scope.meta", "Metadata only");
        m.put("migrate.scope.report", "Generate report only");
        m.put("migrate.confirm", "Confirm? [y/N]");
        m.put("migrate.cancelled", "Cancelled");
        m.put("migrate.done", "✓ Migration complete! Processed %s files");
        m.put("migrate.failed", "✗ Migration failed: %s");
        m.put("post.title", "Post-Migration Actions");
        m.put("post.report", "View migration report");
        m.put("post.detail", "View change details");
        m.put("post.export", "Export diff file");
        m.put("post.retry", "Re-migrate (modify config)");
        m.put("post.back", "Back to main menu");
        m.put("post.exit", "Exit");
        m.put("post.prompt", "Choose [1-6]");
        m.put("report.title", "Migration Report");
        m.put("report.empty", "Report not yet generated.");
        m.put("detail.title", "Change Details");
        m.put("detail.class", "Class renames:");
        m.put("detail.method", "Method renames (top 20):");
        m.put("detail.field", "Field renames (top 20):");
        m.put("detail.removed", "Removed symbols (top 10):");
        m.put("export.prompt", "Export path (default: ./splice-diff.txt)");
        m.put("export.done", "✓ Diff exported to: %s");
        m.put("export.failed", "Export failed: %s");
        m.put("error.version", "✗ Invalid version: %s");
        m.put("error.notfound", "✗ Path not found: %s");
        m.put("error.load", "✗ Load failed: %s");
        m.put("error.config", "✗ Please configure %s first");
        m.put("error.input", "✗ Please configure input path first");
        m.put("press.enter", "Press Enter to continue...");
        m.put("invalid.choice", "Invalid choice, try again.");
        return m;
    }
}
