package de.glutrot.tools.amqpprocessmanager.beans.config;

public class AMQPCommonSettings {
    public String hostname = "localhost";
    public int port = 5672;
    public String username = null;
    public String password = null;
    public String vhost = null;
    
    public boolean automaticRecoveryEnabled = true;
    public int connectionTimeout = 30000;
    public int requestedHeartbeat = 10;
}
