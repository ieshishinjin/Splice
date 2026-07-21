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
