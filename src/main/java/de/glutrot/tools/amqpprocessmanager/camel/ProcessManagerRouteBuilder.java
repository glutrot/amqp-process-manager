package de.glutrot.tools.amqpprocessmanager.camel;

import de.glutrot.tools.amqpprocessmanager.beans.config.AMQPCommonSettings;
import de.glutrot.tools.amqpprocessmanager.beans.config.Config;
import de.glutrot.tools.amqpprocessmanager.beans.config.ProcessConfiguration;
import de.glutrot.tools.amqpprocessmanager.camel.processor.RabbitMQReplyMsg;
import de.glutrot.tools.amqpprocessmanager.camel.processor.ExternalTaskProcessor;
import de.glutrot.tools.amqpprocessmanager.camel.processor.RPCBodyReplyProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rabbitmq.RabbitMQEndpoint;

public class ProcessManagerRouteBuilder extends RouteBuilder {
    private Config config = null;
    private CamelContext ctx = null;
    
    public ProcessManagerRouteBuilder(Config config, CamelContext ctx) {
        super(ctx);
        
        this.config = config;
        this.ctx = ctx;
    }
    
    /**
     * Apply common AMQP settings to given endpoint.
     * @param endpoint endpoint to configure
     */
    private void configureCommonSettings(RabbitMQEndpoint endpoint) {
        AMQPCommonSettings common = config.amqpCommonSettings;
        endpoint.setUsername(common.username);
        endpoint.setPassword(common.password);
        endpoint.setVhost(common.vhost);
        endpoint.setAutomaticRecoveryEnabled(common.automaticRecoveryEnabled);
        endpoint.setConnectionTimeout(common.connectionTimeout);
        endpoint.setRequestedHeartbeat(common.requestedHeartbeat);
        
        // configure prefetcher as workaround for CAMEL-8308 to avoid locking
        // all messages on server (which prevents message expiration)
        // see: https://issues.apache.org/jira/browse/CAMEL-8308
        endpoint.setPrefetchEnabled(true);
        endpoint.setPrefetchCount(1);
    }
    
    @Override
    public void configure() throws Exception {
        String baseURL = "rabbitmq://"+config.amqpCommonSettings.hostname+":"+Integer.toString(config.amqpCommonSettings.port)+"/";
        
        // define common endpoint for sending all replies
        // NOTE: default exchange currently cannot be declared in URL (CAMEL-8270)
        RabbitMQEndpoint amqpOut = (RabbitMQEndpoint) ctx.getEndpoint(baseURL+"amq.direct");
        configureCommonSettings(amqpOut);
        amqpOut.setDeclare(false);
        amqpOut.setExchangeType("direct");
        
        // use a common processor to set headers for reply messages
        RabbitMQReplyMsg headerReplyProcessor = RabbitMQReplyMsg.getInstance();
        RPCBodyReplyProcessor rpcBodyReplyProcessor = new RPCBodyReplyProcessor();
        
        // define dedicated endpoints for each processor defined in config
        for (ProcessConfiguration procConfig : config.processes) {
            // configure AMQP channel(s)
            // Maximum number of concurrent process execution is managed by
            // setting the ConcurrentConsumers property so we only accept N
            // messages for processing at a time.
            RabbitMQEndpoint amqpIn = (RabbitMQEndpoint) ctx.getEndpoint(baseURL+procConfig.amqp.exchange);
            configureCommonSettings(amqpIn);
            amqpIn.setExchangeType(procConfig.amqp.exchangeType);
            amqpIn.setConcurrentConsumers(procConfig.concurrentConsumers);
            amqpIn.setDeclare(true);
            amqpIn.setAutoAck(false); // only lock message on AMQP - if we crash, shutdown or loose connection, processing should be retried
            amqpIn.setAutoDelete(false);
            
            if (procConfig.amqp.routingKey != null) {
                amqpIn.setRoutingKey(procConfig.amqp.routingKey);
            }
            
            if (procConfig.amqp.queue != null) {
                amqpIn.setQueue(procConfig.amqp.queue);
            }
            
            // configure processor to run external task
            ExternalTaskProcessor taskProcessor = new ExternalTaskProcessor(procConfig);
            
            // wire it up
            from(amqpIn).process(taskProcessor).process(rpcBodyReplyProcessor).process(headerReplyProcessor).to(amqpOut);
        }
    }
}
