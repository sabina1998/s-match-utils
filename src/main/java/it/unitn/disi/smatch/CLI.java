package it.unitn.disi.smatch;

import it.unitn.disi.common.DISIException;
import it.unitn.disi.common.components.Configurable;
import it.unitn.disi.common.components.ConfigurableException;
import it.unitn.disi.common.utils.MiscUtils;
import it.unitn.disi.smatch.data.mappings.IContextMapping;
import it.unitn.disi.smatch.data.trees.IBaseContext;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import it.unitn.disi.smatch.loaders.context.IContextLoader;
import it.unitn.disi.smatch.oracles.wordnet.InMemoryWordNetBinaryArray;
import it.unitn.disi.smatch.oracles.wordnet.WordNet;
import it.unitn.disi.smatch.renderers.context.IContextRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Command-line interface for S-Match.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class CLI {

    /**
     * Default configuration file name.
     */
    public static final String DEFAULT_CONFIG_FILE_NAME = "classpath://it/unitn/disi/smatch/s-match.properties";

    // config file command line key
    public static final String CONFIG_FILE_CMD_LINE_KEY = "-config=";
    // property command line key
    public static final String PROP_CMD_LINE_KEY = "-property=";


    // usage string
    private static final String USAGE = "Usage: MatchManager <command> <arguments> [options]\n" +
            " Commands: \n" +
            " wntoflat                                   create cached WordNet files for fast matching\n" +
            " convert <input> <output>                   read input file and write it into output file\n" +
            " convert <source> <target> <input> <output> read source, target and input mapping, and write the output mapping\n" +
            " offline <input> <output>                   read input file, preprocess it and write it into output file\n" +
            " online <source> <target> <output>          read source and target files, run matching and write the output file\n" +
            " filter <source> <target> <input> <output>  read source and target files, input mapping, run filtering and write the output mapping\n" +
            "\n" +
            " Options: \n" +
            " -config=file.properties                    read configuration from file.properties instead of default s-match.properties\n" +
            " -property=key=value                        override the configuration key=value from the config file";


    /**
     * Provides command line interface to the match manager.
     *
     * @param args command line arguments
     * @throws IOException           IOException
     * @throws ConfigurableException ConfigurableException
     */
    public static void main(String[] args) throws IOException, DISIException, ClassNotFoundException {
        // initialize property file
        String configFileName = null;
        ArrayList<String> cleanArgs = new ArrayList<String>();
        for (String arg : args) {
            if (arg.startsWith(CONFIG_FILE_CMD_LINE_KEY)) {
                configFileName = arg.substring(CONFIG_FILE_CMD_LINE_KEY.length());
                System.out.println("Using config file: " + configFileName);
            } else {
                cleanArgs.add(arg);
            }
        }

        args = cleanArgs.toArray(new String[cleanArgs.size()]);
        cleanArgs.clear();

        // collect properties specified on the command line
        Properties commandProperties = new Properties();
        for (String arg : args) {
            if (arg.startsWith(PROP_CMD_LINE_KEY)) {
                String[] props = arg.substring(PROP_CMD_LINE_KEY.length()).split("=");
                if (0 < props.length) {
                    String key = props[0];
                    String value = "";
                    if (1 < props.length) {
                        value = props[1];
                    }
                    commandProperties.put(key, value);
                }
            } else {
                cleanArgs.add(arg);
            }
        }

        args = cleanArgs.toArray(new String[cleanArgs.size()]);

        // check input parameters
        if (args.length < 1) {
            System.out.println(USAGE);
        } else {
            MatchManager mm = new MatchManager();

            Properties config = new Properties();
            if (configFileName == null) {
                config.load(MiscUtils.getInputStream(DEFAULT_CONFIG_FILE_NAME));
                System.out.println("Using resource config file: " + DEFAULT_CONFIG_FILE_NAME);
            } else {
                config.load(MiscUtils.getInputStream(configFileName));
            }

            for (String k : commandProperties.stringPropertyNames()) {
                System.out.println("Property override: " + k + "=" + commandProperties.getProperty(k));
            }

            // override from command line
            config.putAll(commandProperties);

            mm.setProperties(config);

            if ("wntoflat".equals(args[0])) {
                CLI.convertWordNetToFlat(config);
            } else if ("convert".equals(args[0])) {
                if (2 < args.length) {
                    if (3 == args.length) {
                        String inputFile = args[1];
                        String outputFile = args[2];
                        IBaseContext ctxSource = mm.loadContext(inputFile);
                        mm.renderContext(ctxSource, outputFile);
                    } else if (5 == args.length) {
                        String sourceFile = args[1];
                        String targetFile = args[2];
                        String inputFile = args[3];
                        String outputFile = args[4];

                        if (mm.getContextLoader() instanceof IContextLoader) {
                            IContext ctxSource = (IContext) mm.loadContext(sourceFile);
                            IContext ctxTarget = (IContext) mm.loadContext(targetFile);
                            IContextMapping<INode> map = mm.loadMapping(ctxSource, ctxTarget, inputFile);
                            mm.renderMapping(map, outputFile);
                        } else {
                            System.out.println("To convert a mapping, use context loaders supporting IContextLoader.");
                        }
                    }
                } else {
                    System.out.println("Not enough arguments for convert command.");
                }
            } else if ("offline".equals(args[0])) {
                if (2 < args.length) {
                    String inputFile = args[1];
                    String outputFile = args[2];
                    if (mm.getContextLoader() instanceof IContextLoader && mm.getContextRenderer() instanceof IContextRenderer) {
                        IContext ctxSource = (IContext) mm.loadContext(inputFile);
                        mm.offline(ctxSource);
                        mm.renderContext(ctxSource, outputFile);
                    } else {
                        System.out.println("To preprocess a mapping, use context loaders and renderers support IContextLoader and IContextRenderer.");
                    }
                } else {
                    System.out.println("Not enough arguments for offline command.");
                }
            } else if ("online".equals(args[0])) {
                if (3 < args.length) {
                    String sourceFile = args[1];
                    String targetFile = args[2];
                    String outputFile = args[3];
                    if (mm.getContextLoader() instanceof IContextLoader) {
                        IContext ctxSource = (IContext) mm.loadContext(sourceFile);
                        IContext ctxTarget = (IContext) mm.loadContext(targetFile);
                        IContextMapping<INode> result = mm.online(ctxSource, ctxTarget);
                        mm.renderMapping(result, outputFile);
                    } else {
                        System.out.println("To match contexts, use context loaders supporting IContextLoader.");
                    }
                } else {
                    System.out.println("Not enough arguments for online command.");
                }
            } else if ("filter".equals(args[0])) {
                if (4 < args.length) {
                    String sourceFile = args[1];
                    String targetFile = args[2];
                    String inputFile = args[3];
                    String outputFile = args[4];

                    if (mm.getContextLoader() instanceof IContextLoader) {
                        IContext ctxSource = (IContext) mm.loadContext(sourceFile);
                        IContext ctxTarget = (IContext) mm.loadContext(targetFile);
                        IContextMapping<INode> mapInput = mm.loadMapping(ctxSource, ctxTarget, inputFile);
                        IContextMapping<INode> mapOutput = mm.filterMapping(mapInput);
                        mm.renderMapping(mapOutput, outputFile);
                    } else {
                        System.out.println("To filter a mapping, use context loaders supporting IContextLoader.");
                    }
                } else {
                    System.out.println("Not enough arguments for mappingFilter command.");
                }
            } else {
                System.out.println("Unrecognized command.");
            }
        }
    }

    /**
     * Converts WordNet dictionary to binary format for fast searching.
     *
     * @param properties configuration
     * @throws SMatchException SMatchException
     */
    private static void convertWordNetToFlat(Properties properties) throws SMatchException {
        InMemoryWordNetBinaryArray.createWordNetCaches(Configurable.GLOBAL_PREFIX + MatchManager.SENSE_MATCHER_KEY, properties);
        WordNet.createWordNetCaches(Configurable.GLOBAL_PREFIX + MatchManager.LINGUISTIC_ORACLE_KEY, properties);
    }

}