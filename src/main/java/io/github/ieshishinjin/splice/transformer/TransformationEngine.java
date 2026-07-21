package io.github.ieshishinjin.splice.transformer;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MigrationConfig;
import io.github.ieshishinjin.splice.model.MappingDiff;
import io.github.ieshishinjin.splice.processor.FileProcessor;
import io.github.ieshishinjin.splice.reporter.ConflictReporter;
import io.github.ieshishinjin.splice.updater.FabricMetadataUpdater;
import io.github.ieshishinjin.splice.updater.ForgeMetadataUpdater;
import io.github.ieshishinjin.splice.updater.MetadataUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Orchestrates the full migration process:
 * 1. Discovers files to transform
 * 2. Transforms source code / bytecode
 * 3. Updates metadata files
 * 4. Collects and reports conflicts
 */
public class TransformationEngine {

    private static final Logger LOG = LoggerFactory.getLogger(TransformationEngine.class);

    private final MigrationConfig config;
    private final MappingDiff diff;
    private final FileProcessor fileProcessor;
    private final List<Conflict> allConflicts;
    private final ConflictReporter conflictReporter;
    private final ExecutorService executor;

    public TransformationEngine(MigrationConfig config, MappingDiff diff) {
        this.config = config;
        this.diff = diff;
        this.fileProcessor = new FileProcessor(config);
        this.allConflicts = new CopyOnWriteArrayList<>();
        this.conflictReporter = new ConflictReporter();
        this.executor = Executors.newFixedThreadPool(config.getThreads());
    }

    /**
     * Execute the full migration.
     *
     * @return total number of files processed
     */
    public int run() throws IOException {
        LOG.info("Starting migration: {} -> {} (loader: {})",
                config.getSourceVersion(), config.getTargetVersion(), config.getLoaderType());
        LOG.info("Input: {}", config.getInputPath());
        LOG.info("Output: {}", config.getOutputPath());
        LOG.info("Total mapping changes: {}", diff.getTotalChanges());

        if (diff.getTotalChanges() == 0) {
            LOG.warn("No mapping differences found between {} and {}. " +
                    "Files will be copied as-is.", config.getSourceVersion(), config.getTargetVersion());
        }

        // Ensure output directory exists
        if (!config.isDryRun()) {
            Files.createDirectories(config.getOutputPath());
        }

        int processedFiles;

        if (config.isJarInput()) {
            processedFiles = processJarFile();
        } else {
            processedFiles = processDirectory();
        }

        // Write conflict report
        if (!config.isDryRun()) {
            conflictReporter.writeReport(config.getOutputPath().resolve("migration-report.json"), allConflicts);
        }

        // Print summary
        conflictReporter.printSummary(processedFiles, allConflicts, diff);

        return processedFiles;
    }

    /**
     * Process a single .jar file as input.
     */
    private int processJarFile() throws IOException {
        Path inputJar = config.getInputPath();
        Path outputJar = config.getOutputPath().resolve(inputJar.getFileName());

        if (config.isDryRun()) {
            LOG.info("[DRY-RUN] Would transform JAR: {} -> {}", inputJar, outputJar);
            return 1;
        }

        BytecodeTransformer bytecodeTransformer = new BytecodeTransformer(diff);
        List<Conflict> jarConflicts = bytecodeTransformer.transformJar(inputJar, outputJar);
        allConflicts.addAll(jarConflicts);

        // Also update metadata inside the JAR
        updateMetadataInJar(outputJar);

        return 1;
    }

