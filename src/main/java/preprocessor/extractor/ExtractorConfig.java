package preprocessor.extractor;

import preprocessor.CommandLineValues;


public class ExtractorConfig {
    public int maxPathLength;
    public int maxPathWidth;
    public int minCodeLength = 1;
    public int maxCodeLength = -1;
    public int maxChildId = 3;
    public boolean excludeBoilerplates;

    public int methodNameLength = 5;
    public int packageNameLength = 5;

    public static ExtractorConfig fromCommandLineArgs(CommandLineValues args) {
        ExtractorConfig cfg = new ExtractorConfig();
        cfg.maxPathLength = args.maxPathLength;
        cfg.maxPathWidth = args.maxPathWidth;
        cfg.minCodeLength = args.minCodeLength;
        cfg.maxCodeLength = args.maxCodeLength;
        cfg.maxChildId = args.maxChildId;
        cfg.excludeBoilerplates = args.excludeBoilerplates;
        return cfg;
    }
}