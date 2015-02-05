package de.glutrot.tools.amqpprocessmanager;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * When spawned as a thread (call .start()), ProcessWatchdog monitors the given
 * Process' alive status and tries to forcibly terminate it upon timeout. Timeout
 * can be prevented by calling heartbeat(). Watchdog thread will terminate when
 * process ends. Additionally, log messages will be written with WARN level after
 * timeout exceeded and FINE level if process terminated on its own or has not
 * been seen alive by the watchdog. Resolution of benchmark times is affected by
 * checkInterval (if set to 200ms, benchmark results won't show anything &lt;200ms).
 */
public class ProcessWatchdog extends Thread {
    private static final Logger logger = Logger.getLogger(ProcessWatchdog.class.getName());
    private final UUID uuid = UUID.randomUUID();
    
    private Process process = null;
    private int timeout = 30; // seconds!
    private long checkInterval = 200; // milliseconds!
    private String name = null;
    
    // for logging repeated warnings about stuck processes
    private long hangThresholdMillis = -1;
    private long hangThresholdLastWarned = -1;
    
    private final Object syncObj = new Object();
    private long startTimeMillis = -1;
    private long latestExpectedTimeMillis = -1;
    private long firstTimeTerminationMillis = -1;
    private String logPrefix = null;
    
    private List<Callable<Void>> shutdownCallbacks = new LinkedList<>();
    
    /**
     * Initializes a watchdog with given properties.
     * @param process Process to monitor and terminate
     * @param timeout timeout after last heartbeat upon which Process will be terminated (seconds)
     * @param checkInterval interval at which the watchdog should check Process state (milliseconds)
     * @param name process name to help identifying the Process
     */
    public ProcessWatchdog(Process process, int timeout, long checkInterval, String name) {
        super();
        
        this.process = process;
        this.timeout = timeout;
        this.checkInterval = checkInterval;
        
        // warn if threads still did not terminate after double timeout
        // (one to kill + one after kill)
        hangThresholdMillis = timeout * 1000;
        
        // remember start if process is already alive
        if (process.isAlive()) {
            startTimeMillis = System.currentTimeMillis();
        }
        
        logPrefix = "Watchdog "+uuid.toString()+" for process "+name+": ";
    }
    
    /**
     * Tells watchdog that the monitored process is still alive (resets
     * timeout countdown).
     */
    public void heartbeat() {
        synchronized (syncObj) {
            latestExpectedTimeMillis = System.currentTimeMillis() + timeout*1000;
        }
        
        logger.log(Level.FINER, "{0}Processed heartbeat call", logPrefix);
    }
    
    /**
     * Returns the unique ID used to identify this watchdog instance.
     * @return watchdog UUID
     */
    public UUID getUUID() {
        return uuid;
    }
    
    @Override
    public void run() {
        logger.log(Level.FINE, logPrefix+"Starting with timeout set to "+Integer.toString(timeout)+" seconds...");
        
        // initialize variables by starting with an implicit heartbeat
        heartbeat();
        
        while (process.isAlive()) {
            // remember process start
            if (startTimeMillis < 0) {
                startTimeMillis = System.currentTimeMillis();
            }
            
            // get a local copy of latest expected time for process to have finished
            long latestExpectedTimeMillis;
            synchronized (syncObj) {
                latestExpectedTimeMillis = this.latestExpectedTimeMillis;
            }
            
            // get a snapshot of current time
            long currentTimeMillis = System.currentTimeMillis();
            
            // try to terminate process if timeout passed
            if (currentTimeMillis > latestExpectedTimeMillis) {
                if (firstTimeTerminationMillis < 0) {
                    // log first time termination
                    logger.log(Level.WARNING, logPrefix+"Process timed out (>="+Integer.toString(timeout)+" seconds since last heartbeat), trying to terminate...");
                    firstTimeTerminationMillis = currentTimeMillis;
                } else if ((currentTimeMillis - firstTimeTerminationMillis > hangThresholdMillis) && (currentTimeMillis - hangThresholdLastWarned >= hangThresholdMillis)) {
                    // log repeatedly if process appears to be stuck indefinitely
                    // QUESTION: send mail?
                    logger.log(Level.WARNING, logPrefix+"Tried to terminate process but it is still hanging after "+Long.toString((currentTimeMillis - firstTimeTerminationMillis) / 1000)+" seconds...");
                    hangThresholdLastWarned = currentTimeMillis;
                }
                
                // try to terminate
                process.destroyForcibly();
            }
            
            // wait before next check
            try {
                Thread.sleep(checkInterval);
            } catch (InterruptedException ex) {
                logger.log(Level.FINE, logPrefix+"Timer got interrupted:", ex);
            }
        }
        
        // log outcome
        long currentTimeMillis = System.currentTimeMillis();
        boolean wasTerminated = (firstTimeTerminationMillis >= 0);
        if (wasTerminated) {
            logger.log(Level.WARNING, logPrefix+"Terminated in <"+Long.toString(currentTimeMillis - firstTimeTerminationMillis)+"ms (exit code "+Integer.toString(process.exitValue())+")");
        } else if (startTimeMillis < 0) {
            logger.log(Level.FINE, logPrefix+"Process wasn't alive when we started. (exit code "+Integer.toString(process.exitValue())+")");
        } else {
            logger.log(Level.FINE, logPrefix+"Process completed without timeout in <"+Long.toString(currentTimeMillis - startTimeMillis)+"ms (exit code "+Integer.toString(process.exitValue())+")");
        }
        
        // notify observers by calling registered shutdown callbacks
        // NOTE: process.isAlive() has to return false by now to avoid new
        //       callbacks getting registered late (accomplished by while loop above)
        List<Callable<Void>> localShutdownCallbacks;
        synchronized (syncObj) {
            localShutdownCallbacks = new LinkedList<>(shutdownCallbacks);
        }
        
        if (localShutdownCallbacks.isEmpty()) {
            logger.log(Level.FINE, "{0}no shutdown callbacks registered", logPrefix);
        } else {
            logger.log(Level.FINE, "{0}Notifying {1} shutdown callbacks", new Object[]{logPrefix, localShutdownCallbacks.size()});
            
            for (Callable<Void> callback : localShutdownCallbacks) {
                try {
                    callback.call();
                } catch (Exception ex) {
                    logger.log(Level.WARNING, logPrefix+"Exception while notifying shutdown callback after process has shut down:", ex);
                }
            }
            
            logger.log(Level.FINE, "{0}Called all shut down callbacks.", logPrefix);
        }
    }
    
    /**
     * Adds a Callable to be called when process is being shut down. If process
     * is already dead when trying to add the callback, callback will be run
     * immediately.
     * @param callback shutdown callback
     */
    public void addShutdownCallback(Callable<Void> callback) {
        boolean alreadyDead = false;
        
        synchronized (syncObj) {
            alreadyDead = !process.isAlive();
            
            if (!alreadyDead) {
                shutdownCallbacks.add(callback);
            }
        }
        
        if (alreadyDead) {
            try {
                callback.call();
            } catch (Exception ex) {
                logger.log(Level.WARNING, logPrefix+"Exception while immediately notifying shutdown callback that the process is already dead:", ex);
            }
        }
    }
}