    /**
     * Process a source directory.
     */
    private int processDirectory() throws IOException {
        Path inputDir = config.getInputPath();
        Path outputDir = config.getOutputPath();

        // Collect all source files
        List<Path> sourceFiles;
        try (Stream<Path> walk = Files.walk(inputDir)) {
            sourceFiles = walk.filter(Files::isRegularFile)
                    .filter(this::isProcessableFile)
                    .toList();
        }

        LOG.info("Found {} processable files", sourceFiles.size());

        if (config.isDryRun()) {
            sourceFiles.forEach(f -> {
                Path relativePath = inputDir.relativize(f);
                Path outputPath = outputDir.resolve(relativePath);
                LOG.info("[DRY-RUN] Would process: {} -> {}", f, outputPath);
            });
            return sourceFiles.size();
        }

        // Copy directory structure first
        fileProcessor.copyDirectoryStructure(inputDir, outputDir);

        // Process files in parallel
        List<Callable<Integer>> tasks = sourceFiles.stream()
                .<Callable<Integer>>map(file -> () -> processSingleFile(file, inputDir, outputDir))
                .toList();

        try {
            List<Future<Integer>> futures = executor.invokeAll(tasks);
            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get();
            }
            return total;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Processing interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("Processing failed", e.getCause());
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Process a single file (source code or bytecode).
     */
    private int processSingleFile(Path file, Path inputDir, Path outputDir) {
        Path relativePath = inputDir.relativize(file);
        Path outputPath = outputDir.resolve(relativePath);

        try {
            if (file.toString().endsWith(".java")) {
                return transformSourceFile(file, outputPath);
            } else if (file.toString().endsWith(".class")) {
                return transformClassFile(file, outputPath);
            } else if (isMetadataFile(file)) {
                return transformMetadataFile(file, outputPath);
            } else {
                // Copy non-transformable files as-is
                fileProcessor.copyFile(file, outputPath);
                return 1;
            }
        } catch (Exception e) {
            allConflicts.add(new Conflict(
                    Conflict.Severity.ERROR,
                    Conflict.Category.IO_ERROR,
                    "Failed to process file: " + e.getMessage(),
                    file, 0, "Check file permissions and format"));
            LOG.error("Error processing {}: {}", file, e.getMessage());
            return 0;
        }
    }

    /**
     * Transform a single Java source file.
     */
    private int transformSourceFile(Path inputFile, Path outputFile) throws IOException {
        String content = Files.readString(inputFile);
        SourceTransformer transformer = new SourceTransformer(diff);
        SourceTransformer.TransformationResult result = transformer.transform(content, inputFile);

        allConflicts.addAll(result.conflicts());

        if (result.modified()) {
            Files.writeString(outputFile, result.content());
            LOG.debug("Transformed: {}", inputFile);
        } else {
            // Copy original if no changes needed
            fileProcessor.copyFile(inputFile, outputFile);
        }
        return 1;
    }

    /**
     * Transform a single .class file.
     */
    private int transformClassFile(Path inputFile, Path outputFile) throws IOException {
        byte[] classBytes = Files.readAllBytes(inputFile);
        List<Conflict> classConflicts = new ArrayList<>();

        BytecodeTransformer bytecodeTransformer = new BytecodeTransformer(diff);
        String className = inputFile.toString().replace('/', '.');

        byte[] transformed = bytecodeTransformer.transformClass(classBytes, className, classConflicts);
        allConflicts.addAll(classConflicts);

        Files.write(outputFile, transformed);
        LOG.debug("Transformed class: {}", inputFile);
        return 1;
    }

    /**
     * Transform a metadata file (mods.toml or fabric.mod.json).
     */
    private int transformMetadataFile(Path inputFile, Path outputFile) throws IOException {
        MetadataUpdater updater = createMetadataUpdater(inputFile);
        if (updater != null) {
            String content = Files.readString(inputFile);
            String updated = updater.update(content, diff, inputFile);
            allConflicts.addAll(updater.getConflicts());
            Files.writeString(outputFile, updated);
            LOG.info("Updated metadata: {}", inputFile);
            return 1;
        }
        // Copy as-is if no updater matches
        fileProcessor.copyFile(inputFile, outputFile);
        return 1;
    }

    /**
     * Update metadata files inside a processed JAR.
     */
    private void updateMetadataInJar(Path jarPath) {
        // For JAR processing, metadata update is a placeholder since
        // we'd need to re-read the JAR and update specific entries.
        // This is logged as a note.
        LOG.info("Metadata update in JAR: {} (manual check recommended)", jarPath);
    }

    private boolean isProcessableFile(Path file) {
        String name = file.toString().toLowerCase();
        return name.endsWith(".java")
                || name.endsWith(".class")
                || isMetadataFile(file);
    }

    private boolean isMetadataFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.equals("mods.toml")
                || name.equals("fabric.mod.json")
                || name.equals("mods.toml")
                || name.equals("mcmod.info");
    }

    private MetadataUpdater createMetadataUpdater(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.equals("mods.toml") || name.equals("mcmod.info")) {
            return new ForgeMetadataUpdater(config.getTargetVersion().toString());
        } else if (name.equals("fabric.mod.json")) {
            return new FabricMetadataUpdater(config.getTargetVersion().toString());
        }
        return null;
    }
}
