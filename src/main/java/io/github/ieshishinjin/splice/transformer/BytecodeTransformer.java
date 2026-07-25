package io.github.ieshishinjin.splice.transformer;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Transforms bytecode in .class files (inside .jar archives or standalone).
 * Uses ASM's ClassRemapper to rewrite class, method, and field references.
 *
 * 先将 jar 解压到临时目录，处理 class 后再重新打包，
 * 确保所有资源文件（贴图、模型、语言文件等）完整保留。
 */
public class BytecodeTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(BytecodeTransformer.class);

    private final MappingDiff diff;
    private final Map<String, String> classMappings;
    private final List<Conflict> conflicts;

    public BytecodeTransformer(MappingDiff diff) {
        this.diff = diff;
        this.conflicts = new ArrayList<>();
        this.classMappings = buildFullClassMappings();
    }

    /**
     * Transform a .jar file：解压 → 处理 class → 重新打包。
     */
    public List<Conflict> transformJar(Path inputJar, Path outputJar) throws IOException {
        LOG.info("Transforming JAR: {} -> {}", inputJar, outputJar);

        // 创建临时工作目录
        Path tempDir = Files.createTempDirectory("splice-jar-");
        try {
            // 1. 解压 jar 到临时目录
            extractJar(inputJar, tempDir);

            // 2. 遍历并处理 .class 文件
            List<Conflict> jarConflicts = new ArrayList<>();
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".class")) {
                        try {
                            byte[] data = Files.readAllBytes(file);
                            String relPath = tempDir.relativize(file).toString().replace(File.separatorChar, '/');
                            byte[] transformed = transformClass(data, relPath, jarConflicts);
                            String mappedPath = remapClassPath(relPath);
                            Path target = tempDir.resolve(mappedPath);
                            if (!mappedPath.equals(relPath)) {
                                Files.createDirectories(target.getParent());
                                Files.deleteIfExists(file);
                                // 删除旧目录
                                Path oldParent = file.getParent();
                                Files.write(target, transformed);
                                deleteEmptyDirs(oldParent);
                            } else {
                                Files.write(file, transformed);
                            }
                        } catch (Exception e) {
                            jarConflicts.add(new Conflict(
                                    Conflict.Severity.ERROR, Conflict.Category.BYTECODE_ISSUE,
                                    "Failed to transform: " + file + " - " + e.getMessage(),
                                    inputJar, 0, null));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // 3. 重新打包为 jar
            Files.createDirectories(outputJar.getParent());
            packJar(tempDir, outputJar);

            jarConflicts.forEach(c -> LOG.warn("JAR conflict: {}", c));
            return jarConflicts;

        } finally {
            // 清理临时目录
            deleteDir(tempDir);
        }
    }

    /** 解压 jar 到目录 */
    private void extractJar(Path jarPath, Path targetDir) throws IOException {
        try (JarInputStream jis = new JarInputStream(Files.newInputStream(jarPath))) {
            // JarInputStream 构造函数已读取 MANIFEST.MF，需单独写出
            Manifest manifest = jis.getManifest();
            if (manifest != null) {
                Path metaInf = targetDir.resolve("META-INF");
                Files.createDirectories(metaInf);
                try (OutputStream mos = Files.newOutputStream(metaInf.resolve("MANIFEST.MF"))) {
                    manifest.write(mos);
                }
            }

            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                Path outPath = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(jis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                jis.closeEntry();
            }
        }
    }

    /** 从目录打包 jar（MANIFEST.MF 放首位） */
    private void packJar(Path sourceDir, Path outputJar) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputJar))) {
            // 收集所有文件，把 MANIFEST.MF 放第一位
            List<Path> allFiles = new ArrayList<>();
            List<Path> allDirs = new ArrayList<>();
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    allFiles.add(f); return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (!d.equals(sourceDir)) allDirs.add(d);
                    return FileVisitResult.CONTINUE;
                }
            });

            // MANIFEST.MF 排最前
            allFiles.sort((a, b) -> {
                boolean aIsManifest = a.endsWith("MANIFEST.MF");
                boolean bIsManifest = b.endsWith("MANIFEST.MF");
                if (aIsManifest && !bIsManifest) return -1;
                if (!aIsManifest && bIsManifest) return 1;
                return a.toString().compareTo(b.toString());
            });

            // 先写目录条目
            allDirs.sort(Comparator.comparing(Path::toString));
            for (Path dir : allDirs) {
                String name = sourceDir.relativize(dir).toString().replace(File.separatorChar, '/') + "/";
                zos.putNextEntry(new ZipEntry(name));
                zos.closeEntry();
            }

            // 再写文件条目
            for (Path file : allFiles) {
                String entryName = sourceDir.relativize(file).toString().replace(File.separatorChar, '/');
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(Files.getLastModifiedTime(file).toMillis());
                entry.setSize(Files.size(file));
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
    }

    /** 转换单个 class 文件字节码 */
    public byte[] transformClass(byte[] classBytes, String className, List<Conflict> conflictList) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        Remapper remapper = new SpliceRemapper(diff, className, conflictList);
        ClassRemapper classRemapper = new ClassRemapper(writer, remapper);

        try {
            reader.accept(classRemapper, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Exception e) {
            conflictList.add(new Conflict(
                    Conflict.Severity.WARNING, Conflict.Category.BYTECODE_ISSUE,
                    "ASM issue in " + className + ": " + e.getMessage(),
                    null, 0, "Class may need manual migration"));
            writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            classRemapper = new ClassRemapper(writer, remapper);
            reader.accept(classRemapper, 0);
            return writer.toByteArray();
        }
    }

    private Map<String, String> buildFullClassMappings() {
        Map<String, String> full = new LinkedHashMap<>();
        for (var entry : diff.getClassMappings().entrySet()) {
            String src = entry.getKey(), tgt = entry.getValue();
            full.put(src.replace('.', '/'), tgt.replace('.', '/'));
            full.put(src, tgt);
        }
        return full;
    }

    private String remapClassPath(String classPath) {
        if (!classPath.endsWith(".class")) return classPath;
        String internal = classPath.substring(0, classPath.length() - 6);
        String mapped = classMappings.get(internal);
        if (mapped != null) return mapped + ".class";
        return classPath;
    }

    private void deleteEmptyDirs(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (var list = Files.list(dir)) {
                    if (list.findAny().isEmpty()) {
                        Files.delete(dir);
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private void deleteDir(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Files.delete(f); return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                    Files.delete(d); return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }

    // =============== ASM Remapper ===============

    private static class SpliceRemapper extends Remapper {
        private final MappingDiff diff;
        private final String className;
        private final List<Conflict> conflicts;

        SpliceRemapper(MappingDiff diff, String className, List<Conflict> conflicts) {
            this.diff = diff;
            this.className = className;
            this.conflicts = conflicts;
        }

        @Override
        public String map(String internalName) {
            String mapped = diff.getClassMappings().get(internalName);
            if (mapped != null && !mapped.equals(internalName)) {
                return mapped.replace('.', '/');
            }
            String dotted = internalName.replace('/', '.');
            mapped = diff.getClassMappings().get(dotted);
            if (mapped != null) return mapped.replace('.', '/');
            return internalName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            String mapped = diff.getMethodMappings().get(name);
            return (mapped != null && !mapped.equals(name)) ? mapped : name;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String mapped = diff.getFieldMappings().get(name);
            return (mapped != null && !mapped.equals(name)) ? mapped : name;
        }

        @Override
        public String mapRecordComponentName(String owner, String name, String descriptor) {
            String mapped = diff.getFieldMappings().get(name);
            return (mapped != null && !mapped.equals(name)) ? mapped : name;
        }
    }
}
