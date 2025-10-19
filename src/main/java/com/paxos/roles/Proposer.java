package com.paxos.roles;

import com.paxos.tools.Logger;
import com.paxos.tools.Message;
import com.paxos.tools.NetworkManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds the Proposer functionality of the PAXOS algorithm
 */
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

    /**
     * Prepares a proposal with specified value and broadcasts to all members
     *
     * @param value value to be proposed
     */
    public void prepare(String value) {
        // use time as monotonic increasing value
        myCounter = (int) Instant.now().toEpochMilli();

        this.proposalValue = value;

        Logger.log("Proposer " + memberId + " starting PREPARE phase with proposalNumber=" + myCounter);

        Message prepare = new Message(
                Message.MessageType.PREPARE,
                memberId,
                String.valueOf(myCounter),
                proposalValue,
                null,
                null
        );

        pendingPromises.clear();
        pendingAccepts.clear();
        networkManager.broadcast(prepare);
    }

    /**
     * Handle promise message from members - will update trackers in order to send the correct proposal value
     *
     * @param promise the PROMISE message
     */
    public void handlePromise(Message promise) {
        String sender = promise.getSender();
        Logger.log("[handlePromise] Received PROMISE from " + sender + " for proposalNumber=" + promise.getProposalNumber());

        // Ignore stale promises
        if (!String.valueOf(myCounter).equals(promise.getProposalNumber())) {
            Logger.log("[handlePromise] Ignoring stale PROMISE");
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

    /**
     * When proposer acquires enough promises we send ACCEPT_REQUEST to all members
     */
    private void whenQuorumPromises() {
        Logger.log("[whenQuorumPromises] Quorum of PROMISES reached (" + pendingPromises.size() + "/" + quorumSize +
                ") — sending ACCEPT_REQUEST with value=" + proposalValue);

        if (proposalValue == null) {
            Logger.log("[whenQuorumPromises] WARN: proposalValue is null — falling back to previously proposed value");
            proposalValue = highestAcceptedValueSeen != null ? highestAcceptedValueSeen : "LOST_VALUE";
        }

        Message acceptReq = new Message(
                Message.MessageType.ACCEPT_REQUEST,
                memberId,
                String.valueOf(myCounter),
                proposalValue,
                null,
                null
        );

        networkManager.broadcast(acceptReq);
    }

    /**
     * Handle ACCEPTED message and broadcast LEARN iff pending accepts exceeds quorum size
     * @param accepted the ACCEPT message from member
     */
    public void handleAccepted(Message accepted) {
        Logger.log("[handleAccepted] Received ACCEPTED from " + accepted.getSender() +
                " for proposalNumber=" + accepted.getProposalNumber());

        if (!String.valueOf(myCounter).equals(accepted.getProposalNumber())) return;

        pendingAccepts.put(accepted.getSender(), accepted);

        if (pendingAccepts.size() >= quorumSize) {
            Logger.log("[handleAccepted] Proposal " + memberId + " " + myCounter + " is CHOSEN with value=" + proposalValue);

            Message decide = new Message(
                    Message.MessageType.LEARN,
                    memberId,
                    String.valueOf(myCounter),
                    proposalValue, // chosen value
                    null,
                    proposalValue
            );

            networkManager.broadcast(decide);
        }
    }
}