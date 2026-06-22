package com.demo.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 客服对话请求 DTO。
 *
 * @author dmw
 * @since 2026-06-22
 */
@Schema(description = "客服对话请求")
public class ChatRequest {

    @Schema(description = "用户提问内容", example = "Claude Code 如何安装？", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "框架选择：saa（Spring AI Alibaba）或 agentscope（AgentScope-Java）",
            example = "saa", allowableValues = {"saa", "agentscope"})
    private String agentType;

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
