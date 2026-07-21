package io.github.ieshishinjin.splice.transformer;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;
import java.util.stream.Collectors;

/**
 * Transforms bytecode in .class files (inside .jar archives or standalone).
 * Uses ASM's ClassRemapper to rewrite class, method, and field references.
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
     * Transform a .jar file: extract, remap, repackage.
     *
     * @param inputJar  input jar path
     * @param outputJar output jar path
     * @return list of conflicts encountered
     */
    public List<Conflict> transformJar(Path inputJar, Path outputJar) throws IOException {
        LOG.info("Transforming JAR: {} -> {}", inputJar, outputJar);

        List<Conflict> jarConflicts = new ArrayList<>();

        try (JarInputStream jis = new JarInputStream(Files.newInputStream(inputJar));
             JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outputJar))) {

            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                if (entry.isDirectory()) {
                    jos.putNextEntry(new JarEntry(entryName));
                    jos.closeEntry();
                    continue;
                }

                byte[] data = readAllBytes(jis);

                if (entryName.endsWith(".class")) {
                    try {
                        byte[] transformed = transformClass(data, entryName, jarConflicts);
                        String newClassName = remapClassPath(entryName);
                        JarEntry newEntry = new JarEntry(newClassName);
                        copyEntryAttributes(entry, newEntry);
                        jos.putNextEntry(newEntry);
                        jos.write(transformed);
                    } catch (Exception e) {
                        jarConflicts.add(new Conflict(
                                Conflict.Severity.ERROR,
                                Conflict.Category.BYTECODE_ISSUE,
                                "Failed to transform class: " + entryName + " - " + e.getMessage(),
                                inputJar, 0, "Check compatibility or manual fix required"));
                        // Write original class data as fallback
                        jos.putNextEntry(new JarEntry(entryName));
                        jos.write(data);
                    }
                } else if (entryName.equals("META-INF/MANIFEST.MF")) {
                    // Keep manifest as-is
                    jos.putNextEntry(new JarEntry(entryName));
                    jos.write(data);
                } else {
                    // Copy non-class resources as-is
                    jos.putNextEntry(new JarEntry(entryName));
                    jos.write(data);
                }

                jis.closeEntry();
            }
        }

        jarConflicts.forEach(c -> LOG.warn("JAR conflict: {}", c));
        return jarConflicts;
    }

    /**
     * Transform a single .class file.
     */
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
                    Conflict.Severity.WARNING,
                    Conflict.Category.BYTECODE_ISSUE,
                    "ASM transformation issue in " + className + ": " + e.getMessage(),
                    null, 0, "Class may need manual migration"));
            // Fallback: re-read without frame expansion
            writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            classRemapper = new ClassRemapper(writer, remapper);
            reader.accept(classRemapper, 0);
            return writer.toByteArray();
        }
    }

    /**
     * Build a complete class name mapping including internal JVM names.
     */
    private Map<String, String> buildFullClassMappings() {
        Map<String, String> full = new LinkedHashMap<>();

        for (var entry : diff.getClassMappings().entrySet()) {
            String src = entry.getKey();
            String tgt = entry.getValue();

            // Internal JVM names (e.g., net/minecraft/block/Block)
            String srcInternal = src.replace('.', '/');
            String tgtInternal = tgt.replace('.', '/');
            full.put(srcInternal, tgtInternal);

            // Dot-separated names
            full.put(src, tgt);
        }

        return full;
    }

    /**
     * Remap a class file path to its new name.
     * e.g., "net/minecraft/block/Block.class" -> "net/minecraft/block/CustomBlock.class"
     */
    private String remapClassPath(String classPath) {
        if (!classPath.endsWith(".class")) return classPath;

        String internalName = classPath.substring(0, classPath.length() - 6);
        String mapped = classMappings.get(internalName);
        if (mapped != null) {
            return mapped + ".class";
        }
        return classPath;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int n;
        while ((n = is.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    private void copyEntryAttributes(JarEntry source, JarEntry target) {
        target.setTime(source.getTime());
        target.setComment(source.getComment());
    }

    // =============== Inner Remapper ===============

    /**
     * Custom ASM Remapper that applies the mapping diff to bytecode.
     */
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
                String jvmMapped = mapped.replace('.', '/');
                LOG.debug("Class remap: {} -> {} (in {})", internalName, jvmMapped, className);
                return jvmMapped;
            }

            // Also check with dots
            String dotted = internalName.replace('/', '.');
            mapped = diff.getClassMappings().get(dotted);
            if (mapped != null) {
                return mapped.replace('.', '/');
            }

            return internalName;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            String mapped = diff.getMethodMappings().get(name);
            if (mapped != null && !mapped.equals(name)) {
                LOG.debug("Method remap: {}.{} -> {} (in {})", owner, name, mapped, className);
                return mapped;
            }
            return name;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            String mapped = diff.getFieldMappings().get(name);
            if (mapped != null && !mapped.equals(name)) {
                LOG.debug("Field remap: {}.{} -> {} (in {})", owner, name, mapped, className);
                return mapped;
            }
            return name;
        }

        @Override
        public String mapRecordComponentName(String owner, String name, String descriptor) {
            String mapped = diff.getFieldMappings().get(name);
            if (mapped != null && !mapped.equals(name)) {
                return mapped;
            }
            return name;
        }
    }
}
