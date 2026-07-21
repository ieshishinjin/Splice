package io.github.ieshishinjin.splice.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Complete configuration for a single migration run.
 */
public class MigrationConfig {

    private final Version sourceVersion;
    private final Version targetVersion;
    private final LoaderType loaderType;
    private final MappingType mappingType;
    private final Path inputPath;          // Source directory or .jar file
    private final Path outputPath;         // Output directory (default: input + "-migrated")
    private final Path cacheDir;           // Mappings cache directory
    private final boolean verbose;
    private final boolean dryRun;          // Preview only, don't write
    private final int threads;             // Parallelism

    private MigrationConfig(Builder builder) {
        this.sourceVersion = Objects.requireNonNull(builder.sourceVersion, "sourceVersion");
        this.targetVersion = Objects.requireNonNull(builder.targetVersion, "targetVersion");
        this.loaderType = Objects.requireNonNull(builder.loaderType, "loaderType");
        this.mappingType = MappingType.fromLoader(loaderType);
        this.inputPath = Objects.requireNonNull(builder.inputPath, "inputPath");
        this.outputPath = builder.outputPath != null
                ? builder.outputPath
                : Path.of(builder.inputPath + "-migrated");
        this.cacheDir = builder.cacheDir != null
                ? builder.cacheDir
                : Path.of(System.getProperty("user.home"), ".splice", "mappings");
        this.verbose = builder.verbose;
        this.dryRun = builder.dryRun;
        this.threads = builder.threads > 0 ? builder.threads : Runtime.getRuntime().availableProcessors();
    }

    // Getters
    public Version getSourceVersion() { return sourceVersion; }
    public Version getTargetVersion() { return targetVersion; }
    public LoaderType getLoaderType() { return loaderType; }
    public MappingType getMappingType() { return mappingType; }
    public Path getInputPath() { return inputPath; }
    public Path getOutputPath() { return outputPath; }
    public Path getCacheDir() { return cacheDir; }
    public boolean isVerbose() { return verbose; }
    public boolean isDryRun() { return dryRun; }
    public int getThreads() { return threads; }

    public boolean isJarInput() {
        String name = inputPath.toString().toLowerCase();
        return name.endsWith(".jar");
    }

    public boolean isDirectoryInput() {
        return inputPath.toFile().isDirectory();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Version sourceVersion;
        private Version targetVersion;
        private LoaderType loaderType;
        private Path inputPath;
        private Path outputPath;
        private Path cacheDir;
        private boolean verbose;
        private boolean dryRun;
        private int threads;

        public Builder sourceVersion(Version v) { this.sourceVersion = v; return this; }
        public Builder targetVersion(Version v) { this.targetVersion = v; return this; }
        public Builder loaderType(LoaderType l) { this.loaderType = l; return this; }
        public Builder inputPath(Path p) { this.inputPath = p; return this; }
        public Builder outputPath(Path p) { this.outputPath = p; return this; }
        public Builder cacheDir(Path p) { this.cacheDir = p; return this; }
        public Builder verbose(boolean v) { this.verbose = v; return this; }
        public Builder dryRun(boolean d) { this.dryRun = d; return this; }
        public Builder threads(int t) { this.threads = t; return this; }

        public MigrationConfig build() {
            return new MigrationConfig(this);
        }
    }

    @Override
    public String toString() {
        return "MigrationConfig{" +
                sourceVersion + " -> " + targetVersion +
                ", loader=" + loaderType +
                ", input=" + inputPath +
                ", output=" + outputPath +
                '}';
    }
}
