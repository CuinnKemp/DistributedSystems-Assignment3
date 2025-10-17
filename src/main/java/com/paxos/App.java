package com.paxos;

import com.paxos.tools.Logger;
import com.paxos.tools.ProfileManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class App {
    public static void main(String[] args) {
        if (args.length < 2) {
            Logger.log("Usage: java -jar paxos.jar <memberId> [--profile <profile>]");
            return;
        }

        // extract values from arguments
        String memberId = args[0];
        ProfileManager.MemberProfile profile = ProfileManager.MemberProfile.STANDARD;
        String configPath = "./cluster.conf";

        for (int i = 1; i < args.length; i++){
            if (args[i].equalsIgnoreCase("--profile") && i+1 != args.length){
                profile = ProfileManager.MemberProfile.valueOf(args[i+1]);
                i++;
            } else if (args[i].equalsIgnoreCase("--configPath") && i+1 != args.length){
                configPath = args[i+1];
                i++;
            }
        }

        Paxos paxosManager = new Paxos(memberId, profile, configPath);


        // Step 4: Console loop to propose values
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Node ready. Type a value to propose (or 'exit'):");

        while (true) {
            try {
                String line = console.readLine();
                if (line == null){
                    break;
                }

                line = line.trim();
                if (line.equalsIgnoreCase("exit")) {
                    break;
                }
                if (!line.isEmpty()) {
                    paxosManager.initiateProposal(line);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read line " + e);
            }
        }

        System.out.println("Shutting down...");
        paxosManager.killPaxosNode();
    }
}
