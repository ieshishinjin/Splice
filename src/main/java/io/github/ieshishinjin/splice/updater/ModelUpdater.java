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
 * 更新物品/方块的模型 JSON 文件。
 * <p>
 * 1.16.5 → 1.20.4 之间 Forge 改了物品模型格式。
 * 旧格式只有 "parent": "item/generated"，新格式需要加 "loader": "forge:item_layers"
 * 否则物品在物品栏中不显示贴图。
 * <p>
 * 同样处理 forge:block / forge:item_layers 的转换。
 */
public class ModelUpdater implements MetadataUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(ModelUpdater.class);

    private final List<Conflict> conflicts;
    private final Gson gson;

    public ModelUpdater() {
        this.conflicts = new ArrayList<>();
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    @Override
    public String update(String content, MappingDiff diff, Path filePath) {
        try {
            return updateModelJson(content, filePath);
        } catch (Exception e) {
            conflicts.add(new Conflict(Conflict.Severity.WARNING, Conflict.Category.METADATA_ISSUE,
                    "无法解析模型 JSON: " + e.getMessage(), filePath, 0, null));
            return content;
        }
    }

    @Override
    public List<Conflict> getConflicts() { return conflicts; }

    private String updateModelJson(String content, Path filePath) {
        JsonObject root = gson.fromJson(content, JsonObject.class);
        if (root == null) return content;

        boolean modified = false;
        boolean isItemModel = filePath.toString().contains("models/item/");

        // === 物品模型：添加 forge:item_layers ===
        if (isItemModel && needsItemLoader(root)) {
            root.addProperty("loader", "forge:item_layers");
            modified = true;
            LOG.debug("Added forge:item_layers loader to {}", filePath);
        }

        // === 更新不推荐的 parent 引用 ===
        if (root.has("parent")) {
            String parent = root.get("parent").getAsString();
            String newParent = remapParent(parent);
            if (!newParent.equals(parent)) {
                root.addProperty("parent", newParent);
                modified = true;
            }
        }

        // === 移除 "forge:item_layers" 的重复 loader ===
        // （已经存在的就跳过）

        if (modified) {
            conflicts.add(new Conflict(Conflict.Severity.INFO, Conflict.Category.METADATA_ISSUE,
                    "物品模型已更新格式", filePath, 0,
                    "添加了 forge:item_layers loader 以兼容 1.20.4"));
            return gson.toJson(root);
        }

        return content;
    }

    /**
     * 判断是否需要添加 loader。
     * 如果 parent 是 item/generated 或 item/handheld 且没有 loader，就需要加。
     */
    private boolean needsItemLoader(JsonObject root) {
        // 已有 loader 就不动
        if (root.has("loader")) return false;

        // 检查 parent 是否是继承式物品模型
        if (root.has("parent")) {
            String parent = root.get("parent").getAsString();
            return parent.equals("item/generated")
                    || parent.equals("item/handheld")
                    || parent.equals("item/handheld_rod")
                    || parent.startsWith("item/");
        }

        // 有 textures.layer0 但没有 parent 的也处理
        if (root.has("textures")) {
            JsonObject tex = root.getAsJsonObject("textures");
            return tex.has("layer0") || tex.has("layer1");
        }

        return false;
    }

    /**
     * 重写过时的 parent 引用。
     */
    private String remapParent(String parent) {
        // Forge 在 1.20+ 移除了部分内置模型
        return switch (parent) {
            case "item/generated" -> "item/generated";   // 仍然有效
            case "builtin/generated" -> "item/generated";
            default -> parent;
        };
    }
}
