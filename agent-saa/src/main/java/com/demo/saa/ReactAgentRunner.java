package com.demo.saa;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Spring AI Alibaba ReactAgent 最小可运行示例。
 */
public class ReactAgentRunner {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("AI_DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("DASHSCOPE_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("请设置环境变量 AI_DASHSCOPE_API_KEY 或 DASHSCOPE_API_KEY");
            return;
        }

        DashScopeApi api = DashScopeApi.builder().apiKey(apiKey).build();
        ChatModel chatModel = DashScopeChatModel.builder().dashScopeApi(api).build();

        ReactAgent agent = ReactAgent.builder()
            .name("assistant")
            .model(chatModel)
            .instruction("你是一个有帮助的 AI 助手，请用中文回答。")
            .build();

        AssistantMessage response = agent.call("你好，请用一句话介绍一下你自己。");

        System.out.println("=== SAA ReactAgent 结果 ===");
        System.out.println(response.getText());
    }
}
