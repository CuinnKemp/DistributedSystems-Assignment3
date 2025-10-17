package com.paxos.roles;

import com.paxos.tools.Logger;
import com.paxos.tools.Message;
import com.paxos.tools.NetworkManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Learner {
    private final String memberId;
    private final int majority;
    private final NetworkManager networkManager;

    private final Map<String, Integer> proposalNumberToCount = new HashMap<>();
    private String learnedValue = null;
    private boolean decided = false;

    public Learner(String memberId, int totalMembers, NetworkManager networkManager) {
        this.memberId = memberId;
        this.majority = (totalMembers / 2) + 1;
        this.networkManager = networkManager;
    }

    /**
     * Handles ACCEPTED message
     * @param msg The message to be handled
     */
    public synchronized void onAccepted(Message msg) {
        if (decided) return;

        String acceptedNumber = msg.getAcceptedNumber();
        String acceptedValue = msg.getAcceptedValue();

        Logger.log("Learner " + memberId + " received ACCEPTED for proposalNumber=" +
                acceptedNumber + " value=" + acceptedValue);

        // Increment count for this proposalNumber
        proposalNumberToCount.put(acceptedNumber,
                proposalNumberToCount.getOrDefault(acceptedNumber, 0) + 1);

        // Check for majority
        int count = proposalNumberToCount.get(acceptedNumber);
        if (count >= majority) {
            Logger.log("Majority reached for proposalNumber=" + acceptedNumber +
                    " with value=" + acceptedValue + " (" + count + "/" + majority + ")");
            onDecide(acceptedValue);
        }
    }

    /**
     * Called when the learner reaches a majority decision.
     */
    public synchronized void onDecide(String value) {
        if (decided) return;

        decided = true;
        learnedValue = value;

        Logger.log("CONSENSUS: " + value + " has been chosen!");

        // Optionally broadcast LEARN to inform all members
        Message learnMsg = new Message(
                Message.MessageType.LEARN,
                memberId,
                null,           // proposalNumber not relevant
                null,           // proposalValue not relevant
                null,           // acceptedNumber not relevant
                value,          // acceptedValue carries the chosen value
                Instant.now()
        );

        networkManager.broadcast(learnMsg);
    }

    /**
     * Check whether the learner has already decided.
     */
    public synchronized boolean isDecided() {
        return decided;
    }
}
