package de.glutrot.tools.amqpprocessmanager.beans.config;

import java.util.LinkedList;
import java.util.List;

public class ProcessExecutionConfiguration {
    public String workDir = null;
    public String executable = null;
    public List<String> args = new LinkedList<>();
    public int watchdogTimeout = 30;
    public int watchdogCheckInterval = 200;
    public boolean allowWritableExecutable = false;
}
