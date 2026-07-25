package io.github.ieshishinjin.splice;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * 清理 Splice 下载过的依赖缓存和映射表。
 * 只删 Splice 用过的，不影响本机其他项目的依赖。
 */
public class CleanDeps {

    // Splice 项目声明的所有依赖（从 build.gradle.kts 提取）
    private static final List<String> DEPENDENCIES = List.of(
            "info.picocli:picocli",
            "org.ow2.asm:asm",
            "org.ow2.asm:asm-commons",
            "org.ow2.asm:asm-util",
            "com.squareup.okhttp3:okhttp",
            "com.squareup.okio:okio",
            "com.google.code.gson:gson",
            "com.github.javaparser:javaparser-core",
            "com.github.javaparser:javaparser-symbol-solver-core",
            "org.slf4j:slf4j-api",
            "ch.qos.logback:logback-classic",
            "ch.qos.logback:logback-core",
            "org.junit.jupiter:junit-jupiter",
            "org.junit.platform:junit-platform-launcher",
            "org.opentest4j:opentest4j",
            "net.bytebuddy:byte-buddy-agent"
    );

    public static void run(Path cacheDir) {
        System.out.println("正在清理 Splice 依赖缓存...\n");

        long totalDeleted = 0;
        Path gradleCache = Path.of(System.getProperty("user.home"), ".gradle", "caches", "modules-2", "files-2.1");

        if (Files.isDirectory(gradleCache)) {
            for (String dep : DEPENDENCIES) {
                String[] parts = dep.split(":");
                String groupPath = parts[0].replace('.', '/');
                Path depDir = gradleCache.resolve(groupPath).resolve(parts[1]);
                if (Files.isDirectory(depDir)) {
                    long size = deleteDir(depDir);
                    if (size > 0) {
                        totalDeleted += size;
                        System.out.println("  ✗ " + dep);
                    }
                } else {
                    System.out.println("  · " + dep + " (未缓存)");
                }
            }
        }

        // 清理映射缓存
        Path mappings = cacheDir != null ? cacheDir : Path.of(System.getProperty("user.home"), ".splice", "mappings");
        if (Files.isDirectory(mappings)) {
            long size = deleteDir(mappings);
            totalDeleted += size;
            System.out.println("  ✗ 映射缓存: " + mappings);
        }

        // 清理 Splice 日志
        Path logs = Path.of(System.getProperty("user.home"), ".splice", "logs");
        if (Files.isDirectory(logs)) {
            long size = deleteDir(logs);
            totalDeleted += size;
            System.out.println("  ✗ 日志: " + logs);
        }

        System.out.println("\n✓ 清理完成，共释放 " + formatSize(totalDeleted));
    }

    private static long deleteDir(Path dir) {
        long[] size = {0};
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size[0] += attrs.size();
                    file.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) {
                    dir.toFile().delete();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("  删除失败: " + dir + " — " + e.getMessage());
        }
        return size[0];
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
