package preprocessor.extractor;

import com.github.javaparser.JavaParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class ExtractFeaturesTask implements Callable<Void> {
    private final ExtractorConfig cfg;
    private final Path filePath;
    private final JavaParser parser;

    public ExtractFeaturesTask(ExtractorConfig cfg, Path path, JavaParser parser) {
        this.cfg = cfg;
        this.filePath = path;
        this.parser = parser;
    }

    @Override
    public Void call() {
        processFile();
        return null;
    }

    public FileFeature processFile() {
        FileFeature features;
        try {
            features = extractSingleFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return features;
    }

    private FileFeature extractSingleFile() throws IOException {
        String code;
        code = new String(Files.readAllBytes(filePath));
        var packageCallExtractor = new PackageCallExtractor(parser);
        var p = packageCallExtractor.extractPackageCall(code);
        if (p.isEmpty()) {
            return null;
        }
        return new FileFeature(p.get().packageName, p.get().imports, p.get().packageCallTo);
    }

}
