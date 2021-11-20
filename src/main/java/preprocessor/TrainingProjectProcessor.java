package preprocessor;

import com.github.javaparser.JavaParser;
import preprocessor.extractor.CandidatePackageCalculator;
import preprocessor.extractor.DistanceCalculator;
import preprocessor.extractor.ExtractorConfig;
import preprocessor.extractor.NameNormalizer;
import preprocessor.extractor.entity.Method;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TrainingProjectProcessor extends AbstractProjectProcessor{

    public TrainingProjectProcessor(File projectDir, Path outPath, JavaParser parser, ExtractorConfig cfg) {
        super(projectDir, outPath, parser, cfg);
    }

    @Override
    public void process() throws Exception {
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
                    double distanceEc = method.calcDistance(result.getPackages().get(packageNameEc));
                    double distanceTc = method.calcDistance(result.getPackages().get(packageNameTc));
                    String methodName = method.asEntity().getName();
                    nameWriter.write(formatNames(methodName, packageNameEc, packageNameTc) + "\n");
                    distWriter.write(formatDistances(distanceEc, distanceTc, 0) + "\n");
                    nameWriter.write(formatNames(methodName, packageNameTc, packageNameEc) + "\n");
                    distWriter.write(formatDistances(distanceTc, distanceEc, 1) + "\n");
                }
            }
        }


    }

}
