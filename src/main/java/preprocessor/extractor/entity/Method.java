package preprocessor.extractor.entity;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Method {
    private final String packageName;
    private final Set<Entity> entities = new HashSet<>();
    private final Entity correspondingEntity;

    public Method(String packageName, MethodDeclaration decl) {
        this.packageName = packageName;
        this.correspondingEntity = Entity.method(packageName, decl);
    }

    public void addEntities(Collection<Entity> entities) {
        this.entities.addAll(entities);
    }

    public String getPackageName() {
        return packageName;
    }

    public Set<Entity> getEntities() {
        return entities;
    }

    public Entity asEntity() {
        return correspondingEntity;
    }

    public double calcDistance(Package package_) {
        var entitySet = entities;
        var packageSet = package_.getEntities();
        if (packageName.equals(package_.getName())) {
            packageSet.remove(correspondingEntity);
        }

        Set<Entity> union = new HashSet<>(entitySet);
        union.addAll(packageSet);

        if (union.isEmpty()) {
            return 1.0;
        } else {
            Set<Entity> intersection = new HashSet<>(packageSet);
            intersection.retainAll(entitySet);
            return 1.0 - (double) intersection.size() / (double) union.size();
        }
    }
}
