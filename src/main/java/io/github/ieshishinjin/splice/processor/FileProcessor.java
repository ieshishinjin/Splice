package io.github.ieshishinjin.splice.processor;

import io.github.ieshishinjin.splice.model.MigrationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

/**
 * Handles file system operations for migration:
 * - Directory structure copying
 * - File copying
 * - File filtering
 */
public class FileProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(FileProcessor.class);

    private final MigrationConfig config;

    public FileProcessor(MigrationConfig config) {
        this.config = config;
    }

    /**
     * Copy the directory structure from input to output (preserving tree).
     */
    public void copyDirectoryStructure(Path sourceDir, Path targetDir) throws IOException {
        LOG.debug("Copying directory structure from {} to {}", sourceDir, targetDir);

        Files.walkFileTree(sourceDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        Path relativePath = sourceDir.relativize(dir);
                        Path targetPath = targetDir.resolve(relativePath);
                        if (!Files.exists(targetPath)) {
                            Files.createDirectories(targetPath);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        // Files are handled separately by the transformation engine
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    /**
     * Copy a single file from source to target.
     * Creates parent directories if needed.
     */
    public void copyFile(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    /**
     * Read file content as string.
     */
    public String readFile(Path file) throws IOException {
        return Files.readString(file);
    }

    /**
     * Write content to file, creating parent directories if needed.
     */
    public void writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
