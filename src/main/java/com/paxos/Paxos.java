package com.paxos;

import com.paxos.roles.Acceptor;
import com.paxos.roles.Learner;
import com.paxos.roles.Proposer;
import com.paxos.tools.Logger;
import com.paxos.tools.Message;
import com.paxos.tools.NetworkManager;
import com.paxos.tools.ProfileManager;

import java.io.IOException;

public class Paxos {

    private final String memberId;
    private int quorumSize;
    private final NetworkManager networkManager;

    private final Acceptor acceptor;
    private final Learner learner;
    private final Proposer proposer;

    public Paxos(String memberId, ProfileManager.MemberProfile profile, String configPath) {
        // our values
        this.memberId = memberId;
        this.networkManager = new NetworkManager(memberId, profile, configPath, this);
        try {
            networkManager.startServer();
        } catch (IOException e) {
            throw new RuntimeException("Error: Failed to start server " + e);
        }

        this.quorumSize = (networkManager.getClusterSize()/2) + 1;

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
        if (msg.getSender() == null){
            Logger.log("Bad message was received - ignoring");
            return;
        }

        switch (msg.getType()) {
            case PREPARE -> acceptor.onPrepare(msg);
            case ACCEPT_REQUEST -> acceptor.onAcceptRequest(msg);
            case PROMISE -> proposer.handlePromise(msg);
            case ACCEPTED -> {
                proposer.handleAccepted(msg);
                learner.onAccepted(msg);
            }
            case NACK -> proposer.handleNack(msg);
            case LEARN -> learner.onDecide(msg.getProposalValue());
            default -> Logger.log("Unknown message type: " + msg.getType() + " - ignoring");
        }
    }

    /**
     * Called to start a new election/proposal round.
     */
    public void initiateProposal(String candidateName) {
        Logger.log("Node " + memberId + " initiating proposal for: " + candidateName);
        proposer.prepare(candidateName);
    }

    /**
     * Ends the paxos node
     */
    public void killPaxosNode(){
        this.networkManager.stopServer();
    }
}
