package io.github.ieshishinjin.splice.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class SplicePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        var ext = project.getExtensions().create("splice", SpliceExtension.class);

        // 迁移任务
        TaskProvider<SpliceMigrationTask> migrate = project.getTasks()
                .register("spliceMigrate", SpliceMigrationTask.class, task -> {
                    task.setDescription("Run Splice mod migration");
                    task.setGroup("splice");
                    task.getSourceVersion().convention(ext.getSourceVersion());
                    task.getTargetVersions().convention(ext.getTargetVersions());
                    task.getLoader().convention(ext.getLoader());
                    task.getInputDir().convention(ext.getInput());
                    task.getOutputDir().convention(ext.getOutput());
                    task.getMappingsDir().convention(ext.getMappingsDir());
                    task.getDryRun().convention(ext.getDryRun());
                    task.getVerbose().convention(ext.getVerbose());
                    task.getThreads().convention(ext.getThreads());
                    task.getCacheDir().convention(ext.getCacheDir());
                });

        // dry-run 快捷任务
        project.getTasks().register("spliceDryRun", SpliceMigrationTask.class, task -> {
            task.setDescription("Preview Splice migration");
            task.setGroup("splice");
            task.getSourceVersion().convention(ext.getSourceVersion());
            task.getTargetVersions().convention(ext.getTargetVersions());
            task.getLoader().convention(ext.getLoader());
            task.getInputDir().convention(ext.getInput());
            task.getOutputDir().convention(ext.getOutput());
            task.getMappingsDir().convention(ext.getMappingsDir());
            task.getDryRun().set(true);
            task.getVerbose().convention(ext.getVerbose());
        });

        // 清理缓存任务
        project.getTasks().register("spliceCleanDeps", task -> {
            task.setDescription("Clean Splice dependency cache");
            task.setGroup("splice");
            task.doLast($ -> {
                project.getLogger().lifecycle("Running: java -jar ... --clean-deps");
                // 需要找到 jar 路径并执行
            });
        });
    }
}
