package preprocessor.extractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import preprocessor.extractor.visitor.PackageCallCollectorVisitor;

import java.util.*;

public class PackageCallExtractor {

    private final JavaParser parser;

    public PackageCallExtractor( JavaParser parser) {
        this.parser = parser;
    }

    // TODO ここでは usedImport の文だけを返すようにして、その後のフィルタはプロジェクトごとに行う
    public Optional<PackageCall> extractPackageCall(String code) {
        CompilationUnit cu = parseSourceCode(code);
        if (cu == null) {
            return Optional.empty();
        }

        if (cu.getPackageDeclaration().isEmpty()) {
            return Optional.empty();
        }
        String packageName = cu.getPackageDeclaration().get().getNameAsString();

        // import の利用判定に用いるため、利用されている identifier を列挙する
        Set<String> identifiers = new HashSet<>();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            clazz.findAll(SimpleName.class).forEach(n -> identifiers.add(n.asString()));
        });

        // 利用されている import 文に filter する
        Set<String> usedImports = new HashSet<>();
        for (var importDecl : cu.findAll(ImportDeclaration.class)) {
            String importStr = importDecl.getNameAsString();
            // 標準コレクションライブラリはどこでも利用される可能性があるため除外する
            if (importStr.startsWith("java.util.")) {
                continue;
            }
            if (importDecl.isAsterisk()) {
                // asterisk import は文字列操作で利用判定できないので使われている扱いにする
                usedImports.add(importStr);
            } else {
                String identifier = importDecl.getName().getIdentifier();
                if (identifiers.contains(identifier)) {
                    usedImports.add(importStr);
                }
            }
        }

        var packageCallVisitor = new PackageCallCollectorVisitor();
        packageCallVisitor.visit(cu, null);
        Set<String> packageCalls = packageCallVisitor.getPackageCalls();

        PackageCall result = new PackageCall(packageName, usedImports, packageCalls);
        return Optional.of(result);
    }

    private CompilationUnit parseSourceCode(String code) {
        try {
            Optional<CompilationUnit> result = parser.parse(code).getResult();
            return result.orElse(null);
        } catch (ParseProblemException e) {
            return null;
        }
    }

    public static class PackageCall {
        public String packageName;
        public Set<String> imports;
        public Set<String> packageCallTo;

        private PackageCall(String packageName, Set<String> imports, Set<String> packageCallTo) {
            this.packageName = packageName;
            this.imports = imports;
            this.packageCallTo = packageCallTo;
        }

    }
}
