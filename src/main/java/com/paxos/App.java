package com.paxos;

import com.paxos.tools.Logger;
import com.paxos.tools.ProfileManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class App {
    public static void main(String[] args) {
        if (args.length < 2) {
            Logger.log("Usage: java -jar paxos.jar <memberId> [--profile <profile>] [--configPath <path2config>]");
            Logger.log("profile options: 'RELIABLE' 'LATENT' 'FAILING' 'STANDARD'");
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

        // init the paxos controller
        Paxos paxosManager = new Paxos(memberId, profile, configPath);


        // Step 4: Console loop to propose values
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("[main] Node ready. Type a value to propose:");

        while (true) {
            try {
                String line = console.readLine();
                if (line == null){
                    // keep member alive (allows for running in background without stdin)
                    Thread.sleep(1000);
                    continue;
                }

                line = line.trim();
                if (line.equalsIgnoreCase("exit")) {
                    break;
                }
                if (!line.isEmpty()) {
                    // start proposal with the value
                    paxosManager.initiateProposal(line);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read line " + e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("Shutting down...");
        paxosManager.killPaxosNode();
    }
}
