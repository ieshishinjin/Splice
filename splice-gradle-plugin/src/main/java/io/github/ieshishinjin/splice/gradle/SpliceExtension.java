package io.github.ieshishinjin.splice.gradle;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.io.File;

/**
 * Gradle DSL extension for Splice configuration.
 *
 * <pre>
 * splice {
 *     sourceVersion = "1.20.1"
 *     targetVersion = "1.21"
 *     loader = "forge"
 *     input = file("src/main/java")
 *     output = file("build/splice-migrated")
 *     mappingsDir = file("my-mappings")
 *     dryRun = false
 * }
 * </pre>
 */
public abstract class SpliceExtension {

    @Input
    public abstract Property<String> getSourceVersion();

    @Input
    public abstract Property<String> getTargetVersion();

    @Input
    public abstract Property<String> getLoader();

    @Input
    public abstract Property<File> getInput();

    @Optional
    @Input
    public abstract Property<File> getOutput();

    @Optional
    @Input
    public abstract Property<File> getMappingsDir();

    @Input
    public abstract Property<Boolean> getDryRun();

    @Inject
    public SpliceExtension() {
        getDryRun().convention(false);
    }
}
