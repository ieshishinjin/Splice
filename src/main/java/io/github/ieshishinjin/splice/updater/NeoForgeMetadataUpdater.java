package io.github.ieshishinjin.splice.updater;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 更新 NeoForge 的 neoforge.mods.toml。
 * 格式和 Forge 的 mods.toml 类似，但字段略有不同。
 */
public class NeoForgeMetadataUpdater implements MetadataUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(NeoForgeMetadataUpdater.class);

    private final String targetVersion;
    private final List<Conflict> conflicts;

    public NeoForgeMetadataUpdater(String targetVersion) {
        this.targetVersion = targetVersion;
        this.conflicts = new ArrayList<>();
    }

    @Override
    public String update(String content, MappingDiff diff, Path filePath) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n", -1);
        boolean versionUpdated = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.matches("^(version|displayVersion)\\s*=\\s*.*$")) {
                String indent = line.substring(0, line.indexOf(trimmed));
                String key = trimmed.split("\\s*=")[0].trim();
                if (targetVersion != null && !targetVersion.isEmpty()) {
                    result.append(indent).append(key).append(" = \"").append(targetVersion).append("\"");
                    versionUpdated = true;
                } else {
                    result.append(line);
                }
            } else if (trimmed.matches("^(loaderVersion|neoForgeVersion)\\s*=\\s*.*$")) {
                // NeoForge 的版本依赖字段，保留原样
                result.append(line);
            } else {
                result.append(line);
            }
            if (i < lines.length - 1) result.append("\n");
        }

        if (!versionUpdated) {
            conflicts.add(new Conflict(Conflict.Severity.WARNING, Conflict.Category.METADATA_ISSUE,
                    "找不到 version 字段", filePath, 0, "手动添加 version = \"<version>\""));
        }

        return result.toString();
    }

    @Override
    public List<Conflict> getConflicts() { return conflicts; }
}
