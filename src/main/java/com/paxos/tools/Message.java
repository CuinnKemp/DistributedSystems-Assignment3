package com.paxos.tools;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic message with all required values
 */
public class Message {
    public enum MessageType {
        PREPARE,
        PROMISE,
        ACCEPT_REQUEST,
        ACCEPTED,
        NACK,
        LEARN,
        LEARN_ACK,
        VALUE
    }

    final MessageType type;
    final String sender;
    final String proposalNumber;
    final String proposalValue;
    final String acceptedNumber;
    final String acceptedValue;

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

    public Message(
            MessageType type,
            String sender,
            String proposalNumber,
            String proposalValue,
            String acceptedNumber,
            String acceptedValue
    ) {
        this.type = type;
        this.sender = sender;
        this.proposalNumber = proposalNumber;
        this.proposalValue = proposalValue;
        this.acceptedNumber = acceptedNumber;
        this.acceptedValue = acceptedValue;
    }

    /**
     * Creates a json string with the non-null values in the class
     * @return the json string of all non-null fields
     */
    public String toString() {
        Map<String, String> componentMap = new HashMap<>();
        componentMap.put("type", type.toString());
        if (sender != null) componentMap.put("sender", sender);
        if (proposalNumber != null) componentMap.put("proposalNumber", proposalNumber);
        if (proposalValue != null) componentMap.put("proposalValue", proposalValue);
        if (acceptedNumber != null) componentMap.put("acceptedNumber", acceptedNumber);
        if (acceptedValue != null) componentMap.put("acceptedValue", acceptedValue);

        return SimpleJsonUtil.stringify(componentMap);
    }

    /**
     * creates a Message object from a json string
     *
     * @param json message in json format
     * @return A Message object from input json - any non-present value is set to null
     */
    public static Message fromJson(String json){
        Map<String, String> parsed = SimpleJsonUtil.parse(json);
        return new Message(
                MessageType.valueOf(parsed.get("type")),
                parsed.get("sender"),
                parsed.get("proposalNumber"),
                parsed.get("proposalValue"),
                parsed.get("acceptedNumber"),
                parsed.get("acceptedValue")
        );
    }

}
