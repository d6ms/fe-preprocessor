package preprocessor.extractor.entity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Package {

    private final String name;
    private final Set<Entity> entities = new HashSet<>();

    public Package(String name) {
        this.name = name;
    }

    public void addEntities(Collection<Entity> entities) {
        this.entities.addAll(entities);
    }

    public String getName() {
        return name;
    }

    public Set<Entity> getEntities() {
        return entities;
    }
}
