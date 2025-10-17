package com.paxos.tools;

public class Logger {
    public static synchronized void log(String message){
        System.out.println(message);
    }
}
