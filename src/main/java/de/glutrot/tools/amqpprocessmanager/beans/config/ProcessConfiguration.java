package de.glutrot.tools.amqpprocessmanager.beans.config;

public class ProcessConfiguration {
    public String name = null;
    public int concurrentConsumers = 1;
    public ProcessExecutionConfiguration execution = null;
    public ProcessAMQPConfiguration amqp = null;
}
