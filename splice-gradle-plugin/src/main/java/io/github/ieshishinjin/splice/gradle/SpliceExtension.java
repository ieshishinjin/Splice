package io.github.ieshishinjin.splice.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import javax.inject.Inject;
import java.io.File;

public abstract class SpliceExtension {

    @Input
    public abstract Property<String> getSourceVersion();

    @Input
    public abstract ListProperty<String> getTargetVersions();

    @Input
    public abstract Property<String> getLoader();

    @Input
    public abstract Property<File> getInput();

    @Optional @Input
    public abstract Property<File> getOutput();

    @Optional @Input
    public abstract Property<File> getMappingsDir();

    @Input
    public abstract Property<Boolean> getDryRun();

    @Input
    public abstract Property<Boolean> getVerbose();

    @Optional @Input
    public abstract Property<Integer> getThreads();

    @Optional @Input
    public abstract Property<String> getCacheDir();

    @Inject
    public SpliceExtension() {
        getDryRun().convention(false);
        getVerbose().convention(false);
    }
}
