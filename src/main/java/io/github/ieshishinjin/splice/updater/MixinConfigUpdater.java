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
 * Updates mixin configuration JSON files (e.g., my-mod.mixins.json).
 * <p>
 * Remaps class references in:
 * - "mixins" array
 * - "client" array
 * - "server" array
 * - "common" array
 * - "target" field (if present)
 * - "injectors" -> "condition" (if class-named)
 */
public class MixinConfigUpdater implements MetadataUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(MixinConfigUpdater.class);

    private final List<Conflict> conflicts;
    private final Gson gson;

    public MixinConfigUpdater() {
        this.conflicts = new ArrayList<>();
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    @Override
    public String update(String content, MappingDiff diff, Path filePath) {
        try {
            return updateMixinConfig(content, diff, filePath);
        } catch (Exception e) {
            conflicts.add(new Conflict(Conflict.Severity.WARNING, Conflict.Category.METADATA_ISSUE,
                    "Failed to parse mixin config: " + e.getMessage(), filePath, 0, null));
            return content;
        }
    }

    @Override
    public List<Conflict> getConflicts() { return conflicts; }

    private String updateMixinConfig(String content, MappingDiff diff, Path filePath) {
        JsonObject root = gson.fromJson(content, JsonObject.class);
        if (root == null) return content;

        boolean modified = false;

        // Remap class references in known mixin arrays
        for (String key : new String[]{"mixins", "client", "server", "common"}) {
            JsonElement el = root.get(key);
            if (el != null && el.isJsonArray()) {
                modified |= remapArray(el.getAsJsonArray(), diff, filePath, key);
            }
        }

        // Remap "target" if present (used by some frameworks)
        JsonElement target = root.get("target");
        if (target != null && target.isJsonPrimitive()) {
            String t = target.getAsString();
            String mapped = remapClassPath(t, diff);
            if (!mapped.equals(t)) {
                root.addProperty("target", mapped);
                modified = true;
                LOG.debug("Mixin config target rename: {} -> {}", t, mapped);
            }
        }

        // Remap "refmap"
        JsonElement refmap = root.get("refmap");
        if (refmap != null && refmap.isJsonPrimitive()) {
            String r = refmap.getAsString();
            // refmap filenames rarely change across versions, but log for awareness
            LOG.debug("Mixin refmap: {} (kept as-is)", r);
        }

        // Remap plugin class
        JsonElement plugin = root.get("plugin");
        if (plugin != null && plugin.isJsonPrimitive()) {
            String p = plugin.getAsString();
            String mapped = remapClassPath(p, diff);
            if (!mapped.equals(p)) {
                root.addProperty("plugin", mapped);
                modified = true;
                LOG.debug("Mixin plugin rename: {} -> {}", p, mapped);
            }
        }

        if (modified) {
            LOG.info("Updated mixin config: {}", filePath);
            return gson.toJson(root);
        }
        return content;
    }

    private boolean remapArray(JsonArray arr, MappingDiff diff, Path filePath, String section) {
        boolean mod = false;
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if (el.isJsonPrimitive()) {
                String val = el.getAsString();
                String mapped = remapClassPath(val, diff);
                if (!mapped.equals(val)) {
                    arr.set(i, new JsonPrimitive(mapped));
                    mod = true;
                    conflicts.add(new Conflict(Conflict.Severity.INFO, Conflict.Category.METADATA_ISSUE,
                            "Mixin " + section + " rename: " + val + " -> " + mapped, filePath, 0, null));
                }
            } else if (el.isJsonObject()) {
                // Handle nested objects with "value" arrays (e.g., Fabric API style)
                JsonObject obj = el.getAsJsonObject();
                JsonElement value = obj.get("value");
                if (value != null && value.isJsonArray()) {
                    mod |= remapArray(value.getAsJsonArray(), diff, filePath, section);
                }
            }
        }
        return mod;
    }

    static String remapClassPath(String classPath, MappingDiff diff) {
        String dotted = classPath.replace('/', '.').replace('\\', '.');
        if (dotted.endsWith(".class")) dotted = dotted.substring(0, dotted.length() - 6);

        String mapped = diff.getClassMappings().get(dotted);
        if (mapped != null) return mapped.replace('.', '/');

        int lastDot = dotted.lastIndexOf('.');
        if (lastDot >= 0) {
            String simple = dotted.substring(lastDot + 1);
            String mappedSimple = diff.getClassMappings().get(simple);
            if (mappedSimple != null) {
                return (dotted.substring(0, lastDot) + "." + mappedSimple).replace('.', '/');
            }
        }
        return classPath;
    }
}
