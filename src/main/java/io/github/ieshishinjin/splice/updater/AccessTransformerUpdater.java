package io.github.ieshishinjin.splice.updater;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates Forge access transformer files (META-INF/accesstransformer.cfg).
 * <p>
 * Format:
 * <pre>
 * public net/minecraft/class/ClassName field_name f_12345 # comment
 * protected net/minecraft/class/ClassName method_name (L...;)V
 * </pre>
 * <p>
 * We update class names and method/field names that reference renamed
 * Minecraft classes.
 */
public class AccessTransformerUpdater implements MetadataUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(AccessTransformerUpdater.class);

    private final List<Conflict> conflicts;

    public AccessTransformerUpdater() {
        this.conflicts = new ArrayList<>();
    }

    @Override
    public String update(String content, MappingDiff diff, Path filePath) {
        if (content == null || content.isEmpty()) return content;

        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n", -1);
        boolean modified = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.stripLeading();

            // Skip comments and blank lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                result.append(line).append("\n");
                continue;
            }

            // Format: <access> <class> [member] [desc]
            // e.g., "public net/minecraft/world/level/block/Block f_12345"
            String[] parts = trimmed.split("\\s+");

            if (parts.length >= 2) {
                // parts[1] is the class name (internal format)
                String className = parts[1].replace('/', '.');
                String mappedClass = mapClassName(className, diff);

                if (!mappedClass.equals(className)) {
                    String newClassPart = mappedClass.replace('.', '/');
                    // Replace the class name in the original line (preserving indentation & access modifier)
                    String oldLine = line;
                    int idx = oldLine.indexOf(parts[1]);
                    if (idx >= 0) {
                        line = oldLine.substring(0, idx) + newClassPart
                                + oldLine.substring(idx + parts[1].length());
                        modified = true;
                        LOG.debug("AT class rename: {} -> {}", parts[1], newClassPart);
                    }
                }

                // Check method/field name at parts[2]
                if (parts.length >= 3) {
                    // parts[2] could be an SRG name like "func_12345_a" or "field_12345_a"
                    // or a MCP name like "doSomething"
                    String memberName = parts[2];
                    String mappedMethod = diff.getMethodMappings().get(memberName);
                    if (mappedMethod != null) {
                        line = line.replace(" " + memberName + " ", " " + mappedMethod + " ");
                        modified = true;
                        LOG.debug("AT method rename: {} -> {}", memberName, mappedMethod);
                    } else {
                        String mappedField = diff.getFieldMappings().get(memberName);
                        if (mappedField != null) {
                            line = line.replace(" " + memberName + " ", " " + mappedField + " ");
                            modified = true;
                            LOG.debug("AT field rename: {} -> {}", memberName, mappedField);
                        }
                    }
                }
            }

            result.append(line).append("\n");
        }

        if (!content.endsWith("\n") && result.length() > 0) {
            result.setLength(result.length() - 1);
        }

        if (modified) {
            LOG.info("Updated access transformer file: {}", filePath);
        }

        return result.toString();
    }

    @Override
    public List<Conflict> getConflicts() {
        return conflicts;
    }

    private String mapClassName(String dotted, MappingDiff diff) {
        String mapped = diff.getClassMappings().get(dotted);
        if (mapped != null) return mapped;

        int lastDot = dotted.lastIndexOf('.');
        if (lastDot >= 0) {
            String simple = dotted.substring(lastDot + 1);
            String mappedSimple = diff.getClassMappings().get(simple);
            if (mappedSimple != null) {
                return dotted.substring(0, lastDot + 1) + mappedSimple;
            }
        }
        return dotted;
    }
}
