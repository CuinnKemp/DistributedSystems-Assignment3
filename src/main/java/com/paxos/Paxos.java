package com.paxos;

import com.paxos.roles.Acceptor;
import com.paxos.roles.Learner;
import com.paxos.roles.Proposer;
import com.paxos.tools.Logger;
import com.paxos.tools.Message;
import com.paxos.tools.NetworkManager;
import com.paxos.tools.ProfileManager;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Paxos controller - core of the paxos algorithm
 */
public class Paxos {
    private final String memberId;
    private final NetworkManager networkManager;

    // Paxos roles
    private final Acceptor acceptor;
    private final Learner learner;
    private final Proposer proposer;

    // Recovery Handling
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final int RETRY_TIMEOUT = 5000; // 5 second
    private final AtomicBoolean retryActive = new AtomicBoolean(false);

    public Paxos(String memberId, ProfileManager.MemberProfile profile, String configPath) {
        Logger.log("Starting Paxos Member: " + memberId + " with profile: " + profile);
        this.memberId = memberId;
        this.networkManager = new NetworkManager(memberId, profile, configPath, this);
        try {
            networkManager.startServer();
        } catch (IOException e) {
            throw new RuntimeException("Error: Failed to start server " + e);
        }

        int quorumSize = (networkManager.getClusterSize() / 2) + 1;

        // init roles
        this.acceptor = new Acceptor(memberId, networkManager, "./" + memberId + ".save");
        this.learner = new Learner(memberId, networkManager.getClusterSize(), networkManager);
        this.proposer = new Proposer(memberId, networkManager, quorumSize);
    }

    /**
     * Main message dispatcher.
     * Routes each incoming message to the correct Paxos role based on its type.
     */
    public void onMessage(Message msg) {
        switch (msg.getType()) {
            case PREPARE -> {
                retryHandler();
                acceptor.onPrepare(msg);
            }
            case ACCEPT_REQUEST -> acceptor.onAcceptRequest(msg);
            case PROMISE -> proposer.handlePromise(msg);
            case ACCEPTED -> {
                proposer.handleAccepted(msg);
                learner.onAccepted(msg);
            }
            case LEARN -> learner.onDecide(msg.getAcceptedValue());
            case VALUE -> this.initiateProposal(msg.getProposalValue());
            default -> Logger.log("Unknown message type: " + msg.getType() + " - ignoring");
        }
    }

    /**
     * Called to start a new election/proposal round.
     */
    public void initiateProposal(String candidateName) {
        Logger.log("[initiateProposal] Node " + memberId + " initiating proposal for: " + candidateName);
        proposer.prepare(candidateName);
    }

    /**
     *  Starts a thread that ensures that a value is chosen even if a member crashes or a proposal fails
     */
    public void retryHandler() {
        synchronized (retryActive) {
            if (retryActive.get()) return;
            retryActive.set(true);
        }

        scheduler.schedule(() -> {
            synchronized (retryActive) {
                if (!learner.isDecided()) {
                    Logger.log("Timeout Reached: proposing a new value using last accepted proposal message");
                    proposer.prepare(memberId);
                }
                retryActive.set(false);
            }
        }, RETRY_TIMEOUT + ThreadLocalRandom.current().nextInt(1000), TimeUnit.MILLISECONDS);
    }

    /**
     * Ends the paxos node
     */
    public void killPaxosNode(){
        this.networkManager.stopServer();
    }
}
