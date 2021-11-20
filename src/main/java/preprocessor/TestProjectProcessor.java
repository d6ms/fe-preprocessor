package preprocessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import preprocessor.extractor.CandidatePackageCalculator;
import preprocessor.extractor.DistanceCalculator;
import preprocessor.extractor.ExtractorConfig;
import preprocessor.extractor.entity.Method;
import preprocessor.extractor.entity.Package;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class TestProjectProcessor extends AbstractProjectProcessor {

    public TestProjectProcessor(File projectDir, Path outPath, JavaParser parser, ExtractorConfig cfg) {
        super(projectDir, outPath, parser, cfg);
    }

    @Override
    public void process() throws Exception {
        Files.createDirectories(outPath);

        List<Path> targetPaths = listPaths();
        Set<String> allPackages = new HashSet<>();
        for (Path path : targetPaths) {
            path = projectDir.toPath().relativize(path);
            String packageName = path.getParent().toString().replace("/", ".");
            allPackages.add(packageName);
        }

        var candidatePackages = new CandidatePackageCalculator(parser, cfg).listCandidatePackages(targetPaths, allPackages);
        var result = new DistanceCalculator(parser, allPackages, targetPaths).calcDistances();

        Map<String, Move> moves = parseJson().stream()
                .collect(Collectors.toMap(m -> moveKey(m.filePath, m.lineFrom, m.lineTo), m -> m));

        int i = 0;
        for (Method method : result.getMethods()) {
            write(method, moves, candidatePackages, result.getPackages(), outPath.resolve(i + ".txt"));
            i++;
        }

    }

    private void write(Method method, Map<String, Move> moves, Map<String, Set<String>> candidatePackages,
                       Map<String, Package> packages, Path out) throws IOException {
        String packageNameEc;
        List<String> packageNamePtc;
        int correctIndex;
        Optional<Move> move = getMethodMove(method, moves);
        if (move.isPresent()) {
            packageNameEc = move.get().packageMoved;
            packageNamePtc = new ArrayList<>(candidatePackages.getOrDefault(packageNameEc, Collections.emptySet()));
            packageNamePtc.remove(move.get().packageMoved);
            int idx = packageNamePtc.indexOf(move.get().packageOrig);
            if (idx == -1) {
                return;
            }
            correctIndex = idx;
        } else {
            packageNameEc = method.getPackageName();
            packageNamePtc = new ArrayList<>(candidatePackages.getOrDefault(packageNameEc, Collections.emptySet()));
            packageNamePtc.remove(packageNameEc);
            correctIndex = -1;
        }
        if (!candidatePackages.containsKey(packageNameEc) || packageNamePtc.isEmpty()) {
            return;
        }

        try (FileWriter writer = new FileWriter(out.toFile())) {
            writer.write(correctIndex + "\n");
            for (String packageNameTc : packageNamePtc) {
                double distanceEc = method.calcDistance(packages.get(packageNameEc));
                double distanceTc = method.calcDistance(packages.get(packageNameTc));
                String methodName = method.asEntity().getName();
                writer.write(formatNames(methodName, packageNameEc, packageNameTc) + " "
                        + formatDistances(distanceEc, distanceTc, 0) + "\n");
            }
        }
    }


    private List<Move> parseJson() throws IOException {
        String projectName = projectDir.getName();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode moveJson = mapper.readTree(new File("move.json")).get(projectName);
        List<Move> moves = new ArrayList<>();
        for (JsonNode m : moveJson) {
            moves.add(new Move(
                    m.get("signature").asText(),
                    m.get("file_path").asText(),
                    m.get("line_from").asInt(),
                    m.get("line_to").asInt(),
                    m.get("package_orig").asText(),
                    m.get("class_name").asText(),
                    m.get("method_name").asText(),
                    m.get("package_moved").asText()
            ));
        }
        return moves;
    }

    private String moveKey(String filePath, int lineFrom, int lineTo) {
        return filePath + ":" + lineFrom + "-" + lineTo;
    }

    private Optional<Move> getMethodMove(Method method, Map<String, Move> moves) {
        Path filePath = method.getFilePath();
        String relativePath = projectDir.toPath().getParent().getParent().relativize(filePath).toString();
        String key = moveKey("/" + relativePath, method.getLineFrom(), method.getLineTo());
        Move move = moves.get(key);
        if (move == null) {
            return Optional.empty();
        }
        if (method.getSignature().equals(move.signature)
                && method.getPackageName().equals(move.packageOrig)) {
            return Optional.of(move);
        }
        return Optional.empty();
    }

    private static class Move {
        public String signature;
        public String filePath;
        public int lineFrom;
        public int lineTo;
        public String packageOrig;
        public String className;
        public String methodName;
        public String packageMoved;

        public Move(String signature, String filePath, int lineFrom, int lineTo, String packageOrig, String className, String methodName, String packageMoved) {
            this.signature = signature;
            this.filePath = filePath;
            this.lineFrom = lineFrom;
            this.lineTo = lineTo;
            this.packageOrig = packageOrig;
            this.className = className;
            this.methodName = methodName;
            this.packageMoved = packageMoved;
        }
    }
}
