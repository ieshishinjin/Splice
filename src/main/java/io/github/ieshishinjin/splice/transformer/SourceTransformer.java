package io.github.ieshishinjin.splice.transformer;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Transforms Java source code by applying mapping diffs.
 * Handles:
 * - Import statements
 * - Type references (class names)
 * - Method invocations
 * - Field accesses
 * - Fully qualified names
 */
public class SourceTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(SourceTransformer.class);

    private final MappingDiff diff;
    private final List<Conflict> conflicts;

    // Pre-built replacement tables
    private final Map<Pattern, String> classReplacements;
    private final Map<Pattern, String> methodReplacements;
    private final Map<Pattern, String> fieldReplacements;

    // Fully-qualified name replacements
    private final Map<String, String> fqClassReplacements;

    public SourceTransformer(MappingDiff diff) {
        this.diff = diff;
        this.conflicts = new ArrayList<>();
        this.classReplacements = buildClassPatterns(diff.getClassMappings());
        this.methodReplacements = buildMethodPatterns(diff.getMethodMappings());
        this.fieldReplacements = buildFieldPatterns(diff.getFieldMappings());
        this.fqClassReplacements = buildFQClassMappings(diff.getClassMappings());
    }

    /**
     * Transform a Java source file's content.
     *
     * @param content   the original source content
     * @param filePath  path to the file (for conflict tracking)
     * @return the transformed source content, or original if no changes
     */
    public TransformationResult transform(String content, Path filePath) {
        if (content == null || content.isEmpty()) {
            return new TransformationResult(content, false, Collections.emptyList());
        }

        String result = content;
        boolean modified = false;

        // Stage 1: Replace import statements (safest)
        String afterImports = replaceImports(result);
        if (!afterImports.equals(result)) {
            modified = true;
        }

        // Stage 2: Replace fully qualified names (e.g., net.minecraft.block.Block)
        String afterFQ = replaceFullyQualifiedNames(afterImports);
        if (!afterFQ.equals(afterImports)) {
            modified = true;
        }

        // Stage 3: Replace method names (being careful with context)
        String afterMethods = replaceMethodNames(afterFQ, filePath);
        if (!afterMethods.equals(afterFQ)) {
            modified = true;
        }

        // Stage 4: Replace field names
        String afterFields = replaceFieldNames(afterMethods, filePath);
        if (!afterFields.equals(afterMethods)) {
            modified = true;
        }

        // Stage 5: Replace class names in type contexts
        String afterClasses = replaceClassNames(afterFields, filePath);
        if (!afterClasses.equals(afterFields)) {
            modified = true;
        }

        return new TransformationResult(afterClasses, modified,
                Collections.unmodifiableList(conflicts));
    }

    public List<Conflict> getConflicts() {
        return conflicts;
    }

    /**
     * Replace import statements with updated class names.
     * e.g., import net.minecraft.block.Block; -> import net.minecraft.block.CustomBlock;
     */
    private String replaceImports(String content) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\n", -1);

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ") && trimmed.endsWith(";")) {
                String importPath = trimmed.substring(7, trimmed.length() - 1).trim();
                String newImport = remapImportPath(importPath);
                if (!newImport.equals(importPath)) {
                    result.append(line.replace(importPath, newImport)).append("\n");
                    continue;
                }
            }
            result.append(line).append("\n");
        }

        // Remove trailing newline if original didn't have one
        if (!content.endsWith("\n") && result.length() > 0) {
            result.setLength(result.length() - 1);
        }

        return result.toString();
    }

    /**
     * Remap a fully qualified import path.
     */
    private String remapImportPath(String importPath) {
        // Try to map the class name part of the import
        int lastDot = importPath.lastIndexOf('.');
        if (lastDot < 0) return importPath;

        String packagePath = importPath.substring(0, lastDot);
        String className = importPath.substring(lastDot + 1);

        // Check for inner classes (indicated by capital letter after package)
        // Simple case: just the last segment
        String mapped = fqClassReplacements.get(importPath);
        if (mapped != null) return mapped;

        // Try just the class name
        String mappedClass = diff.getClassMappings().get(className);
        if (mappedClass != null) {
            return packagePath + "." + mappedClass;
        }

        // Try each segment of the import path
        // For imports like "net.minecraft.world.level.block.Block":
        // We need to check if "Block" was renamed
        return importPath;
    }

    /**
     * Replace fully qualified class names that appear in the code.
     */
    private String replaceFullyQualifiedNames(String content) {
        String result = content;
        for (var entry : fqClassReplacements.entrySet()) {
            // Match whole-word FQN references (not inside longer identifiers)
            String oldFQ = entry.getKey();
            String newFQ = entry.getValue();
            // Build a pattern that matches the FQN as a whole word
            Pattern pattern = Pattern.compile(
                    "(?<![\\w.])" + Pattern.quote(oldFQ) + "(?![\\w.])");
            result = pattern.matcher(result).replaceAll(Matcher.quoteReplacement(newFQ));
        }
        return result;
    }

    /**
     * Replace method names in the source code.
     */
    private String replaceMethodNames(String content, Path filePath) {
        String result = content;
        for (var entry : methodReplacements.entrySet()) {
            Pattern pattern = entry.getKey();
            String replacement = entry.getValue();
            var matcher = pattern.matcher(result);

            StringBuilder sb = new StringBuilder();
            int lastEnd = 0;
            while (matcher.find()) {
                int pos = matcher.start();
                int lineNum = getLineNumber(content, pos);
                recordConflict(filePath, lineNum,
                        "Method rename: " + matcher.group(1) + " -> " + replacement,
                        Conflict.Severity.INFO);
                sb.append(result, lastEnd, matcher.start(1));
                sb.append(replacement);
                lastEnd = matcher.end(1);
            }
            sb.append(result.substring(lastEnd));
            result = sb.toString();
        }
        return result;
    }

    /**
     * Replace field names in the source code.
     */
    private String replaceFieldNames(String content, Path filePath) {
        String result = content;
        for (var entry : fieldReplacements.entrySet()) {
            Pattern pattern = entry.getKey();
            String replacement = entry.getValue();
            var matcher = pattern.matcher(result);

            StringBuilder sb = new StringBuilder();
            int lastEnd = 0;
            while (matcher.find()) {
                int pos = matcher.start();
                int lineNum = getLineNumber(content, pos);
                recordConflict(filePath, lineNum,
                        "Field rename: " + matcher.group(1) + " -> " + replacement,
                        Conflict.Severity.INFO);
                sb.append(result, lastEnd, matcher.start(1));
                sb.append(replacement);
                lastEnd = matcher.end(1);
            }
            sb.append(result.substring(lastEnd));
            result = sb.toString();
        }
        return result;
    }

    /**
     * Replace class names that appear in type contexts.
     */
    private String replaceClassNames(String content, Path filePath) {
        String result = content;
        for (var entry : classReplacements.entrySet()) {
            Pattern pattern = entry.getKey();
            String replacement = entry.getValue();
            var matcher = pattern.matcher(result);

            StringBuilder sb = new StringBuilder();
            int lastEnd = 0;
            boolean found = false;
            while (matcher.find()) {
                found = true;
                int pos = matcher.start();
                int lineNum = getLineNumber(content, pos);
                recordConflict(filePath, lineNum,
                        "Class rename: " + matcher.group(1) + " -> " + replacement,
                        Conflict.Severity.INFO);
                sb.append(result, lastEnd, matcher.start(1));
                sb.append(replacement);
                lastEnd = matcher.end(1);
            }
            sb.append(result.substring(lastEnd));
            if (found) {
                result = sb.toString();
            }
        }
        return result;
    }

    /**
     * Build regex patterns for class name replacements.
     * Matches class names that appear as:
     * - Type declarations: "Block" in "Block myBlock;"
     * - Casts: "(Block)" or "(BlockAccessor)"
     * - Generic parameters: "<Block>" or "List<Block>"
     * - New expressions: "new Block("
     * - Static references: "Block.create("
     * - Annotations: "@OnlyIn" -> but check via simple word boundary
     */
    private Map<Pattern, String> buildClassPatterns(Map<String, String> classMap) {
        // Class names sorted by descending length for safe matching
        List<Map.Entry<String, String>> sorted = new ArrayList<>(classMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        Map<Pattern, String> patterns = new LinkedHashMap<>();
        for (var entry : sorted) {
            String oldName = entry.getKey();
            String newName = entry.getValue();

            // Match as a standalone word (word boundary, not an import path component)
            String regex = "(?<![\\w.$#])" + Pattern.quote(oldName) + "(?![\\w.$])";
            patterns.put(Pattern.compile(regex), newName);
        }
        return patterns;
    }

    /**
     * Build regex patterns for method name replacements.
     * Matches: "object.methodName(" pattern.
     */
    private Map<Pattern, String> buildMethodPatterns(Map<String, String> methodMap) {
        List<Map.Entry<String, String>> sorted = new ArrayList<>(methodMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        Map<Pattern, String> patterns = new LinkedHashMap<>();
        for (var entry : sorted) {
            String oldName = entry.getKey();
            String newName = entry.getValue();

            // Match method calls: .oldName( or #oldName( (for super calls)
            // Using lookbehind for ".", "#", "::" and lookahead for "("
            String regex = "(?<=\\.|#|::)" + Pattern.quote(oldName) + "(?=\\s*\\()";
            patterns.put(Pattern.compile(regex), newName);

            // Also match standalone method references in annotations etc.
            String regex2 = "(?<![\\w.$])" + Pattern.quote(oldName) + "(?=\\s*\\()";
            patterns.put(Pattern.compile(regex2), newName);
        }
        return patterns;
    }

    /**
     * Build regex patterns for field name replacements.
     * Matches: "object.fieldName" pattern.
     */
    private Map<Pattern, String> buildFieldPatterns(Map<String, String> fieldMap) {
        List<Map.Entry<String, String>> sorted = new ArrayList<>(fieldMap.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        Map<Pattern, String> patterns = new LinkedHashMap<>();
        for (var entry : sorted) {
            String oldName = entry.getKey();
            String newName = entry.getValue();

            // Match field accesses: ".fieldName" (not followed by "(")
            String regex = "(?<=\\.)" + Pattern.quote(oldName) + "(?![\\w(.$])";
            patterns.put(Pattern.compile(regex), newName);

            // Also match "this.fieldName" pattern
            String regex2 = "(?<=this\\.)" + Pattern.quote(oldName) + "(?![\\w$])";
            patterns.put(Pattern.compile(regex2), newName);
        }
        return patterns;
    }

    /**
     * Build fully qualified class name mappings.
     * Takes simple class name diffs and tries to construct FQN mappings.
     */
    private Map<String, String> buildFQClassMappings(Map<String, String> classMappings) {
        Map<String, String> fqMappings = new LinkedHashMap<>();
        // The simple class name mappings directly work as FQN end-segment mappings
        for (var entry : classMappings.entrySet()) {
            String oldName = entry.getKey();
            String newName = entry.getValue();
            // For imports that end with the renamed class
            fqMappings.put(oldName, newName);
        }
        return fqMappings;
    }

    /**
     * Register a conflict during transformation.
     */
    private void recordConflict(Path file, int line, String message, Conflict.Severity severity) {
        conflicts.add(new Conflict(
                severity,
                Conflict.Category.SYNTAX_ISSUE,
                message,
                file,
                line,
                null // suggestion can be derived
        ));
    }

    /**
     * Calculate the line number from a character position in the content.
     */
    private int getLineNumber(String content, int position) {
        int line = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    /**
     * Result of transforming a single source file.
     */
    public record TransformationResult(
            String content,
            boolean modified,
            List<Conflict> conflicts
    ) {}
}
