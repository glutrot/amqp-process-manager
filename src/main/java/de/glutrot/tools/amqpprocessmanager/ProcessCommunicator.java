package de.glutrot.tools.amqpprocessmanager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.camel.util.EndpointHelper;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Handles simple IPC and links process and watchdog.
 * Communication is handled by one thread per direction, which requires start()
 * to be called after initialization and Process and ProcessWatchdog have been
 * started. UTF-8 will be used for communication (which <i>should</i> not matter
 * as JSON is being used).<br />
 * <br />
 * While ProcessCommunicator is running, the process must not talk
 * in anything but one-line JSON encoded messages on its stdout pipe (stdout => reader).
 * The only messages supported to be received so far are watchdog heartbeats and result strings:
 * <ul>
 * <li><code>["heartbeat"]</code> forwards the heartbeat to the linked watchdog to extend process lifetime</li>
 * <li><code>["result", "..."]</code> receives the result from second parameter</li>
 * </ul>
 * In turn, the process may receive input from stdin encoded in JSON format, one line per message (writer => stdin):
 * <ul>
 * <li>sendPlainMessage(String) submits <code>["message", "..."]</code> which contains a generic message to be consumed by the process</li>
 * </ul>
 * Process should flush its stdout after each message to ensure they are processed in time.
 */
public class ProcessCommunicator {
    private static final Logger logger = Logger.getLogger(ProcessCommunicator.class.getName());
    private final String logPrefix;
    
    private FromProcessThread fromProcessThread = null;
    private ToProcessThread toProcessThread = null;
    
    private final static Charset charset = Charset.forName("UTF-8");
    
    protected final FutureResult futureResult = new FutureResult();
    
    /**
     * Future containing a Result which will notify observers upon calling
     * setResult(...).
     */
    public class FutureResult implements Future<Result> {
        private final Object syncObj = new Object();
        private Result result = null;
        
        /**
         * Sets result and notifies observers. Result should only be set once.
         * If multiple results are being set, it is not safe to assume which one
         * may be used by calling code.
         * @param result result to store
         */
        protected void setResult(Result result) {
            logger.log(Level.FINE, logPrefix+"setting result on Future");
            
            if (result == null) {
                logger.log(Level.WARNING, logPrefix+"Setting null result - Future will not terminate!");
            }
            
            synchronized (syncObj) {
                if (this.result != null) {
                    logger.log(Level.WARNING, logPrefix+"Result was already set - any but first Result may be used, previous Results may be lost!");
                }
                
                this.result = result;
                
                syncObj.notifyAll();
            }
        }
        
        @Override
        public boolean cancel(boolean bln) {
            // not implemented
            return false;
        }

        @Override
        public boolean isCancelled() {
            // not implemented
            return false;
        }

        @Override
        public boolean isDone() {
            boolean isDone = false;

            synchronized (syncObj) {
                isDone = (result != null);
            }

            return isDone;
        }

        @Override
        public Result get() throws InterruptedException, ExecutionException {
            Result copiedResult = null;
            
            synchronized (syncObj) {
                while (result == null) {
                    syncObj.wait();
                }
                
                copiedResult = result;
            }
            
            return copiedResult;
        }

        @Override
        public Result get(long duration, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            Result copiedResult = null;
            
            synchronized (syncObj) {
                if (result != null) {
                    syncObj.wait(timeUnit.toMillis(duration));
                }
                
                copiedResult = result;
            }
            
            if (copiedResult == null) {
                throw new TimeoutException("wait for Result object timed out");
            }
            
            return copiedResult;
        }
    };
    
    /**
     * Result represents a process' result containing state (got data?)
     * and plain String result output received from the process.
     */
    public static class Result {
        private String output = null;
        private boolean hasFailed = false;

        public Result(String output, boolean hasFailed) {
            this.output = output;
            this.hasFailed = hasFailed;
        }
        
        public String getOutput() {
            return output;
        }
        
