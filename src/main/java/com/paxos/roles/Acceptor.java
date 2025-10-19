package com.paxos.roles;

import com.paxos.tools.Logger;
import com.paxos.tools.Message;
import com.paxos.tools.NetworkManager;

import java.io.*;

/**
 * The Acceptor functionality of the PAXOS algorithm
 */
public class Acceptor {
    private final Object lock = new Object();

    public String memberId;
    public Integer promisedProposalNumber = null;
    public Integer acceptedNumber = null;
    public String acceptedValue = null;

    public NetworkManager networkManager;
    public String stateFilePath;

    public Acceptor(String memberId, NetworkManager networkManager, String stateFilePath) {
        this.memberId = memberId;
        this.networkManager = networkManager;
        this.stateFilePath = stateFilePath;
    }

    /**
     * Handles a message of type Prepare
     *
     * @param msg the message that is incoming
     */
    public void onPrepare(Message msg) {
        Logger.log("[onPrepare] Received PREPARE from " + msg.getSender() + " with proposalNumber=" + msg.getProposalNumber());

        Integer proposedNumber = parseProposalNumber(msg.getProposalNumber());
        if (proposedNumber == null) return;

        synchronized (lock) { // lock in case multiple prepares come in at the same time
            if (isHigherProposal(proposedNumber)) {
                promisedProposalNumber = proposedNumber;

                Message promise = new Message(
                        Message.MessageType.PROMISE,
                        memberId,
                        msg.getProposalNumber(),
                        null,
                        acceptedNumber != null ? acceptedNumber.toString() : null,
                        acceptedValue
                );

                Logger.log("[onPrepare] Sending PROMISE to " + msg.getSender());
                networkManager.sendMessage(msg.getSender(), promise);
            } else {
                Logger.log("[onPrepare] Ignoring PREPARE as higher proposal number seen highest:" + promisedProposalNumber + " seen:" + msg.getProposalNumber());
            }
        }
    }

    /**
     * Handles a message of type Accept_Request
     *
     * @param msg the message to be handled
     */
    public void onAcceptRequest(Message msg) {
        Logger.log("[onAcceptRequest] Received ACCEPT_REQUEST from " + msg.getSender() + " proposalNumber=" + msg.getProposalNumber());

        Integer proposedNumber = parseProposalNumber(msg.getProposalNumber());
        if (proposedNumber == null) return;

        synchronized (lock) {
            if (isHigherOrEqualProposal(proposedNumber)) {
                promisedProposalNumber = proposedNumber;
                acceptedNumber = proposedNumber;
                acceptedValue = msg.getProposalValue();

                Message accepted = new Message(
                        Message.MessageType.ACCEPTED,
                        memberId,
                        msg.getProposalNumber(),
                        msg.getProposalValue(),
                        acceptedNumber != null ? acceptedNumber.toString() : null,
                        acceptedValue
                );

                Logger.log("[onAcceptRequest] Broadcasting ACCEPTED for proposalNumber=" + msg.getProposalNumber());
                networkManager.broadcast(accepted);
            } else {
                Logger.log("[onAcceptRequest] Ignoring ACCEPT_REQUEST as higher or equal number seen highest:" + acceptedNumber + " seen:" + msg.getAcceptedNumber());
            }
        }
    }

    /**
     * Utility function to convert strings to int without throwing
     * @param numStr the string to convert
     * @return the parsed value or null on failure
     */
    private Integer parseProposalNumber(String numStr) {
        if (numStr == null) return null;
        try {
            return Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            Logger.log("[parseProposalNumber] Failed to parse proposal number: " + numStr);
            return null;
        }
    }

    /**
     * Utility function to compare proposals
     * @param proposalNumber proposed number
     * @return true if proposal is higher else false
     */
    private boolean isHigherProposal(Integer proposalNumber) {
        return proposalNumber != null &&
                (promisedProposalNumber == null || proposalNumber > promisedProposalNumber);
    }

    /**
     * Utility function to compare proposals
     * @param proposalNumber proposed number
     * @return true if proposal is higher or equal false otherwise
     */
    private boolean isHigherOrEqualProposal(Integer proposalNumber) {
        return proposalNumber != null &&
                (promisedProposalNumber == null || proposalNumber >= promisedProposalNumber);
    }
}
