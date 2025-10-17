package com.paxos.tools;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Message {
    public enum MessageType {
        PREPARE,
        PROMISE,
        ACCEPT_REQUEST,
        ACCEPTED,
        NACK,
        LEARN,
    }

    final MessageType type;
    final String sender;
    final String proposalNumber;
    final String proposalValue;
    final String acceptedNumber;
    final String acceptedValue;
    final Instant timestamp;

    public MessageType getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getProposalNumber() {
        return proposalNumber;
    }

    public String getProposalValue() {
        return proposalValue;
    }

    public String getAcceptedNumber() {
        return acceptedNumber;
    }

    public String getAcceptedValue() {
        return acceptedValue;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Message(
            MessageType type,
            String sender,
            String proposalNumber,
            String proposalValue,
            String acceptedNumber,
            String acceptedValue,
            Instant timestamp
    ) {
        this.type = type;
        this.sender = sender;
        this.proposalNumber = proposalNumber;
        this.proposalValue = proposalValue;
        this.acceptedNumber = acceptedNumber;
        this.acceptedValue = acceptedValue;
        this.timestamp = timestamp;
    }


    public String toString() {
        Map<String, String> componentMap = new HashMap<>();
        componentMap.put("type", type.toString());
        componentMap.put("sender", sender);
        if (proposalNumber != null) componentMap.put("proposalNumber", proposalNumber);
        if (proposalValue != null) componentMap.put("proposalValue", proposalValue);
        if (acceptedNumber != null) componentMap.put("acceptedNumber", acceptedNumber);
        if (acceptedValue != null) componentMap.put("acceptedValue", acceptedValue);
        if (timestamp != null) componentMap.put("timestamp", timestamp.toString());

        return SimpleJsonUtil.stringify(componentMap);
    }

    public static Message fromJson(String json){
        Map<String, String> parsed = SimpleJsonUtil.parse(json);
        return new Message(
                MessageType.valueOf(parsed.get("type")),
                parsed.get("sender"),
                parsed.get("proposalNumber"),
                parsed.get("proposalValue"),
                parsed.get("acceptedNumber"),
                parsed.get("acceptedValue"),
                Instant.parse(parsed.get("timestamp"))
        );
    }

}
