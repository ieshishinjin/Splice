package io.github.ieshishinjin.splice.updater;

import com.google.gson.*;
import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 更新 Quilt 的 quilt.mod.json。
 * 格式类似 fabric.mod.json，但字段名略有不同。
 */
public class QuiltMetadataUpdater implements MetadataUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(QuiltMetadataUpdater.class);

    private final String targetVersion;
    private final List<Conflict> conflicts;
    private final Gson gson;

    public QuiltMetadataUpdater(String targetVersion) {
        this.targetVersion = targetVersion;
        this.conflicts = new ArrayList<>();
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    @Override
    public String update(String content, MappingDiff diff, Path filePath) {
        try {
            return updateQuiltModJson(content, diff, filePath);
        } catch (Exception e) {
            conflicts.add(new Conflict(Conflict.Severity.ERROR, Conflict.Category.METADATA_ISSUE,
                    "解析 quilt.mod.json 失败: " + e.getMessage(), filePath, 0, null));
            return content;
        }
    }

    @Override
    public List<Conflict> getConflicts() { return conflicts; }

    private String updateQuiltModJson(String content, MappingDiff diff, Path filePath) {
        JsonObject root = gson.fromJson(content, JsonObject.class);
        if (root == null) return content;
        boolean modified = false;

        // 更新版本
        if (targetVersion != null && !targetVersion.isEmpty()) {
            JsonElement v = root.get("version");
            if (v != null && v.isJsonPrimitive()) {
                root.addProperty("version", targetVersion);
                modified = true;
            }
        }

        // 更新 entrypoints 中的类引用
        JsonObject quiltLoader = root.getAsJsonObject("quilt_loader");
        if (quiltLoader != null) {
            JsonObject entrypoints = quiltLoader.getAsJsonObject("entrypoints");
            if (entrypoints != null) {
                for (String key : entrypoints.keySet()) {
                    modified |= remapArray(entrypoints.get(key), diff);
                }
            }
        }

        if (modified) return gson.toJson(root);
        return content;
    }

    private boolean remapArray(JsonElement el, MappingDiff diff) {
        if (el == null || !el.isJsonArray()) return false;
        boolean mod = false;
        JsonArray arr = el.getAsJsonArray();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement item = arr.get(i);
            if (item.isJsonPrimitive()) {
                String cls = item.getAsString();
                String mapped = diff.getClassMappings().get(cls);
                if (mapped != null && !mapped.equals(cls)) {
                    arr.set(i, new JsonPrimitive(mapped));
                    mod = true;
                }
            }
        }
        return mod;
    }
}
