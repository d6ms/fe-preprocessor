package preprocessor.extractor;

import java.util.Collections;
import java.util.Set;

public class FileFeature {

    private final String packageName;
    private final Set<String> imports;
    private final Set<String> callToPackages;

    public FileFeature(String packageName, Set<String> imports, Set<String> callToPackages) {
        this.packageName = packageName;
        this.imports = imports;
        this.callToPackages = callToPackages;
    }

    public static FileFeature empty() {
        return new FileFeature(null, Collections.emptySet(), Collections.emptySet());
    }

    public String getPackageName() {
        return packageName;
    }


    public Set<String> getImports() {
        return imports;
    }

    public Set<String> getCallToPackages() {
        return callToPackages;
    }
}