        public boolean hasFailed() {
            return hasFailed;
        }
    }
    
    /**
     * FromProcessThread manages communication from process to communicator by
     * reading the process' stdout stream. See documentation of ProcessCommunicator
     * for details on supported messages.
     */
    protected class FromProcessThread extends Thread {
        private final Logger logger = Logger.getLogger(FromProcessThread.class.getName());
        private String logPrefix = null;
        
        private ProcessWatchdog watchdog = null;
        private BufferedReader br = null;
        
        private boolean receivedResult = false;
        
        public FromProcessThread(Process process, ProcessWatchdog watchdog, String name) {
            this.watchdog = watchdog;
            
            logPrefix = "Reader for process "+name+": ";
            
            br = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));
        }
        
        private void handleResult(JSONArray msg) {
            if (msg.size() != 2) {
                logger.log(Level.WARNING, logPrefix+"Process sent result message with wrong number of arguments, ignoring message");
                return;
            }
            
            Object resultObj = msg.get(1);
            String result = (resultObj instanceof String) ? (String) resultObj : null;
            
            if (result == null) {
                logger.log(Level.WARNING, logPrefix+"Process sent invalid result (must be String and not null), ignoring message.");
                return;
            }
            
            futureResult.setResult(new Result(result, false));
            receivedResult = true;
        }
        
        @Override
        public void run() {
            JSONParser parser = new JSONParser();
            
            try {
                // read until stream closes
                while (true) {
                    String line = br.readLine();
                    
                    // terminate upon null line
                    if (line == null) {
                        break;
                    }

                    // try to parse message container
                    JSONArray msg = null;
                    try {
                        Object obj = parser.parse(line);
                        if ((obj != null) && (obj instanceof JSONArray)) {
                            msg = (JSONArray) obj;
                        } else {
                            logger.log(Level.WARNING, logPrefix+"Process sent no JSON array on root level, ignoring message");
                        }
                    } catch (ParseException ex) {
                        logger.log(Level.WARNING, logPrefix+"Process sent something which wasn't valid JSON, ignoring message", ex);
                    }
                    
                    // skip if no message could be read
                    if (msg == null) {
                        continue;
                    }
                    
                    // get message keyword
                    Object keywordObject = (msg.size() > 0) ? msg.get(0) : null;
                    String keyword = (keywordObject instanceof String) ? (String) keywordObject : null;
                    if (keyword == null) {
                        logger.log(Level.WARNING, logPrefix+"Process sent a JSON array without a keyword, ignoring message");
                        continue;
                    }
                    logger.log(Level.FINE, "{0}Received keyword \"{1}\"", new Object[]{logPrefix, keyword});
                    
                    // handle message
                    switch (keyword) {
                        case "heartbeat":   watchdog.heartbeat();
                                            break;
                        
                        case "result":      handleResult(msg);
                                            break;
                        
                        default:            logger.log(Level.WARNING, "Process sent unknown IPC message keyword \""+keyword+"\"");
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.FINE, logPrefix+"Reader caught exception, stopping", ex);
            }
            
            // resolve Future to failed state if we did not receive any result
            // NOTE: this is required to notify observers or they may wait forever
            if (!receivedResult) {
                futureResult.setResult(new Result(null, true));
            }
        }
    }
    
    protected static class ToProcessThread extends Thread {
        private static final Logger logger = Logger.getLogger(ToProcessThread.class.getName());
        
        private String logPrefix = null;
        private BufferedWriter bw = null;
        private boolean streamOpen = true;
        private final LinkedList<String> sendQueue = new LinkedList<>();
        
        public ToProcessThread(Process process, String name) {
            logPrefix = "Writer for process "+name+": ";
            
            bw = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), charset));
        }
        
        @Override
        public void run() {
            String msg = null;
            while (true) {
                // get message from queue
                synchronized (sendQueue) {
                    // stop if stream has been closed
                    if (!streamOpen) {
                        logger.log(Level.FINE, "{0}Stopping due to closed stream", logPrefix);
                        break;
                    }
                    
                    if (sendQueue.isEmpty()) {
                        try {
                            sendQueue.wait();
                        } catch (InterruptedException ex) {
                            logger.log(Level.FINE, logPrefix+"Monitor of sendQueue has been interrupted", ex);
                            continue;
                        }
                        
                        // we may have been woken up because stream has been
                        // closed and we should terminate now; skip processing
                        if (!streamOpen) {
                            continue;
                        }
                    }
                    
                    // get message to send
                    try {
                        msg = sendQueue.pop();
                    } catch (NoSuchElementException ex) {
                        logger.log(Level.WARNING, logPrefix+"sendQueue returned Exception during pop(), retrying...", ex);
                        continue;
                    }
                }
                
                // send message to process
                try {
                    bw.write(msg);
                    bw.newLine();
                    bw.flush();
                    
                    logger.log(Level.FINE, logPrefix+"Message sent to process");
                } catch (IOException ex) {
                    logger.log(Level.FINE, logPrefix+"Exception while sending message to process:", ex);
                    
                    // mark stream closed so no more messages will be queued
                    synchronized (sendQueue) {
                        streamOpen = false;
                    }
                    
                    // continue handling above
                    continue;
                }
            }
            
            logger.log(Level.FINE, "{0}Thread terminating...", logPrefix);
        }
        
        /**
         * Queues the given IPC-encoded message to be sent to the process unless
         * stream has already been closed. Monitors on sendQueue will be notified
         * upon queuing.
         * @param msg encoded message to send to as defined by ProcessCommunicator
         * @return success? (false if stream has been closed)
         */
        public boolean queueMessage(String msg) {
            boolean localStreamOpen;
            
            logger.log(Level.FINE, "{0}Queuing message to be sent to process...", logPrefix);
            
            synchronized (sendQueue) {
                localStreamOpen = streamOpen;
                if (localStreamOpen) {
                    sendQueue.addLast(msg);
                    sendQueue.notifyAll();
                }
            }
            
            return streamOpen;
        }
        
        /**
         * Shuts the thread down (use after process has terminated).
         */
        public void shutdown() {
            logger.log(Level.FINE, logPrefix+"writer shutdown requested");
            
            synchronized (sendQueue) {
                try {
                    bw.close();
                } catch (IOException ex) {
                    logger.log(Level.FINE, logPrefix+"failed to close writer", ex);
                }
                
                streamOpen = false;
                sendQueue.notifyAll();
            }
        }
    }
    
    /**
     * Initializes a new communicator instance to be linked with given process
     * and its watchdog.
     * @param process process to communicate with
     * @param watchdog watchdog monitoring the given process
     * @param name process name used to identify it on logs
     */
    public ProcessCommunicator(Process process, ProcessWatchdog watchdog, String name) {
        super();
        
        logPrefix = "Communicator for process "+name+": ";
        
        fromProcessThread = new FromProcessThread(process, watchdog, name);
        toProcessThread = new ToProcessThread(process, name);
        
        watchdog.addShutdownCallback(() -> {
            toProcessThread.shutdown();
            return null;
        });
    }
    
    /**
     * Starts communication threads.
     */
    public void start() {
        fromProcessThread.start();
        toProcessThread.start();
    }
    
    /**
     * Result will become available during communication or at latest after
     * stream connection has been terminated. As this is an asynchronous
     * operation, you can use this method to get a Future for the Result
     * (non-blocking).
     * @return Future of process Result
     */
    public Future<Result> getFutureResult() {
        return futureResult;
    };
    
    
    public boolean sendPlainMessage(String msg) {
        if (msg == null) {
            logger.log(Level.WARNING, logPrefix+"Tried to send null message; unable to comply by protocol, ignoring message...");
            return false;
        }
        
        JSONArray arr = new JSONArray();
        arr.add("message");
        arr.add(msg);
        
        return toProcessThread.queueMessage(arr.toJSONString());
    }
}
