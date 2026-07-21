package io.github.ieshishinjin.splice.mapping;

import io.github.ieshishinjin.splice.model.MappingEntry;
import io.github.ieshishinjin.splice.model.MappingType;
import io.github.ieshishinjin.splice.model.Version;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for loading Minecraft mappings (MCP or Yarn).
 */
public interface MappingService {

    /**
     * Load all mapping entries for a given Minecraft version.
     *
     * @param version  the Minecraft version
     * @param cacheDir directory to cache downloaded mappings
     * @return list of all mapping entries
     */
    List<MappingEntry> loadMappings(Version version, Path cacheDir);

    /**
     * Get the mapping type this service handles.
     */
    MappingType getMappingType();

    /**
     * Get the display name of this mapping provider.
     */
    String getProviderName();
}
