package preprocessor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import preprocessor.extractor.ExtractFeaturesTask;
import preprocessor.extractor.ExtractorConfig;
import preprocessor.extractor.FileFeature;

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
            var candidatePackages = listCandidatePackages(parser);




            System.out.println("complete preprocessing " + projectDir);
        } catch (Exception e) {
            System.err.println("failed to process project: " + projectDir);
            e.printStackTrace(System.err);
        }
    }

    private Map<String, Set<String>> listCandidatePackages(JavaParser parser) throws IOException {
        // プロジェクト内の全パッケージを列挙する
        List<Path> targetPaths = listPaths();
        Set<String> allPackages = new HashSet<>();
        for (Path path : targetPaths) {
            path = projectDir.toPath().relativize(path);
            String packageName = path.getParent().toString().replace("/", ".");
            allPackages.add(packageName);
        }

        // 1ファイルずつ処理していく
        Map<String, Set<String>> imports = new HashMap<>();
        Map<String, Set<String>> packageCalls = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (Path path : targetPaths) {
            FileFeature fileFeature = processFile(path, parser, count);
            if (fileFeature == null) {
                continue;
            }
            imports.computeIfAbsent(fileFeature.getPackageName(), k -> new HashSet<>())
                    .addAll(fileFeature.getImports());
            packageCalls.computeIfAbsent(fileFeature.getPackageName(), k -> new HashSet<>())
                    .addAll(fileFeature.getCallToPackages());
        }

        // 他パッケージへの呼び出しはプロジェクト内もののみに絞る
        Set<Pair<String, String>> interPackageConnection = new HashSet<>();
        for (var en : packageCalls.entrySet()) {
            en.getValue().retainAll(allPackages);
            for (String callee : en.getValue()) {
                interPackageConnection.add(Pair.of(en.getKey(), callee));
            }
        }

        // import 宣言はプロジェクト内のクラスをそのパッケージに変換し、ライブラリはそのままにする
        Map<String, Set<String>> newImports = allPackages.stream()
                .collect(Collectors.toMap(p -> p, p -> new HashSet<>()));
        for (var en : imports.entrySet()) {
            for (String importPackage : en.getValue()) {
                var result = getCorrespondingPackage(importPackage, allPackages);
                // プロジェクト内の package の import があれば候補に追加する
                if (result.getLeft()) {
                    interPackageConnection.add(Pair.of(result.getRight(), en.getKey()));
                }
                newImports.get(en.getKey()).add(result.getRight());
            }
        }

        // まずは自分自身を候補先クラスに加える
        Map<String, Set<String>> candidatePackages = allPackages.stream()
                .collect(Collectors.toMap(p -> p, Sets::newHashSet));

        // import or methodCall or fieldAccess のあるパッケージを相互に候補に加える
        for (Pair<String, String> p : interPackageConnection) {
            candidatePackages.get(p.getLeft()).add(p.getRight());
            candidatePackages.get(p.getRight()).add(p.getLeft());
        }

        // 同一パッケージ・クラスのインポートがあるパッケージを候補に加える
        for (String target : allPackages) {
            for (String candidate : allPackages) {
                var intersection = Sets.intersection(
                        newImports.get(target), newImports.get(candidate));
                if (!intersection.isEmpty()) {
                    candidatePackages.get(target).add(candidate);
                    candidatePackages.get(candidate).add(target);
                }
            }
        }

        return candidatePackages;
    }

    private Pair<Boolean, String> getCorrespondingPackage(String importPackage, Set<String> allPackages) {
        String[] target = importPackage.split("\\.");
        int max_i = 0;
        String max_package = null;
        for (String p : allPackages) {
            String[] projectPackage = p.split("\\.");
            for (int i = 0; i < projectPackage.length; i++) {
                if (target.length <= i || !projectPackage[i].equals(target[i])) {
                    break;
                }
                if (i > max_i) {
                    max_i = i;
                    max_package = p;
                }
            }
        }
        boolean matched = max_i > 0;
        String result = matched ? max_package : importPackage;
        return Pair.of(matched, result);
    }

    private List<Path> listPaths() throws IOException {
        return Files.walk(projectDir.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().contains("Test"))
                .collect(Collectors.toList());
    }

    private FileFeature processFile(Path path, JavaParser parser, Map<String, Integer> count) {
        // ASTを走査してfeatureを抽出
        ExtractFeaturesTask extractFeaturesTask = new ExtractFeaturesTask(cfg, path, parser);
        FileFeature fileFeature;
        try {
            fileFeature = extractFeaturesTask.processFile();
        } catch (Exception | StackOverflowError e) {  // 型の解決時にStackOverflowになることがある
//                            logger.warning("failed to extract file: " + path);
            return null;
        }
        if (fileFeature == null) {
            return null;
        }

        // TODO 不要なデータを除外
//        List<ProgramFeatures> features = fileFeature.getProgramFeatures().stream()
//                .filter(f -> !f.getPackageName().equals("<unk>"))
//                .filter(f -> !f.getPackageName().contains("test")) // 自動テスト関連 ("latest"なども弾いてしまうが許容する)
//                .filter(f -> !f.getClassName().contains("Test"))
//                .filter(f -> !f.getFeatures().isEmpty())
//                .collect(Collectors.toList());

        return fileFeature;
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
