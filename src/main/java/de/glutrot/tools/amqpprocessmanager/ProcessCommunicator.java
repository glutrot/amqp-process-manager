package de.glutrot.tools.amqpprocessmanager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
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
 * <li>sendMessage(String) submits <code>["message", "..."]</code> which contains a generic message to be consumed by the process</li>
 * </ul>
 * Process should flush its stdout after each message to ensure they are processed in time.
 */
public class ProcessCommunicator {
    private FromProcessThread fromProcessThread = null;
    private ToProcessThread toProcessThread = null;
    
    private final static Charset charset = Charset.forName("UTF-8");
        
    protected static class FromProcessThread extends Thread {
        private static final Logger logger = Logger.getLogger(FromProcessThread.class.getName());
        private String logPrefix = null;
        
        private ProcessWatchdog watchdog = null;
        private BufferedReader br = null;
        
        public FromProcessThread(Process process, ProcessWatchdog watchdog, String name) {
            this.watchdog = watchdog;
            
            logPrefix = "Reader for process "+name+": ";
            
            br = new BufferedReader(new InputStreamReader(process.getInputStream(), charset));
        }
        
        @Override
        public void run() {
            JSONParser parser = new JSONParser();
            
            try {
                // read until stream closes
                while (true) {
                    String line = br.readLine();

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
                        
                        // TODO: implement result/message handlers
                        
                        default:            logger.log(Level.WARNING, "Process sent unknown IPC message keyword \""+keyword+"\"");
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.FINE, logPrefix+"Reader caught exception, stopping", ex);
            }
        }
    }
    
    protected static class ToProcessThread extends Thread {
        private static final Logger logger = Logger.getLogger(ToProcessThread.class.getName());
        
        private String logPrefix = null;
        private BufferedWriter bw = null;
        
        public ToProcessThread(Process process, String name) {
            logPrefix = "Writer for process "+name+": ";
            
            bw = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), charset));
        }
        
        @Override
        public void run() {
            // TODO: implement message writer
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
        
        fromProcessThread = new FromProcessThread(process, watchdog, name);
        toProcessThread = new ToProcessThread(process, name);
    }
    
    /**
     * Starts communication threads.
     */
    public void start() {
        fromProcessThread.start();
        toProcessThread.start();
    }
    
}
