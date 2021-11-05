package preprocessor;

import org.kohsuke.args4j.CmdLineException;
import preprocessor.extractor.ExtractorConfig;

public class App {

    public static void main(String[] args) {
        CommandLineValues s_CommandLineValues;
        ExtractorConfig cfg;
        try {
            s_CommandLineValues = new CommandLineValues(args);
            cfg = ExtractorConfig.fromCommandLineArgs(s_CommandLineValues);
        } catch (CmdLineException e) {
            e.printStackTrace();
            return;
        }

        if (s_CommandLineValues.project != null) {
            ProjectPreprocessTask task = new ProjectPreprocessTask(s_CommandLineValues.project.toFile(), s_CommandLineValues.outputDir, cfg);
            task.run();
        } else if (s_CommandLineValues.dataset != null) {
            DatasetPreprocessor preprocessor = new DatasetPreprocessor(
                    s_CommandLineValues.dataset,
                    s_CommandLineValues.outputDir,
                    s_CommandLineValues.numWorkers,
                    cfg,
                    s_CommandLineValues.logDir
            );
            preprocessor.preprocess();
        }
    }

}
