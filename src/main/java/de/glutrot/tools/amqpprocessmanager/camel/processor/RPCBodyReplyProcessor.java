package de.glutrot.tools.amqpprocessmanager.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.json.simple.JSONObject;

/**
 * Wraps the message into a basic JSON encoded RPC container.
 * Message will become a JSON object with keys "state" and "output", where
 * state is either "success" or "error" and output will be the previous body
 * now encoded in JSON.
 */
public class RPCBodyReplyProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        // get input message and body
        Message in = exchange.getIn();
        String body = in.getBody(String.class);
        
        // wrap in JSON object containing state and output (body)
        JSONObject obj = new JSONObject();
        obj.put("output", body);
        if (body == null) {
            // execution failed
            obj.put("state", "error");
        } else {
            // execution succeeded
            obj.put("state", "success");
        }
        
        // replace message for output
        Message out = in.copy();
        out.setHeader("rabbitmq.CONTENT_TYPE", "application/json");
        out.setBody(obj.toJSONString());
        exchange.setOut(out);
    }
}
