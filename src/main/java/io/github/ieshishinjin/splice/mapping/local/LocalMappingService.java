package io.github.ieshishinjin.splice.mapping.local;

import io.github.ieshishinjin.splice.mapping.MappingService;
import io.github.ieshishinjin.splice.mapping.MCPMappingService;
import io.github.ieshishinjin.splice.mapping.YarnMappingService;
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
 * Reads mappings from a user-specified local directory.
 * Supports: .csv (MCP), .tiny/.tiny2 (Yarn), .srg/.tsrg (SRG).
 * <p>
 * Directory structure expected:
 * <pre>
 *   --source/
 *     methods.csv
 *     fields.csv
 *     joined.tsrg
 *   --target/
 *     methods.csv
 *     fields.csv
 *     joined.tsrg
 * </pre>
 * Or for Yarn:
 * <pre>
 *   --source/
 *     mappings.tiny
 *   --target/
 *     mappings.tiny
 * </pre>
 */
public class LocalMappingService implements MappingService {

    private static final Logger LOG = LoggerFactory.getLogger(LocalMappingService.class);

    private final Path sourceDir;
    private final Path targetDir;
    private final MappingType mappingType;

    public LocalMappingService(Path mappingsDir, Version source, Version target, MappingType mappingType) {
        this.mappingType = mappingType;
        // Support both: --mappings-dir/source/1.20.1/ and --mappings-dir/1.20.1/
        this.sourceDir = resolveMappingDir(mappingsDir, source);
        this.targetDir = resolveMappingDir(mappingsDir, target);
    }

    public Path getSourceDir() { return sourceDir; }
    public Path getTargetDir() { return targetDir; }

    @Override
    public List<MappingEntry> loadMappings(Version version, Path unused) {
        Path dir = version.equals(sourceDir.getFileName()) ? sourceDir : targetDir;
        // Actually we load source and target separately in the CLI — so we use version matching
        return loadFromDirectory(dir);
    }

    /**
     * Load all mapping entries from a local directory.
     */
    public List<MappingEntry> loadFromDirectory(Path dir) {
        LOG.info("Loading local mappings from: {}", dir);
        List<MappingEntry> entries = new ArrayList<>();

        if (!Files.isDirectory(dir)) {
            LOG.warn("Local mappings directory not found: {}", dir);
            return entries;
        }

        try (var files = Files.walk(dir, 2)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString().toLowerCase();
                try {
                    if (name.endsWith(".csv")) {
                        // Delegate to MCP CSV parser logic
                        parseLocalCSV(file, entries);
                    } else if (name.endsWith(".tiny") || name.endsWith(".tiny2")) {
                        // Delegate to Yarn tiny parser logic
                        parseLocalTiny(file, entries);
                    } else if (name.endsWith(".srg")) {
                        parseLocalSRG(file, entries);
                    } else if (name.endsWith(".tsrg")) {
                        parseLocalTSRG(file, entries);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to parse local mapping file: {} - {}", file, e.getMessage());
                }
            });
        } catch (IOException e) {
            LOG.warn("Failed to scan local mappings directory: {}", e.getMessage());
        }

        LOG.info("Loaded {} mapping entries from local: {}", entries.size(), dir);
        return entries;
    }

    @Override
    public MappingType getMappingType() { return mappingType; }

    @Override
    public String getProviderName() { return "Local files (" + sourceDir + ")"; }

    private Path resolveMappingDir(Path base, Version v) {
        // Check if base/v/ exists, else use base directly (for two-level: --source/--target)
        Path versioned = base.resolve(v.getRaw());
        if (Files.isDirectory(versioned)) return versioned;
        return base;
    }

    // --- Delegated parsers (reuse MCP/Yarn service logic) ---

    private void parseLocalCSV(Path file, List<MappingEntry> entries) {
        MCPMappingService.parseCSVStatic(file,
                file.getFileName().toString().contains("method")
                        ? MappingEntry.MappingType.METHOD
                        : MappingEntry.MappingType.FIELD,
                entries);
    }

    private void parseLocalTiny(Path file, List<MappingEntry> entries) {
        YarnMappingService.parseTinyStatic(file, entries);
    }

    private void parseLocalSRG(Path file, List<MappingEntry> entries) {
        MCPMappingService.parseSRGStatic(file, entries);
    }

    private void parseLocalTSRG(Path file, List<MappingEntry> entries) {
        MCPMappingService.parseTSRGStatic(file, entries);
    }
}
