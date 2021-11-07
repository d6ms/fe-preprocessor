package preprocessor.extractor.visitor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;

import java.util.HashSet;
import java.util.Set;

public class PackageCallCollectorVisitor extends VoidVisitorAdapter<Object> {

    private final Set<String> packageCalls = new HashSet<>();

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
        Expression expr = null;
        if (n instanceof MethodCallExpr) {
            expr = ((MethodCallExpr) n).getScope().orElse(null);
        } else if (n instanceof FieldAccessExpr) {
            expr = ((FieldAccessExpr) n).getScope();
        }
        if (expr == null) {
            return;
        }

        try {
            ResolvedType type = expr.calculateResolvedType();
            if (type.isReferenceType()) {
                type.asReferenceType()
                        .getTypeDeclaration()
                        .map(ResolvedTypeDeclaration::getPackageName)
                        .ifPresent(packageCalls::add);
            }
        } catch (Exception e) {
            // NOP
        }
    }

    public Set<String> getPackageCalls() {
        return packageCalls;
    }
}
