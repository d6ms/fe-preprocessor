package preprocessor.extractor.entity;

import com.github.javaparser.ast.body.MethodDeclaration;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Method {
    private final String packageName;
    private final Set<Entity> entities = new HashSet<>();
    private final Entity correspondingEntity;
    private final Path filePath;
    private final int lineFrom;
    private final int lineTo;
    private final String signature;

    public Method(Path filePath, String packageName, MethodDeclaration decl) {
        this.packageName = packageName;
        this.correspondingEntity = Entity.method(packageName, decl);
        this.filePath = filePath;
        this.lineFrom = decl.getRange().map(r -> r.begin.line).orElse(0);
        this.lineTo = decl.getRange().map(r -> r.end.line).orElse(0);
        this.signature = decl.getSignature().asString();
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

    public Path getFilePath() {
        return filePath;
    }

    public int getLineFrom() {
        return lineFrom;
    }

    public int getLineTo() {
        return lineTo;
    }

    public String getSignature() {
        return signature;
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
