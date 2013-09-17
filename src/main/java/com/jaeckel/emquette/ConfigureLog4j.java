package com.jaeckel.emquette;


import android.os.Environment;

import org.apache.log4j.Level;

import java.io.File;

import de.mindpipe.android.logging.log4j.LogConfigurator;

public class ConfigureLog4j {

    public static void configure() {

        System.out.println("ConfigureLog4j...");
        final LogConfigurator logConfigurator = new LogConfigurator();

        logConfigurator.setFileName(Environment.getExternalStorageDirectory() + File.separator + "emquette.log");
        logConfigurator.setRootLevel(Level.DEBUG);
        // Set log level of a specific logger
        logConfigurator.setLevel("org.apache", Level.ERROR);
//        logConfigurator.setLevel("com.jaeckel.emquette", Level.DEBUG);


        // see http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html for documentation
        logConfigurator.setLogCatPattern("[%t] %C{1}.%M(%F:%L) | %m%n");
//        logConfigurator.setLogCatPattern("[%t] %l | %m%n");
//        logConfigurator.setFilePattern("%-5p [%t]: %m%n");
        //TTCC layout
//        logConfigurator.setFilePattern("%-6r [%15.15t] %-5p %30.30c %x - %m%n");
        logConfigurator.setFilePattern("%d{ISO8601} %-5p [%t] %l | %m%n");

        logConfigurator.configure();

        System.out.println("ConfigureLog4j...Done");

    }

}
