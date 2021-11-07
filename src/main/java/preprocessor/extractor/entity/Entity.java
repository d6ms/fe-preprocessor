package preprocessor.extractor.entity;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;

import java.util.Objects;
import java.util.stream.Collectors;

public class Entity {

    private final EntityType entityType;
    private final String packageName;
    private final String javaType;
    private final String name;
    private final String signature;
//    private final Set<MethodOrField> a;  // TODO アクセス先のエンティティ一覧が必要だが、MethodOrField オブジェクトへの参照とするとどっちを先に作る問題が発生してしまう

    private Entity(EntityType entityType, String packageName, String javaType, String name, String signature) {
        this.entityType = entityType;
        this.packageName = packageName;
        this.javaType = javaType;
        this.name = name;
        this.signature = signature;
    }

    public static Entity method(String packageName, MethodDeclaration methodDeclaration) {
        String signature = methodDeclaration.getSignature().asString();
        return new Entity(EntityType.METHOD, packageName, null, methodDeclaration.getNameAsString(), signature);
    }

    public static Entity field(String packageName, VariableDeclarator varDecl) {
        return new Entity(EntityType.FIELD, packageName, varDecl.getType().asString(), varDecl.getNameAsString(), null);
    }

    public boolean isMethod() {
        return entityType == EntityType.METHOD;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getName() {
        return name;
    }

    public String getSignature() {
        return signature;
    }

    private enum EntityType {
        METHOD,
        FIELD
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity entity = (Entity) o;
        return entityType == entity.entityType && packageName.equals(entity.packageName) && Objects.equals(javaType, entity.javaType) && name.equals(entity.name) && Objects.equals(signature, entity.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, packageName, javaType, name, signature);
    }

    @Override
    public String toString() {
        return "Entity{" +
                "entityType=" + entityType +
                ", packageName='" + packageName + '\'' +
                ", javaType='" + javaType + '\'' +
                ", name='" + name + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }
}
