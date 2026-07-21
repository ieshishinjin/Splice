package io.github.ieshishinjin.splice.mapping;

import io.github.ieshishinjin.splice.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes the differences between source and target version mappings.
 * <p>
 * Algorithm:
 * 1. For each mapping entry in source, find the corresponding entry in target
 *    using the intermediate/obfuscated name as the key.
 * 2. If the mapped (human-readable) name differs, record a mapping diff.
 * 3. Entries in source but not in target are "removed".
 * 4. Entries in target but not in source are "added".
 */
public class MappingDiffEngine {

    private static final Logger LOG = LoggerFactory.getLogger(MappingDiffEngine.class);

    /**
     * Compute mapping differences between two versions.
     *
     * @param sourceVersion source Minecraft version
     * @param targetVersion target Minecraft version
     * @param sourceEntries mapping entries for the source version
     * @param targetEntries mapping entries for the target version
     * @param loaderType    the mod loader type
     * @return computed mapping differences
     */
    public MappingDiff computeDiff(Version sourceVersion, Version targetVersion,
                                   List<MappingEntry> sourceEntries,
                                   List<MappingEntry> targetEntries,
                                   LoaderType loaderType) {

        MappingDiff diff = new MappingDiff(sourceVersion, targetVersion, loaderType);

        // Split entries into class and method/field, filtering obfuscated intermediates
        List<MappingEntry> srcClasses = filterNonObfuscated(sourceEntries, MappingEntry.MappingType.CLASS);
        List<MappingEntry> tgtClasses = filterNonObfuscated(targetEntries, MappingEntry.MappingType.CLASS);
        List<MappingEntry> srcMethods = filterNonObfuscated(sourceEntries, MappingEntry.MappingType.METHOD);
        List<MappingEntry> tgtMethods = filterNonObfuscated(targetEntries, MappingEntry.MappingType.METHOD);
        List<MappingEntry> srcFields = filterNonObfuscated(sourceEntries, MappingEntry.MappingType.FIELD);
        List<MappingEntry> tgtFields = filterNonObfuscated(targetEntries, MappingEntry.MappingType.FIELD);

        // Build lookup maps for method/field by (name, descriptor) pair
        Map<String, List<MappingEntry>> srcMethodMap = groupByMethodKey(srcMethods);
        Map<String, List<MappingEntry>> tgtMethodMap = groupByMethodKey(tgtMethods);
        Map<String, List<MappingEntry>> srcFieldMap = groupByFieldKey(srcFields);
        Map<String, List<MappingEntry>> tgtFieldMap = groupByFieldKey(tgtFields);

        // For classes: compare by intermediate name (fully qualified class name)
        Map<String, String> srcClassMap = buildNameMap(srcClasses);
        Map<String, String> tgtClassMap = buildNameMap(tgtClasses);

        for (var entry : srcClassMap.entrySet()) {
            String intermed = entry.getKey();
            String srcName = entry.getValue();
            String tgtName = tgtClassMap.get(intermed);

            if (tgtName == null) {
                // Class was removed in target
                diff.addRemovedEntry(new MappingEntry(
                        MappingEntry.MappingType.CLASS, intermed, srcName, null, null, null));
            } else if (!srcName.equals(tgtName)) {
                diff.addClassMapping(srcName, tgtName);
            }
        }

        // For methods: compare by (intermediateName, descriptor) pair
        diffMethods(diff, srcMethodMap, tgtMethodMap);
        diffMethods(diff, tgtMethodMap, srcMethodMap);

        // For fields: compare by (intermediateName, descriptor) pair
        diffFields(diff, srcFieldMap, tgtFieldMap);
        diffFields(diff, tgtFieldMap, srcFieldMap);

        // Added classes: in target but not in source
        for (var entry : tgtClassMap.entrySet()) {
            if (!srcClassMap.containsKey(entry.getKey())) {
                diff.addAddedEntry(new MappingEntry(
                        MappingEntry.MappingType.CLASS, entry.getKey(), entry.getValue(), null, null, null));
            }
        }

        LOG.info("Mapping diff: {} class changes, {} method changes, {} field changes, " +
                        "{} removed, {} added, {} ambiguous",
                diff.getClassMappings().size(),
                diff.getMethodMappings().size(),
                diff.getFieldMappings().size(),
                diff.getRemovedEntries().size(),
                diff.getAddedEntries().size(),
                diff.getAmbiguousMappings().size());

        return diff;
    }

