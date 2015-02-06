package de.glutrot.tools.amqpprocessmanager.beans.config;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ProcessExecutionConfiguration {
    public String workDir = null;
    public String executable = null;
    public List<String> args = new LinkedList<>();
    public Map<String, String> env = new HashMap<>();
    public int watchdogTimeout = 30;
    public int watchdogCheckInterval = 200;
    public boolean allowWritableExecutable = false;
}
