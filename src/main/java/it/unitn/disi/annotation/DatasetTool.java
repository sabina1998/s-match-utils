package it.unitn.disi.annotation;

import it.unitn.disi.annotation.pipelines.IBaseContextPipeline;
import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.smatch.SMatchException;
import it.unitn.disi.smatch.data.trees.IBaseContext;
import it.unitn.disi.smatch.loaders.context.IBaseContextLoader;
import it.unitn.disi.smatch.renderers.context.IBaseContextRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Processes a dataset by applying to it a series of operations in a pipeline fashion.
 * <p/>
 * Usage: DatasetTool input output -config=... -property=...
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class DatasetTool {

    private static final Logger log = LoggerFactory.getLogger(DatasetTool.class);

    public static final String DEFAULT_CONFIG_FILE_NAME = ".." + File.separator + "conf" + File.separator + "d-tool.xml";

    // usage string
    private static final String USAGE = "Usage: DatasetTool <input> [output] [options]\n" +
            " Options: \n" +
            " -config=file.xml                           read configuration from file.xml\n" +
            "                                            use -Dkey=value to supply values to ${key} placeholders in the config file";

    // config file command line key
    public static final String configFileCmdLineKey = "-config=";

    // component configuration keys and component instance variables
    private static final String CONTEXT_LOADER_KEY = "contextLoader";
    private final IBaseContextLoader contextLoader;

    private static final String CONTEXT_RENDERER_KEY = "contextRenderer";
    private final IBaseContextRenderer contextRenderer;

    private static final String PIPELINE_KEY = "pipeline";
    private IBaseContextPipeline pipeline;

    public DatasetTool(String configFileName) {
        log.info("Loading configuration...");
        ConfigurableApplicationContext applicationContext = new FileSystemXmlApplicationContext(configFileName);

        contextLoader = applicationContext.getBean(CONTEXT_LOADER_KEY, IBaseContextLoader.class);
        contextRenderer = applicationContext.getBean(CONTEXT_RENDERER_KEY, IBaseContextRenderer.class);
        try {
            pipeline = applicationContext.getBean(PIPELINE_KEY, IBaseContextPipeline.class);
        } catch (NoSuchBeanDefinitionException e) {
            pipeline = null;
        }
    }

    public IBaseContext loadContext(String fileName) throws SMatchException {
        if (null == contextLoader) {
            throw new SMatchException("Context loader is not configured.");
        }

        log.info("Loading context from: " + fileName);
        final IBaseContext result = contextLoader.loadContext(fileName);
        log.info("Loading context finished");
        return result;
    }

    @SuppressWarnings("unchecked")
    public void renderContext(IBaseContext context, String fileName) throws SMatchException {
        if (null == contextRenderer) {
            throw new SMatchException("Context renderer is not configured.");
        }
        log.info("Rendering context to: " + fileName);
        contextRenderer.render(context, fileName);
        log.info("Rendering context finished");
    }

    public void process(IBaseContext context) {
        try {
            if (null != pipeline) {
                log.info("Processing context...");
                pipeline.process(context);
            }
        } catch (PipelineComponentException e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
        }
    }

    public void afterProcessing() {
        if (null != pipeline) {
            try {
                pipeline.afterProcessing();
            } catch (PipelineComponentException e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    public void beforeProcessing() {
        if (null != pipeline) {
            try {
                pipeline.beforeProcessing();
            } catch (PipelineComponentException e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, SMatchException {
        // initialize property file
        String configFileName = DEFAULT_CONFIG_FILE_NAME;
        ArrayList<String> cleanArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith(configFileCmdLineKey)) {
                configFileName = arg.substring(configFileCmdLineKey.length());
            } else {
                cleanArgs.add(arg);
            }
        }

        args = cleanArgs.toArray(new String[cleanArgs.size()]);
        cleanArgs.clear();

        // check input parameters
        if (args.length < 1) {
            System.out.println(USAGE);
        } else {
            DatasetTool dt = new DatasetTool(configFileName);

            if (1 == args.length) {
                String[] inputFiles = args[0].split(";");
                dt.beforeProcessing();
                for (String inputFile : inputFiles) {
                    IBaseContext context = dt.loadContext(inputFile);
                    dt.process(context);
                }
                dt.afterProcessing();
            } else if (2 == args.length) {
                String[] inputFiles = args[0].split(";");
                String[] outputFiles = args[1].split(";");

                if (inputFiles.length == outputFiles.length) {
                    dt.beforeProcessing();
                    for (int i = 0; i < inputFiles.length; i++) {
                        IBaseContext context = dt.loadContext(inputFiles[i]);
                        dt.process(context);
                        dt.renderContext(context, outputFiles[i]);
                    }
                    dt.afterProcessing();
                } else {
                    System.out.println("Input and output arguments count mismatch.");
                }
            }
        }
    }
}