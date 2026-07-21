package io.github.ieshishinjin.splice.updater;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates Fabric accessWidener files.
 * <p>
 * Format:
 * <pre>
 * accessWidener v2 named
 * accessible class com/example/MyClass
 * extendable class com/example/MyClass
 * mutable field com/example/MyClass fieldName
 * accessible method com/example/MyMethod (Lnet/minecraft/...;)V
 * </pre>
 * <p>
 * During migration, class and method names in access widener entries
 * need to be updated if they reference renamed Minecraft classes.
 */
public class AccessWidenerUpdater implements MetadataUpdater {

    private static final Logger LOG = LoggerFactory.getLogger(AccessWidenerUpdater.class);

    private final List<Conflict> conflicts;

    public AccessWidenerUpdater() {
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
            String trimmed = line.trim();

            // Check each line for class/method/field references
            if (trimmed.startsWith("accessible ") || trimmed.startsWith("extendable ")
                    || trimmed.startsWith("mutable ") || trimmed.startsWith("accessible class ")
                    || trimmed.startsWith("extendable class ")) {

                // Format: <directive> class <class_name>
                // Format: <directive> <class_name> <field/method_name> <desc>
                String[] parts = trimmed.split("\\s+");

                if (parts.length >= 3) {
                    String className = parts[2].replace('/', '.');
                    String mappedClass = mapClassName(className, diff);

                    if (!mappedClass.equals(className)) {
                        String newLine = line.replace(parts[2], mappedClass.replace('.', '/'));
                        result.append(newLine).append("\n");
                        modified = true;
                        LOG.debug("AW class rename: {} -> {}", parts[2], mappedClass.replace('.', '/'));
                        continue;
                    }

                    // Check method/field name (parts[3])
                    if (parts.length >= 4 && parts[1].equals("method")) {
                        String methodName = parts[3];
                        String mappedMethod = diff.getMethodMappings().get(methodName);
                        if (mappedMethod != null) {
                            String newLine = line.replace(" " + methodName + " ", " " + mappedMethod + " ");
                            result.append(newLine).append("\n");
                            modified = true;
                            LOG.debug("AW method rename: {} -> {}", methodName, mappedMethod);
                            continue;
                        }
                    }
                    if (parts.length >= 4 && parts[1].equals("field")) {
                        String fieldName = parts[3];
                        String mappedField = diff.getFieldMappings().get(fieldName);
                        if (mappedField != null) {
                            String newLine = line.replace(" " + fieldName + " ", " " + mappedField + " ");
                            result.append(newLine).append("\n");
                            modified = true;
                            LOG.debug("AW field rename: {} -> {}", fieldName, mappedField);
                            continue;
                        }
                    }
                }
            }

            result.append(line).append("\n");
        }

        // Remove trailing newline if original didn't have it
        if (!content.endsWith("\n") && result.length() > 0) {
            result.setLength(result.length() - 1);
        }

        return result.toString();
    }

    @Override
    public List<Conflict> getConflicts() {
        return conflicts;
    }

    private String mapClassName(String internalName, MappingDiff diff) {
        String dotted = internalName.replace('/', '.');
        String mapped = diff.getClassMappings().get(dotted);
        if (mapped != null) return mapped;

        // Try simple name
        int lastSlash = internalName.lastIndexOf('/');
        if (lastSlash >= 0) {
            String simple = internalName.substring(lastSlash + 1);
            String mappedSimple = diff.getClassMappings().get(simple);
            if (mappedSimple != null) {
                String pkg = internalName.substring(0, lastSlash + 1);
                return pkg + mappedSimple.replace('.', '/');
            }
        }

        return dotted;
    }
}
