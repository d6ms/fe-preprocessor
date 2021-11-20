package preprocessor.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import org.apache.commons.lang3.tuple.Pair;
import preprocessor.extractor.entity.Entity;
import preprocessor.extractor.entity.Method;
import preprocessor.extractor.entity.Package;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class DistanceCalculator {
    private final JavaParser parser;
    private final Set<String> allPackages;
    private final List<Path> targetPaths;

    public DistanceCalculator(JavaParser parser, Set<String> allPackages, List<Path> targetPaths) {
        this.parser = parser;
        this.allPackages = allPackages;
        this.targetPaths = targetPaths;
    }

    public DistanceCalculationResult calcDistances() {
        // 距離計測可能なパッケージとメソッドを集める
        Map<String, Package> packages = allPackages.stream().collect(Collectors.toMap(p -> p, Package::new));
        List<Method> methods = new ArrayList<>();
        for (Path path : targetPaths) {
            CompilationUnit cu = parse(path);
            if (cu == null) {
                continue;
            }
            String packageName = cu.findFirst(PackageDeclaration.class).get().getNameAsString();
            Package package_ = packages.get(packageName);
            package_.addEntities(collectEntities(cu));

            for (MethodDeclaration methodDecl : collectMethods(cu)) {
                if (isBoilerPlate(methodDecl)) {
                    continue;
                }
                Method method = new Method(path, packageName, methodDecl);
                method.addEntities(collectCallingEntities(methodDecl));
                methods.add(method);
            }
        }

        return new DistanceCalculationResult(packages, new HashSet<>(methods));
    }

    private CompilationUnit parse(Path path) {
        // ソースコードを読み込む
        String code;
        try {
            code = new String(Files.readAllBytes(path));
        } catch (IOException e) {
            // ファイルを読めない場合は存在しないものとして扱う
            return null;
        }

        // パースして AST を構築する
        CompilationUnit cu;
        try {
            Optional<CompilationUnit> result = parser.parse(code).getResult();
            if (result.isEmpty()) {
                return null;
            }
            cu = result.get();
        } catch (ParseProblemException e) {
            // パースできない場合は存在しないものとして扱う
            return null;
        }

        return cu;
    }

    private List<Entity> collectEntities(CompilationUnit cu) {
        List<Entity> entities = new ArrayList<>();

        String packageName = cu.findFirst(PackageDeclaration.class).get().getNameAsString();
        List<FieldDeclaration> fields = cu.findAll(FieldDeclaration.class);
        for (FieldDeclaration field : fields) {
            for (VariableDeclarator varDecl : field.getVariables()) {
                Entity entity = Entity.field(packageName, varDecl);
                entities.add(entity);
            }
        }

        List<MethodDeclaration> methods = collectMethods(cu);
        for (MethodDeclaration method : methods) {
            Entity entity = Entity.method(packageName, method);
            entities.add(entity);
        }

        return entities;
    }


    private List<MethodDeclaration> collectMethods(CompilationUnit cu) {
        var methodCollector = new MethodCollectorVisitor();
        methodCollector.visit(cu, null);
        return methodCollector.methods;
    }

    private Set<Entity> collectCallingEntities(MethodDeclaration method) {
        var visitor = new EntityCallCollectorVisitor();
        visitor.visit(method, null);
        return visitor.entityCalls;
    }


    private boolean isBoilerPlate(MethodDeclaration m) {
        // TODO constructor?
        if (m.getNameAsString().equals("toString")
                || m.getNameAsString().equals("hashCode")
                || m.getNameAsString().equals("equals")) {
            return true;
        }
        if (m.getNameAsString().startsWith("get") || m.getNameAsString().startsWith("is")) {
            for (Node node : m.getChildNodes()) {
                if (node instanceof BlockStmt) {
                    BlockStmt block = (BlockStmt) node;
                    if (block.getStatements().size() == 1
                            && block.getStatements().get(0) instanceof ReturnStmt) {
                        Expression expr = ((ReturnStmt) block.getStatements().get(0)).getExpression().orElse(null);
                        if (expr instanceof FieldAccessExpr || expr instanceof NameExpr) {
                            return true;
                        }
                    }
                }
            }
        }
        if (m.getNameAsString().startsWith("set")) {
            for (Node node : m.getChildNodes()) {
                if (node instanceof BlockStmt) {
                    BlockStmt block = (BlockStmt) node;
                    if (block.getStatements().size() == 1
                            && block.getStatements().get(0) instanceof ExpressionStmt
                            && ((ExpressionStmt) block.getStatements().get(0)).getExpression() instanceof AssignExpr) {
                        AssignExpr assign = (AssignExpr) ((ExpressionStmt) block.getStatements().get(0)).getExpression();
                        if (assign.getTarget() instanceof FieldAccessExpr) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static class DistanceCalculationResult {
        private final Map<String, Package> packages;
        private final Set<Method> methods;

        public DistanceCalculationResult(Map<String, Package> packages, Set<Method> methods) {
            this.packages = packages;
            this.methods = methods;
        }

        public Map<String, Package> getPackages() {
            return packages;
        }

        public Set<Method> getMethods() {
            return methods;
        }

    }

    private static class MethodCollectorVisitor extends VoidVisitorAdapter<Object> {
        public final List<MethodDeclaration> methods = new ArrayList<>();

        @Override
        public void visit(MethodDeclaration n, Object arg) {
            methods.add(n);
        }
    }

    private static class EntityCallCollectorVisitor extends VoidVisitorAdapter<Object> {
        public Set<Entity> entityCalls = new HashSet<>();

        @Override
        public void visit(MethodCallExpr n, Object arg) {
            super.visit(n, arg);
            addPackageCallNode(n);
        }

        @Override
        public void visit(FieldAccessExpr n, Object arg) {
            super.visit(n, arg);
            addPackageCallNode(n);
        }

        private void addPackageCallNode(Node n) {
            if (n instanceof MethodCallExpr) {
                try {
                    ResolvedMethodDeclaration m = ((MethodCallExpr) n).resolve();
                    if (m instanceof JavaParserMethodDeclaration) {
                        MethodDeclaration methodDecl = m.toAst().get();
                        Entity entity = Entity.method(m.getPackageName(), methodDecl);
                        entityCalls.add(entity);
                    }
                } catch (Exception e) {
//                    System.out.println(e.getMessage());
                }
            } else if (n instanceof FieldAccessExpr) {
                try {
                    ResolvedValueDeclaration d = ((FieldAccessExpr) n).resolve();
                    if (d instanceof JavaParserFieldDeclaration) {
                        VariableDeclarator varDecl = ((JavaParserFieldDeclaration) d).getVariableDeclarator();
                        String packageName = ((JavaParserFieldDeclaration) d).declaringType().getPackageName();
                        Entity entity = Entity.field(packageName, varDecl);
                        entityCalls.add(entity);
                    }
                } catch (Exception e) {
//                    System.out.println(e.getMessage());
                }
            }
        }
    }

}
