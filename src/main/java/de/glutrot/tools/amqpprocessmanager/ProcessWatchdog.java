package de.glutrot.tools.amqpprocessmanager;

import java.util.logging.Logger;

/**
 * When spawned as a thread (run .start()), ProcessWatchdog monitors the given
 * Process alive status and tries to forcibly terminate it upon timeout. Timeout
 * can be prevented by calling heartbeat(). Watchdog thread will terminate when
 * process ends.
 */
public class ProcessWatchdog extends Thread {
    private static final Logger logger = Logger.getLogger(ProcessWatchdog.class.getName());
    
    private int timeout = 30; // seconds!
    private long checkInterval = 200; // milliseconds!
    
    /**
     * @param p Process to monitor and terminate
     * @param timeout timeout after last heartbeat upon which Process will be terminated (seconds)
     * @param checkInterval interval at which the watchdog should check Process state (milliseconds)
     */
    public ProcessWatchdog(Process p, int timeout, long checkInterval) {
        super();
        
        this.timeout = timeout;
        this.checkInterval = checkInterval;
    }
    
    @Override
    public void run() {
        // TODO: implement me
    }
}
