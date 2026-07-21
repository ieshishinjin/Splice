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
 * Service for parsing MCP (Mod Coder Pack) mappings used by Forge.
 *
 * MCP mappings are stored in CSV files:
 * - methods.csv:   searge,name,side,desc
 * - fields.csv:    searge,name,side,desc
 * - params.csv:    param,searge,name,side
 */
public class MCPMappingService implements MappingService {

    private static final Logger LOG = LoggerFactory.getLogger(MCPMappingService.class);

    private final MappingDownloader downloader;

    public MCPMappingService(MappingDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public List<MappingEntry> loadMappings(Version version, Path cacheDir) {
        List<MappingEntry> entries = new ArrayList<>();

        try {
            Path mappingDir = downloader.downloadMappings(version, MappingType.MCP, cacheDir);

            // Parse methods.csv
            Path methodsFile = findFile(mappingDir, "methods.csv");
            if (methodsFile != null) {
                parseCSV(methodsFile, MappingEntry.MappingType.METHOD, entries);
                LOG.info("Loaded {} methods from {}", countByType(entries, MappingEntry.MappingType.METHOD), methodsFile);
            }

            // Parse fields.csv
            Path fieldsFile = findFile(mappingDir, "fields.csv");
            if (fieldsFile != null) {
                parseCSV(fieldsFile, MappingEntry.MappingType.FIELD, entries);
                LOG.info("Loaded {} fields from {}", countByType(entries, MappingEntry.MappingType.FIELD), fieldsFile);
            }

            // Try to parse SRG/TSRG files for class mappings and more complete info
            Path srgFile = findFile(mappingDir, "mappings.srg");
            if (srgFile != null) {
                parseSRG(srgFile, entries);
                LOG.info("Loaded SRG mappings from {}", srgFile);
            }

            // Try TSRG format (modern Forge)
            Path tsrgFile = findFile(mappingDir, "joined.tsrg");
            if (tsrgFile == null) tsrgFile = findFile(mappingDir, "mappings.tsrg");
            if (tsrgFile != null) {
                parseTSRG(tsrgFile, entries);
                LOG.info("Loaded TSRG mappings from {}", tsrgFile);
            }

        } catch (IOException e) {
            LOG.error("Failed to load MCP mappings for version {}: {}", version, e.getMessage());
            throw new RuntimeException("Failed to load MCP mappings", e);
        }

        return entries;
    }

    @Override
    public MappingType getMappingType() {
        return MappingType.MCP;
    }

    @Override
    public String getProviderName() {
        return "MCP (Forge)";
    }

    private Path findFile(Path dir, String name) {
        // Search in common locations within the extracted mapping directory
        try (var files = Files.walk(dir, 3)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(name))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // ============ Public static parsers (for reuse by local mapping service) ============

    /**
     * Parse a MCP CSV file (methods.csv or fields.csv).
     * @param csvFile the CSV file
     * @param type the mapping type (METHOD or FIELD)
     * @param entries destination list
     */
    public static void parseCSVStatic(Path csvFile, MappingEntry.MappingType type, List<MappingEntry> entries) {
        try (var lines = Files.lines(csvFile)) {
            lines.skip(1) // skip header
                    .filter(line -> !line.isBlank())
                    .forEach(line -> {
                        String[] parts = parseCSVLine(line);
                        if (parts.length >= 2) {
                            String searge = parts[0].trim();
                            String name = parts[1].trim();
                            String side = parts.length >= 3 ? parts[2].trim() : null;
                            if (!searge.isEmpty() && !name.isEmpty()) {
                                entries.add(new MappingEntry(type, searge, name, null, null, side));
                            }
                        }
                    });
        } catch (IOException e) {
            LOG.warn("Failed to parse CSV: {}", csvFile, e);
        }
    }

    /**
     * Parse an SRG file.
     */
    public static void parseSRGStatic(Path srgFile, List<MappingEntry> entries) {
        try (var lines = Files.lines(srgFile)) {
            lines.forEach(line -> {
                if (line.isBlank() || line.startsWith("#")) return;
                String[] parts = line.split("\\s+");
                if (parts.length < 3) return;
                switch (parts[0]) {
                    case "CL:" -> {
                        if (parts.length >= 3) entries.add(new MappingEntry(
                                MappingEntry.MappingType.CLASS, parts[1], parts[2], null, null, null));
                    }
                    case "MD:" -> {
                        if (parts.length >= 5) entries.add(new MappingEntry(
                                MappingEntry.MappingType.METHOD, parts[3], parts[4], null, parts[2], null));
                    }
                    case "FD:" -> {
                        if (parts.length >= 3) entries.add(new MappingEntry(
                                MappingEntry.MappingType.FIELD, parts[1], parts[2], null, null, null));
                    }
                }
            });
        } catch (IOException e) {
            LOG.warn("Failed to parse SRG: {}", srgFile, e);
        }
    }

    /**
     * Parse a TSRG file.
     */
    public static void parseTSRGStatic(Path tsrgFile, List<MappingEntry> entries) {
        try (var lines = Files.lines(tsrgFile)) {
            String currentClass = null, currentDeobfClass = null;
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank() || line.startsWith("#")) continue;
                if (line.startsWith("tsrg")) continue;
                if (!line.startsWith("\t")) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        currentClass = parts[0];
                        currentDeobfClass = parts[1];
                        entries.add(new MappingEntry(MappingEntry.MappingType.CLASS, currentClass, currentDeobfClass, null, null, null));
                    }
                } else if (currentClass != null) {
                    String trimmed = line.trim();
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length == 2) {
                        entries.add(new MappingEntry(MappingEntry.MappingType.FIELD, parts[0], parts[1], currentDeobfClass, null, null));
                    } else if (parts.length >= 3) {
                        entries.add(new MappingEntry(MappingEntry.MappingType.METHOD, parts[0], parts[1], currentDeobfClass, parts[2], null));
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to parse TSRG: {}", tsrgFile, e);
        }
    }

    /**
     * Simple CSV line parser (handles quoted fields).
     */
    public static String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { inQuotes = !inQuotes; }
            else if (c == ',' && !inQuotes) { fields.add(current.toString().trim()); current = new StringBuilder(); }
            else { current.append(c); }
        }
        fields.add(current.toString().trim());
        return fields.toArray(new String[0]);
    }

    // ============ Instance methods that delegate to static ============

    public void parseCSV(Path csvFile, MappingEntry.MappingType type, List<MappingEntry> entries) {
        parseCSVStatic(csvFile, type, entries);
    }

    public void parseSRG(Path srgFile, List<MappingEntry> entries) {
        parseSRGStatic(srgFile, entries);
    }

    public void parseTSRG(Path tsrgFile, List<MappingEntry> entries) {
        parseTSRGStatic(tsrgFile, entries);
    }

    private long countByType(List<MappingEntry> entries, MappingEntry.MappingType type) {
        return entries.stream().filter(e -> e.getType() == type).count();
    }
}
