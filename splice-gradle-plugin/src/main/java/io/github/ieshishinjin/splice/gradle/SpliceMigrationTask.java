package io.github.ieshishinjin.splice.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;

/**
 * Gradle task that invokes the Splice CLI as a JavaExec process.
 */
public abstract class SpliceMigrationTask extends DefaultTask {

    @Input
    public abstract Property<String> getSourceVersion();

    @Input
    public abstract Property<String> getTargetVersion();

    @Input
    public abstract Property<String> getLoader();

    @InputDirectory
    public abstract Property<File> getInputDir();

    @Optional
    @Input
    public abstract Property<File> getOutputDir();

    @Optional
    @Input
    public abstract Property<File> getMappingsDir();

    @Input
    public abstract Property<Boolean> getDryRun();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void migrate() {
        // Resolve the Splice CLI fat jar from the root project
        File spliceJar = new File(getProject().getRootProject()
                .getLayout().getBuildDirectory().getAsFile().get(),
                "libs/Splice-1.0.0-all.jar");

        if (!spliceJar.exists()) {
            throw new RuntimeException("Splice fat JAR not found. Run `./gradlew :shadowJar` " +
                    "from the root project first, or use the standalone CLI jar.");
        }

        // Build args
        var args = new java.util.ArrayList<String>();
        args.add("-s");
        args.add(getSourceVersion().get());
        args.add("-t");
        args.add(getTargetVersion().get());
        args.add("-l");
        args.add(getLoader().get());
        args.add("-i");
        args.add(getInputDir().get().getAbsolutePath());

        if (getOutputDir().isPresent()) {
            args.add("-o");
            args.add(getOutputDir().get().getAbsolutePath());
        }

        if (getMappingsDir().isPresent()) {
            args.add("-m");
            args.add(getMappingsDir().get().getAbsolutePath());
        }

        if (getDryRun().get()) {
            args.add("--dry-run");
        }

        getLogger().lifecycle("Splice migration: {} -> {} (loader: {})",
                getSourceVersion().get(), getTargetVersion().get(), getLoader().get());
        getLogger().lifecycle("Running: java -jar {} {}", spliceJar.getName(), String.join(" ", args));

        // Execute
        getExecOperations().javaexec(spec -> {
            spec.setExecutable("java");
            spec.args("-jar", spliceJar.getAbsolutePath());
            spec.args(args);
            spec.setWorkingDir(getProject().getRootDir());
        });
    }
}
