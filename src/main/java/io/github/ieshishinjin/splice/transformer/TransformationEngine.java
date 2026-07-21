package io.github.ieshishinjin.splice.transformer;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MigrationConfig;
import io.github.ieshishinjin.splice.model.MappingDiff;
import io.github.ieshishinjin.splice.processor.FileProcessor;
import io.github.ieshishinjin.splice.reporter.ConflictReporter;
import io.github.ieshishinjin.splice.updater.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Orchestrates the full migration process.
 * Uses AST-based transformation for Java source files,
 * bytecode remapping for .class files, and dedicated
 * updaters for mod metadata, mixins, access wideners/transformers.
 */
public class TransformationEngine {

    private static final Logger LOG = LoggerFactory.getLogger(TransformationEngine.class);
    private static final Set<String> MIXIN_CONFIG_PATTERNS = Set.of(
            ".mixins.json", "-mixins.json", "_mixins.json");

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

    public int run() throws IOException {
        LOG.info("Starting migration: {} -> {} (loader: {})",
                config.getSourceVersion(), config.getTargetVersion(), config.getLoaderType());
        LOG.info("Input: {}", config.getInputPath());
        LOG.info("Output: {}", config.getOutputPath());
        LOG.info("Total mapping changes: {}", diff.getTotalChanges());

        if (diff.getTotalChanges() == 0) {
            LOG.warn("No mapping differences found. Files will be copied as-is.");
        }

        if (!config.isDryRun()) {
            Files.createDirectories(config.getOutputPath());
        }

        int processedFiles;
        if (config.isJarInput()) {
            processedFiles = processJarFile();
        } else {
            processedFiles = processDirectory();
        }

        if (!config.isDryRun()) {
            conflictReporter.writeReport(config.getOutputPath().resolve("migration-report.json"), allConflicts);
        }
        conflictReporter.printSummary(processedFiles, allConflicts, diff);
        return processedFiles;
    }

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
        return 1;
    }

    private int processDirectory() throws IOException {
        Path inputDir = config.getInputPath();
        Path outputDir = config.getOutputPath();

        List<Path> sourceFiles;
        try (Stream<Path> walk = Files.walk(inputDir)) {
            sourceFiles = walk.filter(Files::isRegularFile)
                    .filter(this::isProcessableFile)
                    .toList();
        }

        LOG.info("Found {} processable files", sourceFiles.size());

        if (config.isDryRun()) {
            sourceFiles.forEach(f -> {
                Path rel = inputDir.relativize(f);
                LOG.info("[DRY-RUN] Would process: {} -> {}", f, outputDir.resolve(rel));
            });
            return sourceFiles.size();
        }

        fileProcessor.copyDirectoryStructure(inputDir, outputDir);

        List<Callable<Integer>> tasks = sourceFiles.stream()
                .<Callable<Integer>>map(file -> () -> processSingleFile(file, inputDir, outputDir))
                .toList();

        try {
            List<Future<Integer>> futures = executor.invokeAll(tasks);
            int total = 0;
            for (Future<Integer> f : futures) total += f.get();
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

    private int processSingleFile(Path file, Path inputDir, Path outputDir) {
        Path rel = inputDir.relativize(file);
        Path out = outputDir.resolve(rel);
        try {
            String name = file.toString().toLowerCase();
            if (name.endsWith(".java")) {
                return transformSourceFile(file, out);
            } else if (name.endsWith(".class")) {
                return transformClassFile(file, out);
            } else if (isMixinConfigFile(file)) {
                return transformGenericMetadata(file, out, new MixinConfigUpdater());
            } else if (isAccessWidenerFile(file)) {
                return transformGenericMetadata(file, out, new AccessWidenerUpdater());
            } else if (isAccessTransformerFile(file)) {
                return transformGenericMetadata(file, out, new AccessTransformerUpdater());
            } else if (isMetadataFile(file)) {
                return transformMetadataFile(file, out);
            } else {
                fileProcessor.copyFile(file, out);
                return 1;
            }
        } catch (Exception e) {
            allConflicts.add(new Conflict(Conflict.Severity.ERROR, Conflict.Category.IO_ERROR,
                    "Failed: " + e.getMessage(), file, 0, null));
            LOG.error("Error processing {}: {}", file, e.getMessage());
            return 0;
        }
    }

    /** Transform source with AST (preferred), falling back to regex. */
    private int transformSourceFile(Path inputFile, Path outputFile) throws IOException {
        String content = Files.readString(inputFile);

        // Try AST transformer first
        ASTSourceTransformer ast = new ASTSourceTransformer(diff);
        ASTSourceTransformer.TransformationResult result = ast.transform(content, inputFile);
        allConflicts.addAll(result.conflicts());

        // If AST didn't modify but we have changes, try regex fallback
        if (!result.modified()) {
            SourceTransformer regex = new SourceTransformer(diff);
            var regexResult = regex.transform(content, inputFile);
            allConflicts.addAll(regexResult.conflicts());

            if (regexResult.modified()) {
                Files.writeString(outputFile, regexResult.content());
                LOG.debug("Transformed (regex): {}", inputFile);
                return 1;
            }
        }

        if (result.modified()) {
            Files.writeString(outputFile, result.content());
            LOG.debug("Transformed (AST): {}", inputFile);
            return 1;
        }

        fileProcessor.copyFile(inputFile, outputFile);
        return 1;
    }

    private int transformClassFile(Path file, Path out) throws IOException {
        byte[] data = Files.readAllBytes(file);
        List<Conflict> classConflicts = new ArrayList<>();
        BytecodeTransformer bt = new BytecodeTransformer(diff);
        byte[] transformed = bt.transformClass(data, file.toString(), classConflicts);
        allConflicts.addAll(classConflicts);
        Files.write(out, transformed);
        return 1;
    }

    private int transformMetadataFile(Path file, Path out) throws IOException {
        MetadataUpdater updater = createMetadataUpdater(file);
        if (updater == null) { fileProcessor.copyFile(file, out); return 1; }
        String content = Files.readString(file);
        String updated = updater.update(content, diff, file);
        allConflicts.addAll(updater.getConflicts());
        Files.writeString(out, updated);
        return 1;
    }

    private int transformGenericMetadata(Path file, Path out, MetadataUpdater updater) throws IOException {
        String content = Files.readString(file);
        String updated = updater.update(content, diff, file);
        allConflicts.addAll(updater.getConflicts());
        Files.writeString(out, updated);
        return 1;
    }

    private boolean isProcessableFile(Path f) {
        String n = f.toString().toLowerCase();
        return n.endsWith(".java") || n.endsWith(".class") || isMetadataFile(f)
                || isMixinConfigFile(f) || isAccessWidenerFile(f) || isAccessTransformerFile(f);
    }

    private boolean isMetadataFile(Path f) {
        String n = f.getFileName().toString().toLowerCase();
        return n.equals("mods.toml") || n.equals("fabric.mod.json") || n.equals("mcmod.info");
    }

    private boolean isMixinConfigFile(Path f) {
        String n = f.getFileName().toString().toLowerCase();
        return MIXIN_CONFIG_PATTERNS.stream().anyMatch(n::contains) && n.endsWith(".json");
    }

    private boolean isAccessWidenerFile(Path f) {
        return f.getFileName().toString().toLowerCase().endsWith(".accesswidener");
    }

    private boolean isAccessTransformerFile(Path f) {
        String n = f.getFileName().toString().toLowerCase();
        return n.contains("accesstransformer") || n.equals("accesstransformer.cfg");
    }

    private MetadataUpdater createMetadataUpdater(Path f) {
        String n = f.getFileName().toString().toLowerCase();
        if (n.equals("mods.toml") || n.equals("mcmod.info")) {
            return new ForgeMetadataUpdater(config.getTargetVersion().toString());
        } else if (n.equals("fabric.mod.json")) {
            return new FabricMetadataUpdater(config.getTargetVersion().toString());
        }
        return null;
    }
}
