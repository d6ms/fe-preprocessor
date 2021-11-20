package preprocessor;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import preprocessor.extractor.*;

import java.io.*;
import java.nio.file.Path;

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
            if (projectDir.getParent().endsWith("/test")) {
                var processor = new TestProjectProcessor(projectDir, outPath, parser, cfg);
                processor.process();
            } else {
                var processor = new TrainingProjectProcessor(projectDir, outPath, parser, cfg);
                processor.process();
            }
            System.out.println("complete preprocessing " + projectDir);
        } catch (Exception e) {
            System.err.println("failed to process project: " + projectDir);
            e.printStackTrace(System.err);
        }
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
