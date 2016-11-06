package it.unitn.disi.smatch.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import it.unitn.disi.common.DISIException;
import it.unitn.disi.smatch.CLI;

/**
 * @since 2.0.0
 *
 */
public class DiversiconTest {

    public static final Logger log = Logger.getLogger(DiversiconTest.class);
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    
    @Test
    public void testSmatchSynchronousDiversiconXml() {
        runConfig("s-match-synchronous-diversicon.xml");
        
    }
    
    @Test
    public void testSpsmSmatchAsynchronousDiversiconXml() {                
        runConfig("s-match-spsm-asymmetric-diversicon.xml");
        
    }

    /**
     * Runs xml config found in src/main/resources/conf/ + xmlName
     * 
     * @param xmlName something like "s-match-diversicon.xml"
     * 
     * @since 2.0.0
     */
    private void runConfig(String xmlName) {
        File output = new File(folder.getRoot(), UUID.randomUUID().toString());                      
        
        
        try {
            CLI.main(new String[]{
                    CLI.CMD_ALL_STEPS,
                    CLI.CONFIG_FILE_CMD_LINE_KEY + "src/main/resources/conf/" + xmlName,
                    "src/main/resources/test-data/cw/c.xml",
                    "src/main/resources/test-data/cw/w.xml",
                    output.getAbsolutePath()
                    });            
        } catch (ClassNotFoundException | IOException | DISIException e) {            
            throw new RuntimeException(e);
        }
        
        log.info("Output mapping location: " + output.getAbsolutePath());
        assertTrue(output.exists());
        
        try {
            String outputContent = FileUtils.readFileToString(output);
            log.debug("Output mapping content:\n" + outputContent);
        } catch (IOException e) {        
            throw new RuntimeException(e);
        }
                
    }
}
