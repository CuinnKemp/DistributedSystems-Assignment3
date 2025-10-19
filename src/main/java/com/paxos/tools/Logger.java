package com.paxos.tools;

/**
 * Logger class to allow / ensure for synchronised output
 */
public class Logger {
    /**
     * Output a message to stdout
     *
     * @param message the message to be output
     */
    public static synchronized void log(String message){
        System.out.println(message);
    }
}
