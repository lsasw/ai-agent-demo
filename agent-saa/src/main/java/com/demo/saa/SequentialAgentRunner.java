package com.demo.saa;

import java.util.List;
import java.util.Optional;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Spring AI Alibaba SequentialAgent 测试：翻译 → 摘要 串联流水线。
 */
public class SequentialAgentRunner {

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

        // Agent 1: 翻译
        ReactAgent translator = ReactAgent.builder()
            .name("translator")
            .model(chatModel)
            .instruction("你是一个专业翻译。将用户输入翻译成中文，只输出翻译结果。")
            .build();

        // Agent 2: 摘要
        ReactAgent summarizer = ReactAgent.builder()
            .name("summarizer")
            .model(chatModel)
            .instruction("你是一个摘要助手。用一句话总结以下内容，只输出摘要。")
            .build();

        // 串联流水线（使用 subAgents 而非 addAgent）
        SequentialAgent pipeline = SequentialAgent.builder()
            .name("translate_summarize")
            .subAgents(List.of(translator, summarizer))
            .build();

        String input = "Artificial intelligence is transforming every industry. "
            + "From healthcare to finance, AI-powered solutions are enabling "
            + "faster decision-making, reducing costs, and unlocking new possibilities.";

        // invoke 返回 Optional<OverAllState>
        Optional<OverAllState> result = pipeline.invoke(input);
        System.out.println("=== SAA SequentialAgent 结果 ===");
        result.ifPresentOrElse(
            state -> System.out.println(state.data()),
            () -> System.out.println("无结果")
        );
    }
}
