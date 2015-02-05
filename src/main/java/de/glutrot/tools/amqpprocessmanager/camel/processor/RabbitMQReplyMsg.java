package de.glutrot.tools.amqpprocessmanager.camel.processor;

import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/**
 * Camel Processor to turn a message received by a RabbitMQ endpoint into a
 * reply message for replying via a RabbitMQ endpoint.
 * Use RabbitMQReplyMsg.getInstance() to get a singleton instance, processor
 * does not require any state, so one central instance is sufficient.
 */
public class RabbitMQReplyMsg implements Processor {
    private static Object lockObj = new Object();
    private static RabbitMQReplyMsg instance = null;
    
    /**
     * Returns a single shared instance of this processor.
     * @return shared instance
     */
    public static RabbitMQReplyMsg getInstance() {
        synchronized (lockObj) {
            if (instance == null) {
                instance = new RabbitMQReplyMsg();
            }
        }
        
        return instance;
    }
    
    @Override
    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        Message out = in.copy();
        Map<String, Object> headersIn = in.getHeaders();
        
        // remove headers unsuitable for replies
        out.removeHeader("rabbitmq.REPLY_TO");
        out.removeHeader("rabbitmq.EXPIRATION");
        
        // force default exchange, workaround for bug CAMEL-8270
        out.setHeader("rabbitmq.EXCHANGE_NAME", "");
        
        // default exchange routes messages to queue = routing_key
        out.setHeader("rabbitmq.ROUTING_KEY", headersIn.get("rabbitmq.REPLY_TO"));
        
        exchange.setOut(out);
    }
}
