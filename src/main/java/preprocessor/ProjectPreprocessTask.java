package preprocessor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.apache.commons.lang3.tuple.Pair;
import preprocessor.extractor.*;
import preprocessor.extractor.entity.Method;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectPreprocessTask implements Runnable {

    private final File projectDir;
    private final Path outPath;
    private final ExtractorConfig cfg;

    public ProjectPreprocessTask(File projectDir, Path outPath, ExtractorConfig cfg) {
        this.projectDir = projectDir;
        this.outPath = outPath;
        this.cfg = cfg;
    }

    @Override
    public void run() {
        JavaParser parser = createParser(projectDir.toPath());
        try {
            List<Path> targetPaths = listPaths();
            Set<String> allPackages = new HashSet<>();
            for (Path path : targetPaths) {
                path = projectDir.toPath().relativize(path);
                String packageName = path.getParent().toString().replace("/", ".");
                allPackages.add(packageName);
            }

            var candidatePackages = new CandidatePackageCalculator(parser, cfg).listCandidatePackages(targetPaths, allPackages);
            var result = new DistanceCalculator(parser, allPackages, targetPaths).calcDistances();

            File namePath = outPath.resolve("train_Names.txt").toFile();
            File distPath = outPath.resolve("train_Distances.txt").toFile();
            try (
                    FileWriter nameWriter = new FileWriter(namePath);
                    FileWriter distWriter = new FileWriter(distPath);
            ) {
                for (Method method : result.getMethods()) {
                    String packageNameEc = method.getPackageName();
                    if (!candidatePackages.containsKey(packageNameEc)) {
                        continue;
                    }
                    Set<String> packageNamePtc = new HashSet<>(candidatePackages.get(packageNameEc));
                    packageNamePtc.remove(packageNameEc);
                    if (packageNamePtc.isEmpty()) {
                        continue;
                    }

                    for (String packageNameTc : packageNamePtc) {
                        Double distanceEc = result.getDistances().get(Pair.of(method, result.getPackages().get(packageNameEc)));
                        distanceEc = distanceEc == null ? 1.0 : distanceEc;
                        Double distanceTc = result.getDistances().get(Pair.of(method, result.getPackages().get(packageNameTc)));
                        distanceTc = distanceTc == null ? 1.0 : distanceTc;
                        String methodName = method.asEntity().getName();
                        nameWriter.write(formatNames(methodName, packageNameEc, packageNameTc) + "\n");
                        distWriter.write(formatDistances(distanceEc, distanceTc, 0) + "\n");
                        nameWriter.write(formatNames(methodName, packageNameTc, packageNameEc) + "\n");
                        distWriter.write(formatDistances(distanceTc, distanceEc, 1) + "\n");
                    }
                }
            }

            System.out.println("complete preprocessing " + projectDir);
        } catch (Exception e) {
            System.err.println("failed to process project: " + projectDir);
            e.printStackTrace(System.err);
        }
    }

    private String formatNames(String methodName, String packageNameX, String packageNameY) {
        // メソッド名を分割し、指定のサイズに padding する
        List<String> nameParts = NameNormalizer.subtokenizeMethodName(methodName);
        StringJoiner joiner = new StringJoiner(" ");
        if (nameParts.size() < cfg.methodNameLength) {
            for (int i = 0; i< (cfg.methodNameLength - nameParts.size()); i++) {
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

    private String formatDistances(double distanceX, double distanceY, int label) {
        return distanceX + " " + distanceY + " " + label;
    }

    private List<Path> listPaths() throws IOException {
        return Files.walk(projectDir.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().contains("Test"))
                .filter(path -> !getPackageName(path).contains("test")) // 自動テスト関連 ("latest"なども弾いてしまうが許容する)
                .collect(Collectors.toList());
    }

    private String getPackageName(Path path) {
        return projectDir.toPath().relativize(path).getParent().toString()
                .replace("/", ".");
    }

    private JavaParser createParser(Path projectDir) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new JavaParserTypeSolver(projectDir));
        typeSolver.add(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(symbolSolver);
        return parser;
    }
}
