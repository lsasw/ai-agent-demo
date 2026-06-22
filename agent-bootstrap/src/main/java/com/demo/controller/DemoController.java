package com.demo.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.demo.common.dto.ChatRequest;
import com.demo.common.tools.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.tool.Toolkit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST 对比测试入口，同时跑通 SAA ReactAgent 和 AgentScope ReActAgent。
 * 注意：两个框架都有 DashScopeChatModel，必须通过全限定名区分。
 */
@RestController
@RequestMapping("/api/demo")
@Tag(name = "框架对比测试", description = "Spring AI Alibaba 与 AgentScope-Java 双框架对比接口")
public class DemoController {

    private final String apiKey;

    public DemoController() {
        this.apiKey = getApiKeyFromEnv();
    }

    private static String getApiKeyFromEnv() {
        String key = System.getenv("AI_DASHSCOPE_API_KEY");
        if (key != null && !key.isBlank()) return key;
        key = System.getenv("DASHSCOPE_API_KEY");
        if (key != null && !key.isBlank()) return key;
        return null;
    }

    @PostMapping("/chat")
    @Operation(summary = "双框架对话测试",
               description = "根据 agentType 选择 SAA ReactAgent 或 AgentScope ReActAgent 回答用户问题。")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        if (apiKey == null) {
            return Map.of("error", "请设置环境变量 AI_DASHSCOPE_API_KEY 或 DASHSCOPE_API_KEY");
        }

        String result;
        if ("agentscope".equalsIgnoreCase(request.getAgentType())) {
            result = callAgentScope(request.getMessage());
        } else {
            result = callSaaAgent(request.getMessage());
        }

        return Map.of(
            "framework", request.getAgentType() != null ? request.getAgentType() : "saa",
            "response", result
        );
    }

    private String callSaaAgent(String message) {
        try {
            DashScopeApi api = DashScopeApi.builder().apiKey(apiKey).build();
            ChatModel chatModel = com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel.builder()
                .dashScopeApi(api).build();
            ReactAgent agent = ReactAgent.builder()
                .name("assistant")
                .model(chatModel)
                .instruction("你是一个有帮助的 AI 助手，请用中文回答。")
                .build();
            AssistantMessage response = agent.call(message);
            return response.getText();
        } catch (Exception e) {
            return "SAA 调用失败: " + e.getMessage();
        }
    }

    private String callAgentScope(String message) {
        try {
            WeatherTool weatherTool = new WeatherTool();
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(new Object() {
                @io.agentscope.core.tool.Tool(name = "get_weather", description = "获取指定城市的天气信息")
                public String getWeather(
                    @io.agentscope.core.tool.ToolParam(name = "city", description = "城市名称") String city) {
                    return weatherTool.getWeather(city);
                }
            });

            ReActAgent agent = ReActAgent.builder()
                .name("WeatherAgent")
                .sysPrompt("你是一个天气助手。当用户询问天气时，调用 get_weather 工具。")
                .model(io.agentscope.core.model.DashScopeChatModel.builder()
                    .apiKey(apiKey)
                    .modelName("qwen-plus")
                    .build())
                .toolkit(toolkit)
                .build();

            Msg response = agent.call(Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(message).build())
                .build()).block();
            return response.getTextContent();
        } catch (Exception e) {
            return "AgentScope 调用失败: " + e.getMessage();
        }
    }

    @GetMapping("/health")
    @Operation(summary = "服务健康检查", description = "检查框架对比测试服务是否正常运行。")
    public Map<String, String> health() {
        return Map.of("status", "UP", "timestamp", java.time.Instant.now().toString());
    }
}
