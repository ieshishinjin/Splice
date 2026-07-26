package io.github.ieshishinjin.splice.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;

public abstract class SpliceMigrationTask extends DefaultTask {

    @Input public abstract Property<String> getSourceVersion();
    @Input public abstract ListProperty<String> getTargetVersions();
    @Input public abstract Property<String> getLoader();
    @InputDirectory public abstract Property<File> getInputDir();
    @Optional @Input public abstract Property<File> getOutputDir();
    @Optional @Input public abstract Property<File> getMappingsDir();
    @Input public abstract Property<Boolean> getDryRun();
    @Input public abstract Property<Boolean> getVerbose();
    @Optional @Input public abstract Property<Integer> getThreads();
    @Optional @Input public abstract Property<String> getCacheDir();

    @Inject protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void migrate() {
        File jar = new File(getProject().getRootProject()
                .getLayout().getBuildDirectory().getAsFile().get(), "libs/Splice-1.1.0-all.jar");
        if (!jar.exists()) {
            throw new RuntimeException("Splice JAR not found. Run `./gradlew shadowJar` from root.");
        }

        var args = new java.util.ArrayList<String>();
        args.add("-s"); args.add(getSourceVersion().get());
        for (String tv : getTargetVersions().get()) {
            args.add("-t"); args.add(tv);
        }
        args.add("-l"); args.add(getLoader().get());
        args.add("-i"); args.add(getInputDir().get().getAbsolutePath());

        if (getOutputDir().isPresent()) {
            args.add("-o"); args.add(getOutputDir().get().getAbsolutePath());
        }
        if (getMappingsDir().isPresent()) {
            args.add("-m"); args.add(getMappingsDir().get().getAbsolutePath());
        }
        if (getVerbose().get()) args.add("--verbose");
        if (getThreads().isPresent()) {
            args.add("--threads"); args.add(getThreads().get().toString());
        }
        if (getCacheDir().isPresent()) {
            args.add("-c"); args.add(getCacheDir().get());
        }
        if (getDryRun().get()) args.add("--dry-run");

        getLogger().lifecycle("Splice: {}→{} (loader={}, target={})",
                getSourceVersion().get(), String.join(",", getTargetVersions().get()),
                getLoader().get(), getInputDir().get());

        getExecOperations().javaexec(spec -> {
            spec.setExecutable("java");
            spec.args("-jar", jar.getAbsolutePath());
            spec.args(args);
            spec.setWorkingDir(getProject().getRootDir());
        });
    }
}
