package it.unitn.disi.smatch.test;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.junit.Rule;

import org.junit.rules.TemporaryFolder;

import it.unitn.disi.common.DISIException;
import it.unitn.disi.smatch.CLI;


/**
 * @since 2.0.0
 * @author <a rel="author" href="http://davidleoni.it/">David Leoni</a>
 *
 */
public class CliTest {

    public static final Logger log = Logger.getLogger(CliTest.class);
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /**
     * @since 2.0.0
     */
    @Test
    public void testAllSteps() throws ClassNotFoundException, IOException, DISIException {
                
       File output = new File(folder.getRoot(), UUID.randomUUID().toString());                      
        
        CLI.main(new String[]{
                CLI.CMD_ALL_STEPS,
                "src/main/resources/test-data/cw/c.xml",
                "src/main/resources/test-data/cw/w.xml",
                output.getAbsolutePath()
                });
        log.debug("Output mapping is " + output.getAbsolutePath());
        assertTrue(output.exists());                
    }
    
    /**
     * @since 2.0.0
     */
    @Test
    public void testAllStepsMissingArg() throws ClassNotFoundException, IOException, DISIException {
                
       File output = new File(folder.getRoot(), UUID.randomUUID().toString());
        
        CLI.main(new String[]{
                CLI.CMD_ALL_STEPS,
                "src/main/resources/test-data/cw/c.xml",
                "src/main/resources/test-data/cw/w.xml"
                });
        
        assertFalse(output.exists());                
    }
    
    /**
     * @since 2.0.0
     */
    @Test
    public void testNoArgs() throws ClassNotFoundException, IOException, DISIException{
        
        CLI.main(new String[]{});
    }
    
    /**
     * @since 2.0.0
     */
    @Test
    public void testUnrecognizedCommand() throws ClassNotFoundException, IOException, DISIException{
        
        CLI.main(new String[]{"666"});
        
        CLI.main(new String[]{"666", "999"});
    }
    
    /**
     * @since 2.0.0
     */
    @Test
    public void testVerbose() throws ClassNotFoundException, IOException, DISIException{
                
        CLI.main(new String[]{CLI.VERBOSE_CMD_LINE_KEY});               
    }
    
}