    /**
     * Diff methods between source and target by (intermediate, descriptor) key.
     */
    private void diffMethods(MappingDiff diff,
                             Map<String, List<MappingEntry>> srcMethods,
                             Map<String, List<MappingEntry>> tgtMethods) {
        for (var entry : srcMethods.entrySet()) {
            String key = entry.getKey();
            List<MappingEntry> srcList = entry.getValue();
            List<MappingEntry> tgtList = tgtMethods.get(key);

            if (tgtList == null) {
                for (var src : srcList) {
                    diff.addRemovedEntry(src);
                }
                continue;
            }

            // Compare mapped names
            for (var src : srcList) {
                String srcName = src.getMappedName();
                // Find matching target by owner if available
                String tgtName = findMappedName(src, tgtList);
                if (tgtName != null && !srcName.equals(tgtName)) {
                    diff.addMethodMapping(srcName, tgtName);
                }
            }
        }
    }

    /**
     * Diff fields between source and target by (intermediate, descriptor) key.
     */
    private void diffFields(MappingDiff diff,
                            Map<String, List<MappingEntry>> srcFields,
                            Map<String, List<MappingEntry>> tgtFields) {
        for (var entry : srcFields.entrySet()) {
            String key = entry.getKey();
            List<MappingEntry> srcList = entry.getValue();
            List<MappingEntry> tgtList = tgtFields.get(key);

            if (tgtList == null) {
                for (var src : srcList) {
                    diff.addRemovedEntry(src);
                }
                continue;
            }

            for (var src : srcList) {
                String srcName = src.getMappedName();
                String tgtName = findMappedName(src, tgtList);
                if (tgtName != null && !srcName.equals(tgtName)) {
                    diff.addFieldMapping(srcName, tgtName);
                }
            }
        }
    }

    /**
     * Find the corresponding mapped name in target candidates.
     */
    private String findMappedName(MappingEntry source, List<MappingEntry> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0).getMappedName();
        }
        // Try to match by owner
        if (source.getOwner() != null) {
            for (var c : candidates) {
                if (source.getOwner().equals(c.getOwner())) {
                    return c.getMappedName();
                }
            }
        }
        return candidates.get(0).getMappedName();
    }

    /**
     * Filter entries by type, excluding obfuscated names when CSV mappings are available.
     * For class entries, we keep them if they have a fully qualified name.
     * For method/field entries, we only keep them if they have a proper SRG/intermediary name.
     */
    private List<MappingEntry> filterNonObfuscated(List<MappingEntry> entries, MappingEntry.MappingType type) {
        return entries.stream()
                .filter(e -> e.getType() == type)
                .filter(e -> !e.isObfuscated())
                .toList();
    }

    /**
     * Build a simple name map: intermediate -> mapped name.
     */
    private Map<String, String> buildNameMap(List<MappingEntry> entries) {
        Map<String, String> map = new LinkedHashMap<>();
        for (var entry : entries) {
            map.putIfAbsent(entry.getIntermediateName(), entry.getMappedName());
        }
        return map;
    }

    /**
     * Group method entries by (intermediateName, descriptor) key.
     */
    private Map<String, List<MappingEntry>> groupByMethodKey(List<MappingEntry> methods) {
        return methods.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getIntermediateName() + ":" +
                        (e.getDescriptor() != null ? e.getDescriptor() : "")));
    }

    /**
     * Group field entries by (intermediateName, descriptor) key.
     */
    private Map<String, List<MappingEntry>> groupByFieldKey(List<MappingEntry> fields) {
        return fields.stream()
                .collect(Collectors.groupingBy(e ->
                        e.getIntermediateName() + ":" +
                        (e.getDescriptor() != null ? e.getDescriptor() : "")));
    }

    /**
     * Build a map: intermediate name -> list of mapping entries of that type.
     */
    private Map<String, List<MappingEntry>> groupByIntermediate(List<MappingEntry> entries) {
        return entries.stream()
                .collect(Collectors.groupingBy(
                        MappingEntry::getIntermediateName,
                        Collectors.toList()));
    }

    /**
     * Find the best matching target entry for a source entry.
     * Considers type and descriptor for disambiguation.
     */
    private MappingEntry findBestMatch(MappingEntry source, List<MappingEntry> candidates) {
        // First, filter by matching type
        List<MappingEntry> sameType = candidates.stream()
                .filter(c -> c.getType() == source.getType())
                .toList();

        if (sameType.isEmpty()) return null;
        if (sameType.size() == 1) return sameType.get(0);

        // Multiple candidates with same type — try to match by descriptor
        if (source.getDescriptor() != null) {
            List<MappingEntry> descMatch = sameType.stream()
                    .filter(c -> source.getDescriptor().equals(c.getDescriptor()))
                    .toList();
            if (descMatch.size() == 1) return descMatch.get(0);
        }

        // Try matching by owner class
        if (source.getOwner() != null) {
            List<MappingEntry> ownerMatch = sameType.stream()
                    .filter(c -> source.getOwner().equals(c.getOwner()))
                    .toList();
            if (ownerMatch.size() == 1) return ownerMatch.get(0);
        }

        // Last resort: return the first candidate
        LOG.warn("Ambiguous match for {}: {} candidates (type={})",
                source.getIntermediateName(), sameType.size(), source.getType());
        return sameType.get(0);
    }
}
