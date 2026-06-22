package com.demo.common.dto;

public class ChatRequest {
    private String message;
    private String agentType; // "saa" 或 "agentscope"

    public ChatRequest() {}

    public ChatRequest(String message, String agentType) {
        this.message = message;
        this.agentType = agentType;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
}
