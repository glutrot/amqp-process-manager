package de.glutrot.tools.amqpprocessmanager.beans.config;

import java.util.LinkedList;
import java.util.List;

public class Config {
    public AMQPCommonSettings amqpCommonSettings;
    public List<ProcessConfiguration> processes = new LinkedList<>();
}
