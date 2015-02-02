package de.glutrot.tools.amqpprocessmanager;

import de.glutrot.tools.amqpprocessmanager.beans.config.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.glutrot.tools.amqpprocessmanager.camel.ProcessManagerRouteBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;

public class Main {
    private static final String defaultConfigFileName = ".amqpprocessmanager.json";
    private static final String defaultConfigFileCharset = "UTF-8";
    
    private static String configFilePath = "";
    
    public static void main(String[] args) throws Exception {
        parseArguments(args);
        Config config = readConfigOrExit();
        
        CamelContext camelContext = configureCamel(config);
        if (camelContext == null) {
            System.err.println("Exiting because Camel failed to configure...");
            System.exit(1);
        }
        
        System.out.println("Starting Camel...");
        camelContext.start();
        System.out.println("Camel started...");
        
        while (true) {
            Thread.yield();
        }
    }
    
    /**
     * Configures Camel according to configuration.
     * @return configured CamelContext if successful, else null
     */
    private static CamelContext configureCamel(final Config config) {
        try {
            CamelContext camelContext = new DefaultCamelContext();
            camelContext.addRoutes(new ProcessManagerRouteBuilder(config, camelContext));
            return camelContext;
        } catch (Exception ex) {
            System.err.println("Error while trying to configure Camel:");
            ex.printStackTrace();
            return null;
        }
    }
    
    private static void parseArguments(final String[] args) {
        // process arguments, get config file path & show help (and exit) if requested
        String userHome = System.getProperty("user.home");
        String fileSeparator = System.getProperty("file.separator");

        configFilePath = userHome + fileSeparator + defaultConfigFileName;
        
        if (args.length > 0) {
            String arg = args[0];
            if (arg.equals("--help") || arg.equals("-h")) {
                printHelpAndExit();
            }
            
            configFilePath = args[0];
        }
        
        if (args.length > 1) {
            System.err.println("You supplied more than one argument, all but optional first one (config file path) will be ignored.");
        }
    }
    
    private static Config readConfigOrExit() {
        Config config = null;
        
        // try to open config file
        File configFile = new File(configFilePath);
        if (!configFile.exists() || !configFile.canRead()) {
            System.err.println("Config file "+configFilePath+" does not exist or user does not have read permission! (call with config path as first argument to override default)");
            System.exit(1);
        }
        
        // try to get default charset
        Charset charset = null;
        try {
            charset = Charset.forName(defaultConfigFileCharset);
        } catch (Exception ex) {
            System.err.println("Unable to get default config file character set from JVM: "+defaultConfigFileCharset+" ("+ex.getMessage()+")");
            System.exit(1);
        }
        
        // open file for reading
        BufferedReader configFileReader = null;
        try {
            configFileReader = new BufferedReader(new InputStreamReader(new FileInputStream(configFile), charset));
        } catch (Exception ex) {
            System.err.println("Unable to read config from "+configFilePath+" ("+ex.getMessage()+")");
            System.exit(1);
        }
        
        // parse config file
        try {
            GsonBuilder gsonBuilder = new GsonBuilder();
            Gson gson = gsonBuilder.create();
            config = gson.fromJson(configFileReader, Config.class);
        } catch (Exception ex) {
            System.err.println("Error while parsing config file "+configFilePath+":");
            ex.printStackTrace();
            System.exit(1);
        }
        
        return config;
    }

    private static void printHelpAndExit() {
        System.out.println("You can supply the path to the JSON-formatted config file to use for this instance. If that argument is missing, ~/"+defaultConfigFileName+" will be read instead.");
        System.exit(2);
    }
}
