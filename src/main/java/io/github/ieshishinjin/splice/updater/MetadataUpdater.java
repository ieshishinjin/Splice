package io.github.ieshishinjin.splice.updater;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for updating mod metadata files (mods.toml, fabric.mod.json).
 */
public interface MetadataUpdater {

    /**
     * Update a metadata file's content with the new version and any
     * loader-specific changes needed.
     *
     * @param content    original file content
     * @param diff       the mapping diff (may contain relevant class changes)
     * @param filePath   path to the file (for conflict tracking)
     * @return updated file content
     */
    String update(String content, MappingDiff diff, Path filePath);

    /**
     * Get conflicts encountered during the update.
     */
    List<Conflict> getConflicts();
}
