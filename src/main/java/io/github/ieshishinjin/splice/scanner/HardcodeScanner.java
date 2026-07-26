package io.github.ieshishinjin.splice.scanner;

import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 扫描迁移后的代码，找出可能遗漏的硬编码引用并自动修复。
 * <p>
 * 比如: 字符串中的类名 "net/minecraft/block/Block"、反射调用等，
 * 这些不会被字节码 remapping 或源文件 AST 转换覆盖。
 */
public class HardcodeScanner {

    private static final Logger LOG = LoggerFactory.getLogger(HardcodeScanner.class);

    private final MappingDiff diff;
    private final List<Conflict> findings;
    private final List<Pattern> classPatterns;
    private final Map<String, String> classRepl;

    public HardcodeScanner(MappingDiff diff) {
        this.diff = diff;
        this.findings = new CopyOnWriteArrayList<>();
        this.classPatterns = buildPatterns();
        this.classRepl = new LinkedHashMap<>();
        for (var e : diff.getClassMappings().entrySet()) {
            classRepl.put(e.getKey(), e.getValue());
            classRepl.put(e.getKey().replace('.', '/'), e.getValue().replace('.', '/'));
        }
    }

    /**
     * 扫描目录中的所有源文件。
     */
    public List<Conflict> scanDirectory(Path dir) {
        findings.clear();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(f -> {
                        String n = f.toString().toLowerCase();
                        return n.endsWith(".java") || n.endsWith(".json")
                                || n.endsWith(".cfg") || n.endsWith(".toml");
                    })
                    .forEach(this::scanFile);
        } catch (IOException e) {
            LOG.warn("扫描失败: {}", e.getMessage());
        }
        return List.copyOf(findings);
    }

    /**
     * 扫描并自动修复硬编码的类名字符串。
     */
    public int scanAndFix(Path dir) {
        int fixed = 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(f -> f.toString().toLowerCase().endsWith(".java"))
                    .toList();

            for (Path file : files) {
                String content = Files.readString(file);
                String before = content;

                // 替换字符串中的类名引用
                for (var e : classRepl.entrySet()) {
                    String oldName = e.getKey();
                    String newName = e.getValue();
                    if (oldName.equals(newName)) continue;

                    // 替换引号中的类名: "net/minecraft/block/Block" -> "new/path/Block"
                    content = content.replace("\"" + oldName + "\"", "\"" + newName + "\"");
                    content = content.replace("'" + oldName + "'", "'" + newName + "'");
                }

                if (!content.equals(before)) {
                    Files.writeString(file, content);
                    fixed++;
                }
            }
        } catch (IOException e) {
            LOG.warn("自动修复失败: {}", e.getMessage());
        }
        return fixed;
    }

    private void scanFile(Path file) {
        try {
            String content = Files.readString(file);
            List<String> lines = List.of(content.split("\n", -1));
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int lineNum = i + 1;
                for (Pattern p : classPatterns) {
                    var m = p.matcher(line);
                    if (m.find()) {
                        String matched = m.group(1) != null ? m.group(1) : m.group();
                        if (matched.length() > 3 && matched.length() < 200) {
                            findings.add(new Conflict(Conflict.Severity.WARNING, Conflict.Category.SYNTAX_ISSUE,
                                    "可能的硬编码引用: \"" + matched + "\"", file, lineNum,
                                    "手动确认是否需要更新为新的映射名"));
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("读取失败: {}", file);
        }
    }

    private List<Pattern> buildPatterns() {
        List<Pattern> patterns = new ArrayList<>();

        for (String oldName : allOldNames()) {
            // 忽略太短的匹配（防误报）
            String simple = oldName.contains("/") ? oldName.substring(oldName.lastIndexOf('/') + 1) : oldName;
            if (simple.length() < 4) continue;

            // 字符串中的类名: "net/minecraft/block/Block"
            String escaped = Pattern.quote(oldName);
            patterns.add(Pattern.compile("\"" + escaped + "\""));

            // 点分格式: "net.minecraft.block.Block"
            String dotted = oldName.replace('/', '.');
            String escDot = Pattern.quote(dotted);
            patterns.add(Pattern.compile("\"" + escDot + "\""));

            // 简单类名 (防止字符串字面量)
            if (simple.length() >= 4) {
                String escSimple = Pattern.quote(simple);
                patterns.add(Pattern.compile("\"" + escSimple + "\""));
            }
        }

        return patterns;
    }

    private Set<String> allOldNames() {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(diff.getClassMappings().keySet());
        // 添加内部 JVM 格式
        for (String n : diff.getClassMappings().keySet()) {
            names.add(n.replace('.', '/'));
        }
        return names;
    }

    public List<Conflict> getFindings() { return findings; }
}
