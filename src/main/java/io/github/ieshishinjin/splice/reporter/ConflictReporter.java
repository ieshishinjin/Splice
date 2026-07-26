package io.github.ieshishinjin.splice.reporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles reporting of conflicts and migration results.
 * Outputs both console summary and JSON report file.
 */
public class ConflictReporter {

    private static final Logger LOG = LoggerFactory.getLogger(ConflictReporter.class);

    private final Gson gson;

    public ConflictReporter() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    /**
     * Write a detailed JSON report of all conflicts.
     */
    public void writeReport(Path reportPath, List<Conflict> conflicts) {
        if (conflicts.isEmpty()) {
            LOG.info("No conflicts to report.");
            try {
                // Still write an empty report for reference
                Files.writeString(reportPath, """
                        {
                          "status": "SUCCESS",
                          "totalConflicts": 0,
                          "message": "Migration completed without issues."
                        }
                        """);
            } catch (IOException e) {
                LOG.warn("Failed to write empty report: {}", e.getMessage());
            }
            return;
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("status", "COMPLETED_WITH_CONFLICTS");
        report.put("totalConflicts", conflicts.size());

        // Group by severity
        Map<Conflict.Severity, List<Conflict>> bySeverity = conflicts.stream()
                .collect(Collectors.groupingBy(Conflict::getSeverity));

        report.put("errors", bySeverity.getOrDefault(Conflict.Severity.ERROR, List.of()).size());
        report.put("warnings", bySeverity.getOrDefault(Conflict.Severity.WARNING, List.of()).size());
        report.put("infos", bySeverity.getOrDefault(Conflict.Severity.INFO, List.of()).size());

        // Detailed conflict entries
        List<Map<String, Object>> entries = conflicts.stream().map(c -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("severity", c.getSeverity().name());
            entry.put("category", c.getCategory().name());
            entry.put("message", c.getMessage());
            if (c.getFile() != null) {
                entry.put("file", c.getFile().toString());
            }
            if (c.getLineNumber() > 0) {
                entry.put("line", c.getLineNumber());
            }
            if (c.getSuggestion() != null) {
                entry.put("suggestion", c.getSuggestion());
            }
            return entry;
        }).toList();

        report.put("conflicts", entries);

        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, gson.toJson(report));
            LOG.info("Conflict report written to: {}", reportPath);
        } catch (IOException e) {
            LOG.error("Failed to write conflict report: {}", e.getMessage());
        }
    }

    /**
     * 生成 HTML 可视化迁移报告。
     */
    public void writeHtmlReport(Path htmlPath, int filesProcessed, List<Conflict> conflicts, MappingDiff diff) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">");
        html.append("<title>Splice 迁移报告 — ").append(diff.getSourceVersion()).append(" → ").append(diff.getTargetVersion()).append("</title>");
        html.append("<style>");
        html.append("body{font-family:-apple-system,sans-serif;max-width:960px;margin:0 auto;padding:20px;background:#0d1117;color:#c9d1d9}");
        html.append("h1{color:#58a6ff;border-bottom:1px solid #30363d;padding-bottom:10px}");
        html.append("h2{color:#58a6ff;margin-top:30px}");
        html.append("h3{color:#c9d1d9}");
        html.append(".summary{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;margin:20px 0}");
        html.append(".card{background:#161b22;border:1px solid #30363d;border-radius:8px;padding:16px;text-align:center}");
        html.append(".card .num{font-size:28px;font-weight:700;color:#58a6ff}");
        html.append(".card .label{font-size:12px;color:#8b949e;margin-top:4px}");
        html.append(".badge-error{display:inline-block;background:#da3633;color:#fff;padding:2px 8px;border-radius:12px;font-size:12px}");
        html.append(".badge-warning{display:inline-block;background:#d29922;color:#fff;padding:2px 8px;border-radius:12px;font-size:12px}");
        html.append(".badge-info{display:inline-block;background:#1f6feb;color:#fff;padding:2px 8px;border-radius:12px;font-size:12px}");
        html.append(".diff-table{width:100%;border-collapse:collapse;margin:10px 0;font-size:13px}");
        html.append(".diff-table th{text-align:left;padding:8px 12px;border-bottom:2px solid #30363d;color:#8b949e}");
        html.append(".diff-table td{padding:8px 12px;border-bottom:1px solid #21262d;font-family:monospace}");
        html.append(".diff-table tr:hover{background:#161b22}");
        html.append(".old{color:#f85149}");
        html.append(".new{color:#3fb950}");
        html.append(".arrow{color:#8b949e;padding:0 8px}");
        html.append(".conflict-item{padding:10px 14px;border:1px solid #30363d;border-radius:6px;margin:8px 0}");
        html.append(".conflict-item .file{color:#58a6ff;font-family:monospace;font-size:13px}");
        html.append(".conflict-item .line{color:#8b949e;font-size:12px}");
        html.append(".conflict-item .msg{margin:6px 0}");
        html.append(".conflict-item .suggestion{color:#8b949e;font-size:13px;font-style:italic}");
        html.append(".error{border-left:4px solid #da3633}");
        html.append(".warning{border-left:4px solid #d29922}");
        html.append(".info{border-left:4px solid #1f6feb}");
        html.append("</style></head><body>");

        html.append("<h1>🧬 Splice 迁移报告</h1>");
        html.append("<p>").append(diff.getSourceVersion()).append(" → ").append(diff.getTargetVersion());
        html.append(" &nbsp;|&nbsp; ").append(diff.getLoaderType()).append("</p>");

        // 统计卡片
        long errs = conflicts.stream().filter(c -> c.getSeverity() == Conflict.Severity.ERROR).count();
        long warns = conflicts.stream().filter(c -> c.getSeverity() == Conflict.Severity.WARNING).count();
        html.append("<div class=\"summary\">");
        card(html, String.valueOf(filesProcessed), "文件处理");
        card(html, String.valueOf(diff.getClassMappings().size()), "类变更");
        card(html, String.valueOf(diff.getMethodMappings().size()), "方法变更");
        card(html, String.valueOf(diff.getFieldMappings().size()), "字段变更");
        card(html, String.valueOf(errs), "错误");
        card(html, String.valueOf(warns), "警告");
        html.append("</div>");

        // 类名变更表
        if (!diff.getClassMappings().isEmpty()) {
            html.append("<h2>类名变更</h2><table class=\"diff-table\"><tr><th>原名</th><th></th><th>新名</th></tr>");
            diff.getClassMappings().forEach((k, v) -> html.append("<tr><td class=\"old\">").append(esc(k))
                    .append("</td><td class=\"arrow\">→</td><td class=\"new\">").append(esc(v)).append("</td></tr>"));
            html.append("</table>");
        }

        // 方法名变更表
        if (!diff.getMethodMappings().isEmpty()) {
            html.append("<h2>方法名变更（前 50）</h2><table class=\"diff-table\"><tr><th>原名</th><th></th><th>新名</th></tr>");
            diff.getMethodMappings().entrySet().stream().limit(50)
                    .forEach(e -> html.append("<tr><td class=\"old\">").append(esc(e.getKey()))
                            .append("</td><td class=\"arrow\">→</td><td class=\"new\">").append(esc(e.getValue())).append("</td></tr>"));
            html.append("</table>");
        }

        // 冲突详情
        if (!conflicts.isEmpty()) {
            html.append("<h2>冲突详情</h2>");
            for (Conflict c : conflicts) {
                String sev = c.getSeverity().name().toLowerCase();
                html.append("<div class=\"conflict-item ").append(sev).append("\">");
                html.append("<span class=\"badge-").append(sev).append("\">").append(c.getSeverity()).append("</span> ");
                html.append("<span class=\"file\">").append(c.getFile() != null ? esc(c.getFile().toString()) : "?");
                if (c.getLineNumber() > 0) html.append(":").append(c.getLineNumber());
                html.append("</span>");
                html.append("<div class=\"msg\">").append(esc(c.getMessage())).append("</div>");
                if (c.getSuggestion() != null) {
                    html.append("<div class=\"suggestion\">💡 ").append(esc(c.getSuggestion())).append("</div>");
                }
                html.append("</div>");
            }
        }

        html.append("</body></html>");

        try {
            Files.createDirectories(htmlPath.getParent());
            Files.writeString(htmlPath, html.toString());
            LOG.info("HTML 报告已生成: {}", htmlPath);
        } catch (IOException e) {
            LOG.error("生成 HTML 报告失败", e);
        }
    }

    private void card(StringBuilder h, String num, String label) {
        h.append("<div class=\"card\"><div class=\"num\">").append(num).append("</div><div class=\"label\">").append(label).append("</div></div>");
    }

    private String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Print a human-readable summary to the console.
     */
    public void printSummary(int filesProcessed, List<Conflict> conflicts, MappingDiff diff) {
        // Build summary header
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append("=".repeat(60)).append("\n");
        sb.append("  SPLICE MIGRATION SUMMARY\n");
        sb.append("  ").append(diff.getSourceVersion()).append(" -> ").append(diff.getTargetVersion());
        sb.append("  |  Loader: ").append(diff.getLoaderType()).append("\n");
        sb.append("=".repeat(60)).append("\n");

        // Files processed
        sb.append("  Files processed: ").append(filesProcessed).append("\n");

        // Mapping changes
        sb.append("\n  -- Mapping Changes --\n");
        sb.append("  Classes: ").append(diff.getClassMappings().size()).append("\n");
        sb.append("  Methods: ").append(diff.getMethodMappings().size()).append("\n");
        sb.append("  Fields:  ").append(diff.getFieldMappings().size()).append("\n");
        sb.append("  Total:   ").append(diff.getTotalChanges()).append("\n");

        // Changes detail (first 10 entries)
        if (!diff.getClassMappings().isEmpty()) {
            sb.append("\n  -- Class Renames (showing first 15) --\n");
            diff.getClassMappings().entrySet().stream()
                    .limit(15)
                    .forEach(e -> sb.append("    ").append(e.getKey())
                            .append(" -> ").append(e.getValue()).append("\n"));
            if (diff.getClassMappings().size() > 15) {
                sb.append("    ... and ").append(diff.getClassMappings().size() - 15).append(" more\n");
            }
        }

        // Removed entries
        if (!diff.getRemovedEntries().isEmpty()) {
            sb.append("\n  -- Removed Symbols (may need manual attention) --\n");
            diff.getRemovedEntries().stream()
                    .limit(10)
                    .forEach(e -> sb.append("    [").append(e.getType()).append("] ")
                            .append(e.getIntermediateName())
                            .append(" (").append(e.getMappedName()).append(")\n"));
            if (diff.getRemovedEntries().size() > 10) {
                sb.append("    ... and ").append(diff.getRemovedEntries().size() - 10).append(" more\n");
            }
        }

        // Conflicts
        long errorCount = conflicts.stream()
                .filter(c -> c.getSeverity() == Conflict.Severity.ERROR).count();
        long warningCount = conflicts.stream()
                .filter(c -> c.getSeverity() == Conflict.Severity.WARNING).count();
        long infoCount = conflicts.stream()
                .filter(c -> c.getSeverity() == Conflict.Severity.INFO).count();

        sb.append("\n  -- Conflicts --\n");
        sb.append("  Errors:   ").append(errorCount).append("\n");
        sb.append("  Warnings: ").append(warningCount).append("\n");
        sb.append("  Infos:    ").append(infoCount).append("\n");

        // Show ERROR conflicts in detail
        if (errorCount > 0) {
            sb.append("\n  -- ERROR Details (must be fixed manually) --\n");
            conflicts.stream()
                    .filter(c -> c.getSeverity() == Conflict.Severity.ERROR)
                    .forEach(c -> {
                        sb.append("  [ERROR] ").append(c.getMessage()).append("\n");
                        if (c.getFile() != null) {
                            sb.append("    File: ").append(c.getFile());
                            if (c.getLineNumber() > 0) {
                                sb.append(":").append(c.getLineNumber());
                            }
                            sb.append("\n");
                        }
                        if (c.getSuggestion() != null) {
                            sb.append("    Suggestion: ").append(c.getSuggestion()).append("\n");
                        }
                    });
        }

        sb.append("=".repeat(60)).append("\n");

        // Use LOG at info level so it appears in both console and log file
        LOG.info(sb.toString());

        // If there are errors, print a prominent warning
        if (errorCount > 0) {
            LOG.warn("⚠ Migration completed with {} error(s). Review the report and fix manually.", errorCount);
        }
    }
}
