package io.github.ieshishinjin.splice.mapping;

import io.github.ieshishinjin.splice.model.MappingEntry;
import io.github.ieshishinjin.splice.model.MappingType;
import io.github.ieshishinjin.splice.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing Yarn mappings used by Fabric.
 *
 * Yarn uses the "tiny" mapping format (v1 and v2).
 * Tiny v2 format:
 *   tiny	2	<num_names>	<name_0>	<name_1>	...
 *   c	<name_0>	<name_1>	...
 *   m	<name_0>	<name_1>	<desc>	<name_2>
 *   f	<name_0>	<name_f>	<name_2>
 *   p	<name_0>	<name_1>	<lvt_index>	<name_2>
 *
 * We map: intermediary (name_0) -> named (name_1)
 */
public class YarnMappingService implements MappingService {

    private static final Logger LOG = LoggerFactory.getLogger(YarnMappingService.class);

    private final MappingDownloader downloader;

    public YarnMappingService(MappingDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public List<MappingEntry> loadMappings(Version version, Path cacheDir) {
        List<MappingEntry> entries = new ArrayList<>();

        try {
            Path mappingDir = downloader.downloadMappings(version, MappingType.YARN, cacheDir);

            // Find all tiny/tiny2 files in the extracted content
            List<Path> tinyFiles = findTinyFiles(mappingDir);

            if (tinyFiles.isEmpty()) {
                // Try searching more broadly
                try (var walk = Files.walk(mappingDir, 3)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> {
                                String name = p.getFileName().toString();
                                return name.endsWith(".tiny") || name.endsWith(".tiny2")
                                        || name.equals("mappings");
                            })
                            .forEach(tinyFiles::add);
                }
            }

            for (Path tinyFile : tinyFiles) {
                parseTinyFile(tinyFile, entries);
                LOG.info("Loaded {} entries from {}",
                        countEntries(entries, tinyFile), tinyFile);
            }

            if (entries.isEmpty()) {
                LOG.warn("No Yarn mapping entries found for version {} in {}", version, mappingDir);
            }

        } catch (IOException e) {
            LOG.error("Failed to load Yarn mappings for version {}: {}", version, e.getMessage());
            throw new RuntimeException("Failed to load Yarn mappings", e);
        }

        return entries;
    }

    @Override
    public MappingType getMappingType() {
        return MappingType.YARN;
    }

    @Override
    public String getProviderName() {
        return "Yarn (Fabric)";
    }

    private List<Path> findTinyFiles(Path dir) throws IOException {
        List<Path> results = new ArrayList<>();
        if (!Files.isDirectory(dir)) return results;

        try (var walk = Files.walk(dir, 3)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".tiny") || name.endsWith(".tiny2");
                    })
                    .forEach(results::add);
        }
        return results;
    }

    /**
     * Parse a tiny v2 mapping file.
     *
     * Header: tiny\t2\t<namesCount>\t<namespace1>\t<namespace2>...
     * Classes:   c\t<intermediary>\t<named>
     * Methods:   m\t<intermediary>\t<desc>\t<named>
     * Fields:    f\t<intermediary>\t<field>\t<named>
     */
    private void parseTinyFile(Path tinyFile, List<MappingEntry> entries) {
        try (var lines = Files.lines(tinyFile)) {
            // Read header first
            String header = lines.findFirst().orElse("");
            boolean isV2 = header.startsWith("tiny\t2");

            // Reset and parse all lines
            try (var allLines = Files.lines(tinyFile)) {
                for (String line : (Iterable<String>) allLines::iterator) {
                    if (line.isBlank() || line.startsWith("#")) continue;

                    String[] parts = tabSplit(line);
                    if (parts.length < 3) continue;

                    String type = parts[0];

                    // We want intermediary (index 1) -> named (last index)
                    int namedIdx = parts.length - 1;
                    int intermediaryIdx = 1;

                    switch (type) {
                        case "c" -> {
                            if (parts.length >= 3) {
                                String intermClass = parts[intermediaryIdx];
                                String namedClass = parts[namedIdx];
                                if (!intermClass.isEmpty() && !namedClass.isEmpty()) {
                                    entries.add(new MappingEntry(
                                            MappingEntry.MappingType.CLASS,
                                            intermClass, namedClass,
                                            null, null, null));
                                }
                            }
                        }
                        case "m" -> {
                            // For v2: m  class  desc  intermediary  named
                            // parts: m, intermediary_class, method_desc, intermediary_method, named
                            // We need owner info from the class context
                            String intermClass = "";
                            String intermMethod = "";
                            String namedMethod = "";
                            String desc = "";

                            if (parts.length >= 5) {
                                // v2 format: m  class  desc  intermediary  named
                                intermClass = parts[1];
                                desc = parts[2];
                                intermMethod = parts[3];
                                namedMethod = parts[4];
                            } else if (parts.length == 4) {
                                // v1 format: m  intermediary  desc  named
                                intermMethod = parts[1];
                                desc = parts[2];
                                namedMethod = parts[3];
                            }

                            if (!intermMethod.isEmpty() && !namedMethod.isEmpty()) {
                                entries.add(new MappingEntry(
                                        MappingEntry.MappingType.METHOD,
                                        intermMethod, namedMethod,
                                        intermClass.isEmpty() ? null : intermClass,
                                        desc, null));
                            }
                        }
                        case "f" -> {
                            String intermClass = "";
                            String intermField = "";
                            String namedField = "";

                            if (parts.length >= 4) {
                                // v2: f  class  intermediary  named
                                intermClass = parts[1];
                                intermField = parts[2];
                                namedField = parts[3];
                            } else if (parts.length == 3) {
                                // v1: f  intermediary  named
                                intermField = parts[1];
                                namedField = parts[2];
                            }

                            if (!intermField.isEmpty() && !namedField.isEmpty()) {
                                entries.add(new MappingEntry(
                                        MappingEntry.MappingType.FIELD,
                                        intermField, namedField,
                                        intermClass.isEmpty() ? null : intermClass,
                                        null, null));
                            }
                        }
                        case "p" -> {
                            // Parameters - useful but less critical for migration
                            if (parts.length >= 5) {
                                entries.add(new MappingEntry(
                                        MappingEntry.MappingType.PARAM,
                                        parts[3], parts[4],
                                        parts[1], null, null));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to parse tiny file: {}", tinyFile, e);
        }
    }

    private String[] tabSplit(String line) {
        return line.split("\t", -1);
    }

    private long countEntries(List<MappingEntry> total, Path source) {
        return total.size(); // Not precise per file but serves the logging purpose
    }
}
