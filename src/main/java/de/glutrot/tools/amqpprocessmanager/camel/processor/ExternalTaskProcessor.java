package de.glutrot.tools.amqpprocessmanager.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

public class ExternalTaskProcessor implements Processor {
    private String name = null;
    
    public ExternalTaskProcessor(String name) {
        this.name = name;
    }
    
    @Override
    public void process(Exchange exchange) throws Exception {
        // TODO: implement me
        Message out = exchange.getIn().copy();
        out.setBody("pseudo-processed by "+name);
        exchange.setOut(out);
        
        Thread.sleep(2000);
    }
}
