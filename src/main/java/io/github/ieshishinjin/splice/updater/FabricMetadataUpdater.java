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
 * Updates Fabric mod metadata (fabric.mod.json).
 *
 * fabric.mod.json format:
 * {
 *   "id": "my-mod",
 *   "version": "1.0.0",
 *   "name": "My Mod",
 *   ...
 * }
 */
public class FabricMetadataUpdater implements MetadataUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(FabricMetadataUpdater.class);

    private final String targetVersion;
    private final List<Conflict> conflicts;
    private final Gson gson;

    public FabricMetadataUpdater(String targetVersion) {
        this.targetVersion = targetVersion;
        this.conflicts = new ArrayList<>();
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }

    @Override
    public String update(String content, MappingDiff diff, Path filePath) {
        try {
            return updateFabricModJson(content, diff, filePath);
        } catch (Exception e) {
            conflicts.add(new Conflict(
                    Conflict.Severity.ERROR,
                    Conflict.Category.METADATA_ISSUE,
                    "Failed to parse fabric.mod.json: " + e.getMessage(),
                    filePath, 0, "Ensure the file is valid JSON"));
            return content;
        }
    }

    @Override
    public List<Conflict> getConflicts() {
        return conflicts;
    }

    private String updateFabricModJson(String content, MappingDiff diff, Path filePath) {
        JsonObject root = gson.fromJson(content, JsonObject.class);
        boolean modified = false;

        // Update version field
        if (targetVersion != null && !targetVersion.isEmpty()) {
            JsonElement versionEl = root.get("version");
            if (versionEl != null && versionEl.isJsonPrimitive()) {
                String oldVersion = versionEl.getAsString();
                if (!oldVersion.equals(targetVersion)) {
                    root.addProperty("version", targetVersion);
                    modified = true;
                    LOG.debug("Updated version in fabric.mod.json: {} -> {}", oldVersion, targetVersion);
                }
            } else {
                root.addProperty("version", targetVersion);
                modified = true;
            }
        }

        // Update entrypoints if needed (class name changes)
        JsonElement entrypoints = root.get("entrypoints");
        if (entrypoints != null && entrypoints.isJsonObject()) {
            JsonObject ep = entrypoints.getAsJsonObject();
            modified |= updateEntrypointClasses(ep, diff, filePath);
        }

        // Update mixins if needed
        JsonElement mixins = root.get("mixins");
        if (mixins != null && mixins.isJsonArray()) {
            modified |= updateMixinEntries(mixins.getAsJsonArray(), diff, filePath);
        }

        // Update depends / suggests for Minecraft version
        JsonObject depends = root.getAsJsonObject("depends");
        if (depends != null) {
            modified |= updateDependencyVersion(depends, filePath);
        } else {
            // Try suggests
            JsonObject suggests = root.getAsJsonObject("suggests");
            if (suggests != null) {
                modified |= updateDependencyVersion(suggests, filePath);
            }
        }

        if (modified) {
            return gson.toJson(root);
        }

        return content;
    }

    /**
     * Update entrypoint class references if the class was renamed.
     */
    private boolean updateEntrypointClasses(JsonObject entrypoints, MappingDiff diff, Path filePath) {
        boolean modified = false;

        for (String key : entrypoints.keySet()) {
            JsonElement value = entrypoints.get(key);
            modified |= updateClassRefsInArray(value, diff, filePath);
        }

        return modified;
    }

    /**
     * Update mixin references if the target class was renamed.
     */
    private boolean updateMixinEntries(JsonArray mixins, MappingDiff diff, Path filePath) {
        boolean modified = false;

        for (int i = 0; i < mixins.size(); i++) {
            JsonElement element = mixins.get(i);
            if (element.isJsonPrimitive()) {
                String mixinPath = element.getAsString();
                // Mixin paths look like: "com/example/mixin/ExampleMixin"
                String mapped = mapClassPath(mixinPath, diff);
                if (!mapped.equals(mixinPath)) {
                    mixins.set(i, new JsonPrimitive(mapped));
                    modified = true;
                    conflicts.add(new Conflict(
                            Conflict.Severity.INFO,
                            Conflict.Category.METADATA_ISSUE,
                            "Updated mixin reference: " + mixinPath + " -> " + mapped,
                            filePath, 0, null));
                }
            }
        }

        return modified;
    }

    /**
     * Update class references in a JSON array (for entrypoints).
     */
    private boolean updateClassRefsInArray(JsonElement element, MappingDiff diff, Path filePath) {
        if (element == null || !element.isJsonArray()) return false;

        boolean modified = false;
        JsonArray array = element.getAsJsonArray();

        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item.isJsonPrimitive()) {
                String className = item.getAsString();
                String mapped = diff.getClassMappings().get(className);
                if (mapped != null && !mapped.equals(className)) {
                    array.set(i, new JsonPrimitive(mapped));
                    modified = true;
                    LOG.debug("Updated entrypoint class: {} -> {}", className, mapped);
                }
            } else if (item.isJsonObject()) {
                // Handle nested entrypoint objects
                JsonObject obj = item.getAsJsonObject();
                JsonElement value = obj.get("value");
                if (value != null) {
                    modified |= updateClassRefsInArray(value, diff, filePath);
                }
            }
        }

        return modified;
    }

    /**
     * Update Minecraft dependency version in depends/suggests.
     */
    private boolean updateDependencyVersion(JsonObject deps, Path filePath) {
        // The minecraft dependency could be "minecraft" or "minecraft_dependency"
        for (String key : List.of("minecraft", "minecraft_dependency")) {
            JsonElement mcDep = deps.get(key);
            if (mcDep != null && mcDep.isJsonPrimitive()) {
                // Update version constraint (e.g., ">=1.19.2" -> ">=1.21")
                // This is a simple replacement - complex constraints need manual review
                String oldConstraint = mcDep.getAsString();
                String newConstraint = oldConstraint.replaceAll(
                        "\\d+\\.\\d+(\\.\\d+)?", targetVersion);
                if (!newConstraint.equals(oldConstraint)) {
                    deps.addProperty(key, newConstraint);
                    LOG.debug("Updated dependency: {} -> {}", oldConstraint, newConstraint);
                    conflicts.add(new Conflict(
                            Conflict.Severity.INFO,
                            Conflict.Category.METADATA_ISSUE,
                            "Updated Minecraft dependency: " + oldConstraint + " -> " + newConstraint,
                            filePath, 0,
                            "Review the version constraint for accuracy"));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Map a class file path to its new name if the class was renamed.
     */
    private String mapClassPath(String classPath, MappingDiff diff) {
        // Convert path separators to dots for lookup
        String dotted = classPath.replace('/', '.').replace('\\', '.');
        // Remove .class extension if present
        if (dotted.endsWith(".class")) {
            dotted = dotted.substring(0, dotted.length() - 6);
        }

        // Try to find the last segment (simple class name)
        String mapped = diff.getClassMappings().get(dotted);
        if (mapped != null) {
            return mapped.replace('.', '/') + ".class";
        }

        // Try just the class name part
        int lastDot = dotted.lastIndexOf('.');
        if (lastDot >= 0) {
            String simpleName = dotted.substring(lastDot + 1);
            String mappedSimple = diff.getClassMappings().get(simpleName);
            if (mappedSimple != null) {
                String packagePath = dotted.substring(0, lastDot);
                return (packagePath + "." + mappedSimple).replace('.', '/');
            }
        }

        return classPath;
    }
}
