package de.glutrot.tools.amqpprocessmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs all stderr output from a given Process on INFO level.
 */
public class ProcessStdErrLogForwarder extends Thread {
    private static final Logger logger = Logger.getLogger(ProcessStdErrLogForwarder.class.getName());
    private final static Charset charset = Charset.forName("UTF-8");
    
    private final String name;
    private final String logPrefix;
    
    private BufferedReader br = null;

    /**
     * Initializes logging of stderr from given process.
     * @param p process to grab stderr from
     * @param name process name for identification on log output
     */
    public ProcessStdErrLogForwarder(Process p, String name) {
        br = new BufferedReader(new InputStreamReader(p.getErrorStream(), charset));
        this.name = name;
        logPrefix = "Process "+name+" printed to stderr: ";
    }
    
    @Override
    public void run() {
        try {
            while (true) {
                String line = br.readLine();
                
                if (line == null) {
                    break;
                }
                
                logger.log(Level.INFO, "{0}{1}", new Object[]{logPrefix, line});
            }
        } catch (IOException ex) {
            logger.log(Level.FINE, "Process "+name+": failed reading from stderr stream");
        }
        
        logger.log(Level.FINE, "Process "+name+": stderr forwarding stopped");
    }
    
}
