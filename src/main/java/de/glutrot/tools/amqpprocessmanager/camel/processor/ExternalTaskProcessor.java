package de.glutrot.tools.amqpprocessmanager.camel.processor;

import de.glutrot.tools.amqpprocessmanager.ProcessWatchdog;
import de.glutrot.tools.amqpprocessmanager.beans.config.ProcessConfiguration;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class ExternalTaskProcessor implements Processor {
    private static final Logger logger = Logger.getLogger(ExternalTaskProcessor.class.getName());
    
    private boolean allowWritableExecutable = false;
    private boolean isConfigured = false;
    private ProcessBuilder pb = null;
        
    private String name = null;
    
    private int watchdogTimeout = 0;
    private int watchdogCheckInterval = 0;
    
    public ExternalTaskProcessor(ProcessConfiguration config) {
        name = config.name;
        
        watchdogTimeout = config.execution.watchdogTimeout;
        watchdogCheckInterval = config.execution.watchdogCheckInterval;
        
        String executable = config.execution.executable;
        List<String> args = config.execution.args;
        allowWritableExecutable = config.execution.allowWritableExecutable;
        
        if (allowWritableExecutable) {
            logger.log(Level.WARNING, "Process "+name+" allows executable to be writable by current user - this is potentially unsafe and should only be used during development!");
        }
        
        List<String> cmdAndArgs = new LinkedList<>();
        cmdAndArgs.add(executable);
        if (args != null) {
            for (String arg : args) {
                cmdAndArgs.add(arg);
            }
        }
        
        isConfigured = true;
        
        if ((executable == null) || executable.trim().isEmpty()) {
            logger.log(Level.SEVERE, "Process {0}: Executable has not been configured!", name);
            isConfigured = false;
        }
        
        pb = new ProcessBuilder(cmdAndArgs);
        pb.directory(new File(config.execution.workDir));
        
        if (!checkSafeExecution()) {
            logger.warning("Process "+name+": One or more pre-conditions have been violated, no execution will happen until you fix these issues!");
        } else {
            logger.info("Process "+name+": Pre-conditions verified.");
        }
    }
    
    /**
     * Checks if execution of given command is considered "safe" by multiple
     * criteria.
     * Note that there is still the possibility of race conditions between
     * check and actual execution but this should raise the bar for any
     * (accidental) faults which can be made.
     * @return safe to execute?
     */
    private boolean checkSafeExecution() {
        boolean isSafe = true;
        
        if (!isConfigured) {
            logger.log(Level.WARNING, "Process {0} has not been configured properly!", name);
            return false;
        }
        
        File workdir = pb.directory();
        try {
            if (workdir == null) {
                logger.log(Level.WARNING, "Process {0}: Working directory has not been configured - this is considered unsafe as it cannot be checked further, please set it to the executable's directory!", workdir);
                isSafe = false;
            } else {
                if (!workdir.exists() || !workdir.isDirectory()) {
                    logger.log(Level.WARNING, "Process {0}: What was selected as a working directory is no directory or does not exist: {1}", new Object[]{name, workdir.getCanonicalPath()});
                    isSafe = false;
                }

                if (!(workdir.canRead() && workdir.canWrite() && workdir.canExecute())) {
                    logger.log(Level.WARNING, "Process {0}: Working directory has to be readable, writeable and executable: {1}", new Object[]{name, workdir.getCanonicalPath()});
                    isSafe = false;
                }
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Process "+name+": Caught exception while checking working directory!", ex);
            isSafe = false;
        }
        
        List<String> command = pb.command();
        if ((command == null) || command.isEmpty()) {
            logger.log(Level.WARNING, "Process "+name+": Command is missing!");
            isSafe = false;
        }
        
        String executable = (command != null) ? command.get(0) : null;
        File executableFile = new File(workdir, executable);
        try {
            if (!executableFile.exists() || !executableFile.isFile()) {
                logger.log(Level.WARNING, "Process "+name+": Executable does not exist or is no file: "+executableFile.getCanonicalPath());
                isSafe = false;
            }

            if (!executableFile.canExecute()) {
                logger.log(Level.WARNING, "Process "+name+": Executable is not set executable: "+executableFile.getCanonicalPath());
                isSafe = false;
            }
            
            if (!allowWritableExecutable && executableFile.canWrite()) {
                logger.log(Level.WARNING, "Process "+name+": Executable must not be writable for current user: "+executableFile.getCanonicalPath());
                isSafe = false;
            }
            
            String parent = executableFile.getParent();
            if (parent == null) {
                logger.log(Level.WARNING, "Process "+name+": Executable does not define any parent directory!");
                isSafe = false;
            }
            
            if (!executable.contains(System.getProperty("file.separator"))) {
                logger.log(Level.WARNING, "Process "+name+": Executable is not restricted to any path (prepend ./ for current workdir): "+executable);
                isSafe = false;
            }
            
            if (!executableFile.getCanonicalPath().startsWith(workdir.getCanonicalPath() + System.getProperty("file.separator"))) {
                logger.log(Level.WARNING, "Process "+name+": Executable ("+executableFile.getCanonicalPath()+") appears to be outside of working directory ("+workdir.getCanonicalPath()+")");
                isSafe = false;
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Process "+name+": Caught exception while checking executable!", ex);
            isSafe = false;
        } 
        
        return isSafe;
    }
    
    @Override
    public void process(Exchange exchange) throws Exception {
        boolean success = true;
        
        // check pre-conditions again in case executable or workdir has changed
        // since program start
        if (!checkSafeExecution()) {
            logger.log(Level.WARNING, "Process "+name+": pre-conditions failed, executable will not be run");
            success = false;
        }
        
        // start process
        String result = null;
        if (success) {
            logger.log(Level.INFO, "Process "+name+": Starting...");
            
            try {
                Process p = pb.start();
                ProcessWatchdog wd = new ProcessWatchdog(p, watchdogTimeout, watchdogCheckInterval, name);
                wd.start();
                
                while (p.isAlive()) {
                    logger.log(Level.INFO, "Process "+name+": still waiting...");
                    p.waitFor(5, TimeUnit.SECONDS);
                }

                logger.log(Level.INFO, "Process "+name+": Finished...");

                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
                result = br.readLine();
                br.close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "Process "+name+": Execution failed with exception:", ex);
                success = false;
            }
        }
        
        // prepare output message
        Message out = exchange.getIn().copy();
        if (success) {
            out.setBody(result);
        } else {
            out.setBody("failed!");
        }
        exchange.setOut(out);
        
        //Thread.sleep(2000);
    }
}
