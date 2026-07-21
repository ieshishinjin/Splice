package io.github.ieshishinjin.splice.model;

import java.util.*;

/**
 * Represents the computed differences between two versions of mappings.
 * Contains mappings from source names to target names.
 */
public class MappingDiff {

    private final Version sourceVersion;
    private final Version targetVersion;
    private final LoaderType loaderType;

    // Mapping: class simple name or fully qualified name
    private final Map<String, String> classMappings;       // src -> tgt
    private final Map<String, String> methodMappings;      // src -> tgt
    private final Map<String, String> fieldMappings;       // src -> tgt

    // Reverse mappings for verification
    private final Map<String, String> reverseClassMappings;
    private final Map<String, String> reverseMethodMappings;
    private final Map<String, String> reverseFieldMappings;

    // Entries that were ambiguous (multiple targets for one source)
    private final List<AmbiguousMapping> ambiguousMappings;

    // Entries removed in the target version
    private final List<MappingEntry> removedEntries;

    // Entries added in the target version (no source counterpart)
    private final List<MappingEntry> addedEntries;

    public MappingDiff(Version sourceVersion, Version targetVersion, LoaderType loaderType) {
        this.sourceVersion = sourceVersion;
        this.targetVersion = targetVersion;
        this.loaderType = loaderType;
        this.classMappings = new LinkedHashMap<>();
        this.methodMappings = new LinkedHashMap<>();
        this.fieldMappings = new LinkedHashMap<>();
        this.reverseClassMappings = new HashMap<>();
        this.reverseMethodMappings = new HashMap<>();
        this.reverseFieldMappings = new HashMap<>();
        this.ambiguousMappings = new ArrayList<>();
        this.removedEntries = new ArrayList<>();
        this.addedEntries = new ArrayList<>();
    }

    public void addClassMapping(String source, String target) {
        if (classMappings.containsKey(source) && !classMappings.get(source).equals(target)) {
            ambiguousMappings.add(new AmbiguousMapping(
                    MappingEntry.MappingType.CLASS, source, target, classMappings.get(source)));
        }
        classMappings.put(source, target);
        reverseClassMappings.put(target, source);
    }

    public void addMethodMapping(String source, String target) {
        if (methodMappings.containsKey(source) && !methodMappings.get(source).equals(target)) {
            ambiguousMappings.add(new AmbiguousMapping(
                    MappingEntry.MappingType.METHOD, source, target, methodMappings.get(source)));
        }
        methodMappings.put(source, target);
        reverseMethodMappings.put(target, source);
    }

    public void addFieldMapping(String source, String target) {
        if (fieldMappings.containsKey(source) && !fieldMappings.get(source).equals(target)) {
            ambiguousMappings.add(new AmbiguousMapping(
                    MappingEntry.MappingType.FIELD, source, target, fieldMappings.get(source)));
        }
        fieldMappings.put(source, target);
        reverseFieldMappings.put(target, source);
    }

    public void addRemovedEntry(MappingEntry entry) {
        removedEntries.add(entry);
    }

    public void addAddedEntry(MappingEntry entry) {
        addedEntries.add(entry);
    }

    // Getters
    public Version getSourceVersion() { return sourceVersion; }
    public Version getTargetVersion() { return targetVersion; }
    public LoaderType getLoaderType() { return loaderType; }

    /** Get sorted class mappings (by key length descending for safe replacement). */
    public Map<String, String> getClassMappings() { return sortByKeyLength(classMappings); }
    public Map<String, String> getMethodMappings() { return sortByKeyLength(methodMappings); }
    public Map<String, String> getFieldMappings() { return sortByKeyLength(fieldMappings); }

    public List<AmbiguousMapping> getAmbiguousMappings() { return ambiguousMappings; }
    public List<MappingEntry> getRemovedEntries() { return removedEntries; }
    public List<MappingEntry> getAddedEntries() { return addedEntries; }

    public boolean hasChanges() {
        return !classMappings.isEmpty() || !methodMappings.isEmpty() || !fieldMappings.isEmpty();
    }

    public int getTotalChanges() {
        return classMappings.size() + methodMappings.size() + fieldMappings.size();
    }

    /**
     * Get all mappings flattened into a single map keyed by source name.
     * The type is encoded as prefix: CLASS:, METHOD:, FIELD:
     */
    public Map<String, String> getAllMappings() {
        Map<String, String> all = new LinkedHashMap<>();
        classMappings.forEach((k, v) -> all.put("CLASS:" + k, v));
        methodMappings.forEach((k, v) -> all.put("METHOD:" + k, v));
        fieldMappings.forEach((k, v) -> all.put("FIELD:" + k, v));
        return all;
    }

    /** Sort mappings so longer keys come first, preventing partial replacement issues. */
    private static Map<String, String> sortByKeyLength(Map<String, String> map) {
        var list = new ArrayList<>(map.entrySet());
        list.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
        Map<String, String> sorted = new LinkedHashMap<>(list.size());
        for (var entry : list) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    /**
     * Represents an ambiguous mapping where one source name maps to multiple targets.
     */
    public record AmbiguousMapping(
            MappingEntry.MappingType type,
            String source,
            String targetA,
            String targetB
    ) {}
}
