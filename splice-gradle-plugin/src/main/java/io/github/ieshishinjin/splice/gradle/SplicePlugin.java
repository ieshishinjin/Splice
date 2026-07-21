package io.github.ieshishinjin.splice.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;

/**
 * Splice Gradle plugin — applies the migration tool as a build task.
 * <p>
 * Usage in build.gradle.kts:
 * <pre>
 * plugins {
 *     id("io.github.ieshishinjin.splice") version "1.0.0"
 * }
 *
 * splice {
 *     sourceVersion = "1.20.1"
 *     targetVersion = "1.21"
 *     loader = "forge"
 *     input = file("src/main/java")
 *     output = file("build/splice-migrated")
 * }
 * </pre>
 *
 * Then run:
 * <pre>
 * ./gradlew spliceMigrate
 * </pre>
 */
public class SplicePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        // Create extension for DSL configuration
        var extension = project.getExtensions()
                .create("splice", SpliceExtension.class);

        // Register the migration task
        TaskProvider<SpliceMigrationTask> migrateTask = project.getTasks()
                .register("spliceMigrate", SpliceMigrationTask.class, task -> {
                    task.setDescription("Migrate Minecraft mod to a new version using Splice");
                    task.setGroup("splice");
                    task.getSourceVersion().convention(extension.getSourceVersion());
                    task.getTargetVersion().convention(extension.getTargetVersion());
                    task.getLoader().convention(extension.getLoader());
                    task.getInputDir().convention(extension.getInput());
                    task.getOutputDir().convention(extension.getOutput());
                    task.getMappingsDir().convention(extension.getMappingsDir());
                    task.getDryRun().convention(extension.getDryRun());
                });

        // Quick task aliases
        project.getTasks().register("spliceDryRun", SpliceMigrationTask.class, task -> {
            task.setDescription("Preview Splice migration without writing files");
            task.setGroup("splice");
            task.getSourceVersion().convention(extension.getSourceVersion());
            task.getTargetVersion().convention(extension.getTargetVersion());
            task.getLoader().convention(extension.getLoader());
            task.getInputDir().convention(extension.getInput());
            task.getOutputDir().convention(extension.getOutput());
            task.getMappingsDir().convention(extension.getMappingsDir());
            task.getDryRun().set(true);
        });
    }
}
