package preprocessor;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This class handles the programs arguments.
 */
public class CommandLineValues {
    @Option(name = "--max_path_length", required = true)
    public int maxPathLength;

    @Option(name = "--max_path_width", required = true)
    public int maxPathWidth;

    @Option(name = "--min_code_len", required = false)
    public int minCodeLength = 1;

    @Option(name = "--max_code_len", required = false)
    public int maxCodeLength = -1;

    @Option(name = "--max_child_id", required = false)
    public int maxChildId = 3;

    @Option(name = "--dataset", required = false, forbids = {"--project"})
    public Path dataset;

    @Option(name = "--project", required = false, forbids = {"--dataset"})
    public Path project;

    @Option(name = "--output_dir", required = false)
    public Path outputDir = new File("./output").toPath();

    @Option(name = "--log_dir", required = false)
    public Path logDir = Paths.get("./logs");

    @Option(name = "--num_workers", required = false)
    public int numWorkers = 1;

    @Option(name = "--exclude_boilerplates", required = false)
    public boolean excludeBoilerplates = false;

    public CommandLineValues(String... args) throws CmdLineException {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            throw e;
        }
    }

    public CommandLineValues() {

    }
}