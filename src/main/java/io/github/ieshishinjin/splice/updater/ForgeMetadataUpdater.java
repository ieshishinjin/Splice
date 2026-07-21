package io.github.ieshishinjin.splice.updater;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates Forge mod metadata files (mods.toml, mcmod.info).
 *
 * mods.toml format:
 * [[mods]]
 * modId="..."
 * version="..."
 * displayName="..."
 *
 * Or in newer format:
 * [mods]
 *   [[mods]]
 *   modId = "..."
 *   version = "..."
 */
public class ForgeMetadataUpdater implements MetadataUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(ForgeMetadataUpdater.class);

    private final String targetVersion;
    private final List<Conflict> conflicts;

    public ForgeMetadataUpdater(String targetVersion) {
        this.targetVersion = targetVersion;
        this.conflicts = new ArrayList<>();
    }

    @Override
    public String update(String content, MappingDiff diff, Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        return switch (fileName) {
            case "mods.toml" -> updateModsToml(content, filePath);
            case "mcmod.info" -> updateMcmodInfo(content, filePath);
            default -> content;
        };
    }

    @Override
    public List<Conflict> getConflicts() {
        return conflicts;
    }

    /**
     * Update mods.toml - find version field and update it.
     */
    private String updateModsToml(String content, Path filePath) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n", -1);
        boolean versionUpdated = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // Match version fields (both quoted and unquoted)
            if (trimmed.matches("^(version|displayVersion)\\s*=\\s*.*$")) {
                String indent = line.substring(0, line.indexOf(trimmed));
                String key = trimmed.split("\\s*=")[0].trim();

                if (targetVersion != null && !targetVersion.isEmpty()) {
                    result.append(indent).append(key).append(" = \"").append(targetVersion).append("\"");
                    versionUpdated = true;
                    LOG.debug("Updated {} to {} in mods.toml", key, targetVersion);
                } else {
                    result.append(line);
                }
            } else {
                result.append(line);
            }

            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        if (!versionUpdated) {
            conflicts.add(new Conflict(
                    Conflict.Severity.WARNING,
                    Conflict.Category.METADATA_ISSUE,
                    "Could not find version field in mods.toml",
                    filePath, 0, "Add 'version = \"<version>\"' manually")
            );
        }

        return result.toString();
    }

    /**
     * Update legacy mcmod.info format (JSON).
     */
    private String updateMcmodInfo(String content, Path filePath) {
        // Simple replacement of version field in JSON
        String updated = content.replaceAll(
                "\"version\"\\s*:\\s*\"[^\"]*\"",
                "\"version\": \"" + targetVersion + "\"");
        if (updated.equals(content)) {
            conflicts.add(new Conflict(
                    Conflict.Severity.WARNING,
                    Conflict.Category.METADATA_ISSUE,
                    "Could not find version field in mcmod.info",
                    filePath, 0, "Update version manually"));
        }
        return updated;
    }
}
