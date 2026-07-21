package io.github.ieshishinjin.splice.transformer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import io.github.ieshishinjin.splice.model.Conflict;
import io.github.ieshishinjin.splice.model.MappingDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AST-based source transformer using JavaParser.
 * More accurate than regex: handles generics, method chains,
 * nested classes, annotations, and imports correctly.
 *
 * Falls back to regex-based SourceTransformer when parse fails.
 */
public class ASTSourceTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(ASTSourceTransformer.class);

    private final MappingDiff diff;
    private final List<Conflict> conflicts;
    private final Map<String, String> classMap;   // simple name -> new simple name
    private final Map<String, String> methodMap;
    private final Map<String, String> fieldMap;

    public ASTSourceTransformer(MappingDiff diff) {
        this.diff = diff;
        this.conflicts = new CopyOnWriteArrayList<>();
        this.classMap = new HashMap<>();
        for (var e : diff.getClassMappings().entrySet()) {
            String src = e.getKey();
            String simple = src.contains(".") ? src.substring(src.lastIndexOf('.') + 1) : src;
            String tgt = e.getValue();
            String tgtSimple = tgt.contains(".") ? tgt.substring(tgt.lastIndexOf('.') + 1) : tgt;
            classMap.put(simple, tgtSimple);
            classMap.put(src, tgt); // FQN mapping
        }
        this.methodMap = new HashMap<>(diff.getMethodMappings());
        this.fieldMap = new HashMap<>(diff.getFieldMappings());
    }

    public List<Conflict> getConflicts() { return conflicts; }

    /**
     * Transform a Java source file using AST manipulation.
     */
    public TransformationResult transform(String content, Path filePath) {
        if (content == null || content.trim().isEmpty()) {
            return new TransformationResult(content, false, Collections.emptyList());
        }

        // Parse with JavaParser
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(content);
        } catch (Exception e) {
            LOG.warn("AST parse failed for {}: {}. Falling back to regex.", filePath, e.getMessage());
            SourceTransformer fallback = new SourceTransformer(diff);
            var r = fallback.transform(content, filePath);
            return new TransformationResult(r.content(), r.modified(), r.conflicts());
        }

        // Collect parse problems
        if (cu.getAllComments() != null) {
            // Ensure we at least have a valid CU
        }

        boolean[] modified = {false};

        // --- Transform imports ---
        cu.getImports().forEach(imp -> {
            String name = imp.getNameAsString();
            String mapped = remapFQN(name);
            if (!mapped.equals(name)) {
                imp.setName(mapped);
                modified[0] = true;
                conflicts.add(new Conflict(Conflict.Severity.INFO, Conflict.Category.SYNTAX_ISSUE,
                        "Import rename: " + name + " -> " + mapped, filePath,
                        imp.getBegin().map(r -> r.line).orElse(0), null));
            }
        });

        // --- Walk AST to transform types, methods, fields ---
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ClassOrInterfaceDeclaration decl, Void arg) {
                String name = decl.getNameAsString();
                String mapped = classMap.get(name);
                if (mapped != null && !mapped.equals(name)) {
                    decl.setName(mapped);
                    modified[0] = true;
                    conflicts.add(new Conflict(Conflict.Severity.INFO, Conflict.Category.SYNTAX_ISSUE,
                            "Class rename: " + name + " -> " + mapped, filePath,
                            decl.getBegin().map(r -> r.line).orElse(0), null));
                }
                return super.visit(decl, arg);
            }

            @Override
            public Visitable visit(ClassOrInterfaceType type, Void arg) {
                String name = type.getNameAsString();
                String mapped = classMap.get(name);
                if (mapped != null && !mapped.equals(name)) {
                    String simple = mapped.contains(".") ? mapped.substring(mapped.lastIndexOf('.') + 1) : mapped;
                    type.setName(simple);
                    modified[0] = true;
                }
                // Check scope (e.g., OuterClass.InnerClass)
                if (type.getScope().isPresent()) {
                    String scope = type.getScope().get().toString();
                    String scopeMapped = classMap.get(scope);
                    if (scopeMapped != null) {
                        // Scope is handled separately
                    }
                }
                return super.visit(type, arg);
            }

            @Override
            public Visitable visit(MethodCallExpr expr, Void arg) {
                String name = expr.getNameAsString();
                String mapped = methodMap.get(name);
                if (mapped != null && !mapped.equals(name)) {
                    expr.setName(mapped);
                    modified[0] = true;
                    conflicts.add(new Conflict(Conflict.Severity.INFO, Conflict.Category.SYNTAX_ISSUE,
                            "Method rename: " + name + " -> " + mapped, filePath,
                            expr.getBegin().map(r -> r.line).orElse(0), null));
                }
                return super.visit(expr, arg);
            }

            @Override
            public Visitable visit(FieldAccessExpr expr, Void arg) {
                String name = expr.getNameAsString();
                String mapped = fieldMap.get(name);
                if (mapped != null && !mapped.equals(name)) {
                    expr.setName(mapped);
                    modified[0] = true;
                }
                return super.visit(expr, arg);
            }

            @Override
            public Visitable visit(SimpleName nameExpr, Void arg) {
                String name = nameExpr.asString();
                // Only rename if it matches a class name (uppercase start)
                if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
                    String mapped = classMap.get(name);
                    if (mapped != null && !mapped.equals(name)) {
                        String simple = mapped.contains(".") ? mapped.substring(mapped.lastIndexOf('.') + 1) : mapped;
                        nameExpr.setIdentifier(simple);
                        modified[0] = true;
                    }
                }
                return super.visit(nameExpr, arg);
            }

            @Override
            public Visitable visit(MarkerAnnotationExpr expr, Void arg) {
                String name = expr.getNameAsString();
                String mapped = classMap.get(name);
                if (mapped != null && !mapped.equals(name)) {
                    String simple = mapped.contains(".") ? mapped.substring(mapped.lastIndexOf('.') + 1) : mapped;
                    expr.setName(simple);
                    modified[0] = true;
                }
                return super.visit(expr, arg);
            }

            @Override
            public Visitable visit(SingleMemberAnnotationExpr expr, Void arg) {
                String name = expr.getNameAsString();
                String mapped = classMap.get(name);
                if (mapped != null && !mapped.equals(name)) {
                    String simple = mapped.contains(".") ? mapped.substring(mapped.lastIndexOf('.') + 1) : mapped;
                    expr.setName(simple);
                    modified[0] = true;
                }
                return super.visit(expr, arg);
            }

            @Override
            public Visitable visit(NormalAnnotationExpr expr, Void arg) {
                String name = expr.getNameAsString();
                String mapped = classMap.get(name);
                if (mapped != null && !mapped.equals(name)) {
                    String simple = mapped.contains(".") ? mapped.substring(mapped.lastIndexOf('.') + 1) : mapped;
                    expr.setName(simple);
                    modified[0] = true;
                }
                return super.visit(expr, arg);
            }
        }, null);

        if (!modified[0]) {
            return new TransformationResult(content, false, Collections.emptyList());
        }

        return new TransformationResult(cu.toString(), true, Collections.unmodifiableList(conflicts));
    }

    /** Remap a fully qualified class name. */
    private String remapFQN(String fqn) {
        String mapped = classMap.get(fqn);
        if (mapped != null) return mapped;
        int dot = fqn.lastIndexOf('.');
        if (dot > 0) {
            String cls = fqn.substring(dot + 1);
            mapped = classMap.get(cls);
            if (mapped != null) return fqn.substring(0, dot + 1) + mapped;
        }
        return fqn;
    }

    /** Result of transforming a single file. */
    public record TransformationResult(String content, boolean modified, List<Conflict> conflicts) {}
}
