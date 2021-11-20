package preprocessor;

import com.github.javaparser.JavaParser;
import preprocessor.extractor.ExtractorConfig;
import preprocessor.extractor.NameNormalizer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public abstract class AbstractProjectProcessor {
    protected final File projectDir;
    protected final Path outPath;
    protected final JavaParser parser;
    protected final ExtractorConfig cfg;

    public AbstractProjectProcessor(File projectDir, Path outPath, JavaParser parser, ExtractorConfig cfg) {
        this.projectDir = projectDir;
        this.outPath = outPath;
        this.parser = parser;
        this.cfg = cfg;
    }

    abstract void process() throws Exception;

    protected String formatNames(String methodName, String packageNameX, String packageNameY) {
        // メソッド名を分割し、指定のサイズに padding する
        List<String> nameParts = NameNormalizer.subtokenizeMethodName(methodName);
        StringJoiner joiner = new StringJoiner(" ");
        if (nameParts.size() < cfg.methodNameLength) {
            for (int i = 0; i < (cfg.methodNameLength - nameParts.size()); i++) {
                joiner.add("*");
            }
        } else {
            nameParts = nameParts.subList(0, cfg.methodNameLength);
        }
        for (String namePart : nameParts) {
            joiner.add(namePart);
        }

        // パッケージ名を分割
        for (String packageName : Arrays.asList(packageNameX, packageNameY)) {
            nameParts = Arrays.asList(packageName.split("\\."));
            if (nameParts.size() > cfg.packageNameLength) {
                nameParts = nameParts.subList(nameParts.size() - cfg.packageNameLength, nameParts.size());
            }
            if (nameParts.size() < cfg.packageNameLength) {
                for (int i = 0; i < (cfg.packageNameLength - nameParts.size()); i++) {
                    joiner.add("*");
                }
            }
            for (String namePart : nameParts) {
                joiner.add(namePart);
            }
        }
        return joiner.toString();
    }

    protected String formatDistances(double distanceX, double distanceY, int label) {
        return distanceX + " " + distanceY + " " + label;
    }

    protected List<Path> listPaths() throws IOException {
        return Files.walk(projectDir.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().contains("Test"))
                .filter(path -> !getPackageName(path).contains("test")) // 自動テスト関連 ("latest"なども弾いてしまうが許容する)
                .collect(Collectors.toList());
    }

    protected String getPackageName(Path path) {
        return projectDir.toPath().relativize(path).getParent().toString()
                .replace("/", ".");
    }
}
