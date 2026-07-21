package io.github.ieshishinjin.splice.mapping;

import io.github.ieshishinjin.splice.model.MappingType;
import io.github.ieshishinjin.splice.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and caches mapping files from official sources.
 * Supports MCP (Forge) and Yarn (Fabric) mappings.
 */
public class MappingDownloader {

    private static final Logger LOG = LoggerFactory.getLogger(MappingDownloader.class);

    private static final String MCP_MAVEN_URL = "https://files.minecraftforge.net/maven/de/oceanlabs/mcp";
    private static final String MCP_BOT_URL = "https://mcp.onesixnine.net/versions";
    private static final String YARN_MAVEN_URL = "https://maven.fabricmc.net/net/fabricmc/yarn";

    private final HttpClient httpClient;

    public MappingDownloader() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Download and cache mapping files for the given version and type.
     *
     * @param version  Minecraft version
     * @param type     MCP or YARN
     * @param cacheDir directory to store cached files
     * @return path to the cached/extracted mapping directory or file
     */
    public Path downloadMappings(Version version, MappingType type, Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);

        return switch (type) {
            case MCP -> downloadMCPMappings(version, cacheDir);
            case YARN -> downloadYarnMappings(version, cacheDir);
        };
    }

    private Path downloadMCPMappings(Version version, Path cacheDir) throws IOException {
        String versionStr = version.getRaw();
        Path configDir = cacheDir.resolve("mcp_" + versionStr);

        // Check if already cached
        if (isMCPMappingCached(configDir)) {
            LOG.info("MCP mappings for {} found in cache: {}", versionStr, configDir);
            return configDir;
        }

        // Try multiple possible URL patterns for MCP config
        List<String> urlsToTry = new java.util.ArrayList<>();

        // Pattern 1: mcp_config/{version}/mcp_config-{version}.zip (modern Forge)
        urlsToTry.add(MCP_MAVEN_URL + "/mcp_config/" + versionStr + "/mcp_config-" + versionStr + ".zip");

        // Pattern 2: mcp_config/mcp_config-{version}.zip (legacy)
        urlsToTry.add(MCP_MAVEN_URL + "/mcp_config/mcp_config-" + versionStr + ".zip");

        // Pattern 3: Try MCPBot export page
        urlsToTry.add(MCP_BOT_URL + "/" + versionStr + "/mcp-" + versionStr + ".zip");

        Path zipPath = cacheDir.resolve("mcp_config_" + versionStr + ".zip");
        boolean downloaded = false;

        for (String url : urlsToTry) {
            try {
                LOG.info("Trying MCP mappings URL: {}", url);
                downloadFile(url, zipPath);
                downloaded = true;
                break;
            } catch (IOException e) {
                LOG.debug("URL failed: {} - {}", url, e.getMessage());
                Files.deleteIfExists(zipPath);
            }
        }

        if (!downloaded) {
            throw new IOException("Failed to download MCP mappings for " + versionStr
                    + " from any known URL. Try specifying mappings manually.");
        }

        // MCP config zip contains files under "config/" directory (TSRG format)
        // Also try extracting CSV files from different locations
        extractZip(zipPath, configDir, entry -> {
            String name = entry.getName();
            // Extract .srg, .tsrg, .csv files
            boolean isMappingFile = name.endsWith(".srg")
                    || name.endsWith(".tsrg")
                    || name.endsWith(".csv");
            // Check common directories: config/, mappings/, or root
            return isMappingFile;
        });

        // Rename files in config/ to root of mapping dir for easier access
        Path configSubdir = configDir.resolve("config");
        if (Files.isDirectory(configSubdir)) {
            try (var files = Files.newDirectoryStream(configSubdir)) {
                for (Path file : files) {
                    Path target = configDir.resolve(file.getFileName());
                    if (!Files.exists(target)) {
                        Files.move(file, target);
                    }
                }
            }
        }

        // If we only got SRG/TSRG files but not CSV files (MCP names),
        // we need to also download the MCP named mappings (mcp_stable or mcp_snapshot)
        if (!hasCSVFiles(configDir)) {
            LOG.info("No CSV files found in mcp_config, attempting to download MCP named mappings...");
            downloadMCPNamedMappings(versionStr, configDir);
        }

        LOG.info("MCP mappings extracted to: {}", configDir);
        return configDir;
    }

    /**
     * Download MCP named mappings (fields.csv, methods.csv) from stable or snapshot channels.
     */
    private void downloadMCPNamedMappings(String versionStr, Path configDir) throws IOException {
        // Try different stable versions for this Minecraft version
        // The mapping from MC version -> stable# is not fixed, so we try a range
        List<String> stableVersions = resolveStableVersions(versionStr);

        for (String stable : stableVersions) {
            try {
                String url = MCP_MAVEN_URL + "/mcp_stable/" + stable + "/mcp_stable-" + stable + ".zip";
                LOG.info("Trying MCP stable mappings: {}", url);
                Path stableZip = configDir.resolve("mcp_stable-" + stable + ".zip");
                downloadFile(url, stableZip);
                extractZip(stableZip, configDir, entry -> entry.getName().endsWith(".csv"));
                Files.deleteIfExists(stableZip);

                if (hasCSVFiles(configDir)) {
                    LOG.info("Found MCP named mappings (stable {}) for {}", stable, versionStr);
                    return;
                }
            } catch (IOException e) {
                LOG.debug("Stable {} failed: {}", stable, e.getMessage());
            }
        }

        // Try snapshot as fallback
        String snapshotDate = resolveSnapshotDate(versionStr);
        if (snapshotDate != null) {
            try {
                String url = MCP_MAVEN_URL + "/mcp_snapshot/" + snapshotDate + "/mcp_snapshot-" + snapshotDate + ".zip";
                LOG.info("Trying MCP snapshot mappings: {}", url);
                Path snapshotZip = configDir.resolve("mcp_snapshot-" + snapshotDate + ".zip");
                downloadFile(url, snapshotZip);
                extractZip(snapshotZip, configDir, entry -> entry.getName().endsWith(".csv"));
                Files.deleteIfExists(snapshotZip);
            } catch (IOException e) {
                LOG.debug("Snapshot {} failed: {}", snapshotDate, e.getMessage());
            }
        }

        if (!hasCSVFiles(configDir)) {
            LOG.warn("Could not download MCP named mappings for {}. " +
                    "Will use SRG-only mappings (class-level only).", versionStr);
        }
    }

    /**
     * Try to resolve known stable mapping versions for a Minecraft version.
     */
    private List<String> resolveStableVersions(String mcVersion) {
        // Known mappings: for common versions
        // These are approximate mappings between MC versions and stable mapping releases
        java.util.Map<String, List<String>> knownStables = java.util.Map.ofEntries(
                java.util.Map.entry("1.21", java.util.List.of("60", "59")),
                java.util.Map.entry("1.20.6", java.util.List.of("59", "58")),
                java.util.Map.entry("1.20.5", java.util.List.of("58", "57")),
                java.util.Map.entry("1.20.4", java.util.List.of("57", "56")),
                java.util.Map.entry("1.20.2", java.util.List.of("56", "55")),
                java.util.Map.entry("1.20.1", java.util.List.of("55", "54", "53")),
                java.util.Map.entry("1.20", java.util.List.of("53", "52")),
                java.util.Map.entry("1.19.4", java.util.List.of("52", "51")),
                java.util.Map.entry("1.19.3", java.util.List.of("51", "50")),
                java.util.Map.entry("1.19.2", java.util.List.of("50", "49")),
                java.util.Map.entry("1.19.1", java.util.List.of("49", "48")),
                java.util.Map.entry("1.19", java.util.List.of("48", "47")),
                java.util.Map.entry("1.18.2", java.util.List.of("47", "46")),
                java.util.Map.entry("1.18.1", java.util.List.of("46", "45")),
                java.util.Map.entry("1.18", java.util.List.of("45", "44"))
        );

        List<String> stables = knownStables.get(mcVersion);
        if (stables != null) return stables;

        // Fallback: try stables 30-60 (broad range)
        List<String> fallback = new java.util.ArrayList<>();
        for (int i = 60; i >= 30; i--) {
            fallback.add(String.valueOf(i));
        }
        return fallback;
    }

    /**
     * Try to resolve a snapshot date for a Minecraft version.
     */
    private String resolveSnapshotDate(String mcVersion) {
        // Recently, Forge uses YYYYMMDD snapshot format
        // This is a best-effort mapping
        java.util.Map<String, String> knownSnapshots = java.util.Map.of(
                "1.21", "20240613",
                "1.20.6", "20240422",
                "1.20.4", "20231218"
        );
        return knownSnapshots.get(mcVersion);
    }

    private boolean isMCPMappingCached(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        // Check for key mapping files (SRG or CSV)
        boolean hasSRG = false;
        boolean hasCSV = false;
        try (var files = Files.walk(dir, 2)) {
            for (var p : (Iterable<Path>) files::iterator) {
                String name = p.getFileName().toString();
                if (name.endsWith(".srg") || name.endsWith(".tsrg")) hasSRG = true;
                if (name.endsWith(".csv")) hasCSV = true;
            }
        } catch (IOException e) {
            return false;
        }
        return hasSRG || hasCSV;
    }

    private boolean hasCSVFiles(Path dir) {
        try (var files = Files.walk(dir, 1)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".csv"));
        } catch (IOException e) {
            return false;
        }
    }

    private Path downloadYarnMappings(Version version, Path cacheDir) throws IOException {
        String versionStr = version.getRaw();
        Path yarnDir = cacheDir.resolve("yarn_" + versionStr);

        // Check if already cached
        if (isYarnMappingCached(yarnDir)) {
            LOG.info("Yarn mappings for {} found in cache: {}", versionStr, yarnDir);
            return yarnDir;
        }

        // Try multiple Yarn artifact versions (the version may be "1.20.1+build.10" or similar)
        // First try the exact version, then try with build number
        String yarnVersion = versionStr;

        // Yarn jar URL
        String url = YARN_MAVEN_URL + "/" + yarnVersion.replace('.', '/')
                + "/yarn-" + yarnVersion + "-mergedv2.jar";
        LOG.info("Downloading Yarn mappings from: {}", url);

        Path jarPath = cacheDir.resolve("yarn_" + versionStr + ".jar");
        try {
            downloadFile(url, jarPath);
        } catch (IOException e) {
            // Try alternate URL patterns for Yarn
            url = YARN_MAVEN_URL + "/" + yarnVersion.replace('.', '/')
                    + "/yarn-" + yarnVersion + ".jar";
            LOG.info("Retrying Yarn download from: {}", url);
            downloadFile(url, jarPath);
        }

        // Extract tiny mapping files
        extractZip(jarPath, yarnDir, entry -> {
            String name = entry.getName();
            return name.endsWith(".tiny") || name.endsWith(".tiny2")
                    || name.contains("mappings/") || name.equals("mappings");
        });

        LOG.info("Yarn mappings extracted to: {}", yarnDir);
        return yarnDir;
    }

    private boolean isYarnMappingCached(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        // Check for tiny files
        try (var files = Files.walk(dir, 1)) {
            return files.anyMatch(p -> p.toString().endsWith(".tiny")
                    || p.toString().endsWith(".tiny2"));
        } catch (IOException e) {
            return false;
        }
    }

    private void downloadFile(String url, Path target) throws IOException {
        if (Files.exists(target)) {
            LOG.debug("File already exists: {}", target);
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to download " + url + ": HTTP " + response.statusCode());
            }

            Files.createDirectories(target.getParent());
            try (InputStream is = response.body()) {
                Files.copy(is, target);
            }

            LOG.info("Downloaded: {} -> {}", url, target);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    private void extractZip(Path zipPath, Path targetDir, java.util.function.Predicate<ZipEntry> filter)
            throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!filter.test(entry)) continue;

                Path entryPath = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath);
                }
                zis.closeEntry();
            }
        }
    }
}
