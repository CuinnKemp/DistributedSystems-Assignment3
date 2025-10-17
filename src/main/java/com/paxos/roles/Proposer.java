package com.paxos.roles;

import com.paxos.tools.Logger;
import com.paxos.tools.Message;
import com.paxos.tools.NetworkManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Proposer {
    private final String memberId;
    private final NetworkManager networkManager;
    private final int quorumSize;

    private int myCounter = 0;
    private String proposalValue = null;

    private Integer highestAcceptedNumberSeen = null;
    private String highestAcceptedValueSeen = null;

    private final Map<String, Message> pendingPromises = new HashMap<>();
    private final Map<String, Message> pendingAccepts = new HashMap<>();

    public Proposer(String memberId, NetworkManager networkManager, int quorumSize) {
        this.memberId = memberId;
        this.networkManager = networkManager;
        this.quorumSize = quorumSize;
    }

    public void prepare(String value) {
        myCounter++;
        if (value != null) {
            this.proposalValue = value;
        }

        Logger.log("Proposer " + memberId + " starting PREPARE phase with proposalNumber=" + myCounter);

        Message prepare = new Message(
                Message.MessageType.PREPARE,
                memberId,
                String.valueOf(myCounter),
                null,
                null,
                null,
                Instant.now()
        );

        pendingPromises.clear();
        pendingAccepts.clear();
        networkManager.broadcast(prepare);
    }


    public void handlePromise(Message promise) {
        String sender = promise.getSender();
        Logger.log("Received PROMISE from " + sender + " for proposalNumber=" + promise.getProposalNumber());

        // Ignore stale promises
        if (!String.valueOf(myCounter).equals(promise.getProposalNumber())) {
            Logger.log("Ignoring stale PROMISE");
            return;
        }

        pendingPromises.put(sender, promise);

        if (promise.getAcceptedNumber() != null) {
            try {
                int acceptedNum = Integer.parseInt(promise.getAcceptedNumber());
                if (highestAcceptedNumberSeen == null || acceptedNum > highestAcceptedNumberSeen) {
                    highestAcceptedNumberSeen = acceptedNum;
                    highestAcceptedValueSeen = promise.getAcceptedValue();
                    // Adopt the highest accepted value if exists
                    if (highestAcceptedValueSeen != null) {
                        proposalValue = highestAcceptedValueSeen;
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        if (pendingPromises.size() >= quorumSize) {
            whenQuorumPromises();
        }
    }

    public void handleNack(Message nack) {
        Logger.log("Received NACK from " + nack.getSender() + " for proposalNumber=" + nack.getProposalNumber());

        if (String.valueOf(myCounter).equals(nack.getProposalNumber())) {
            abort();
            Logger.log("Retrying prepare with higher proposal number...");
            prepare(proposalValue);
        }
    }

    public void whenQuorumPromises() {
        Logger.log("Quorum of PROMISES reached (" + pendingPromises.size() + "/" + quorumSize +
                ") — sending ACCEPT_REQUEST with value=" + proposalValue);

        if (proposalValue == null) {
            Logger.log("WARN: proposalValue is null — falling back to previously proposed value");
            proposalValue = highestAcceptedValueSeen != null ? highestAcceptedValueSeen : "DEFAULT_VALUE";
        }

        Message acceptReq = new Message(
                Message.MessageType.ACCEPT_REQUEST,
                memberId,
                String.valueOf(myCounter),
                proposalValue,
                null,
                null,
                Instant.now()
        );

        networkManager.broadcast(acceptReq);
    }


    public void handleAccepted(Message accepted) {
        Logger.log("Received ACCEPTED from " + accepted.getSender() +
                " for proposalNumber=" + accepted.getProposalNumber());

        if (!String.valueOf(myCounter).equals(accepted.getProposalNumber())) return;

        pendingAccepts.put(accepted.getSender(), accepted);

        if (pendingAccepts.size() >= quorumSize) {
            Logger.log("Proposal " + memberId + " " + myCounter + " is CHOSEN with value=" + proposalValue);

            Message decide = new Message(
                    Message.MessageType.LEARN,
                    memberId,
                    String.valueOf(myCounter),
                    proposalValue, // chosen value
                    null,
                    null,
                    Instant.now()
            );

            networkManager.broadcast(decide);
        }
    }

    public void timeoutHandler() {
        Logger.log("Timeout waiting for quorum, retrying prepare...");
        prepare(proposalValue);
    }

    public void abort() {
        Logger.log("Aborting current proposal " + memberId + " " + myCounter);
        pendingPromises.clear();
        pendingAccepts.clear();
    }
}