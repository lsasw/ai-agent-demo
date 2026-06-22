package com.demo.agentscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.pipeline.SequentialPipeline;

/**
 * AgentScope 多智能体协作示例：翻译 → 摘要 顺序流水线。
 *
 * 注意：AgentScope 2.0 计划移除 pipeline 包，升级时需替换为 HarnessAgent。
 */
public class PipelineExample {

    public static void main(String[] args) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("请设置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        DashScopeChatModel model = DashScopeChatModel.builder()
            .apiKey(apiKey)
            .modelName("qwen-plus")
            .build();

        // Agent 1: 翻译
        ReActAgent translator = ReActAgent.builder()
            .name("Translator")
            .sysPrompt("你是一个专业翻译。将用户输入翻译成中文，只输出翻译结果。")
            .model(model)
            .build();

        // Agent 2: 摘要
        ReActAgent summarizer = ReActAgent.builder()
            .name("Summarizer")
            .sysPrompt("你是一个摘要助手。用一句话总结以下内容，只输出摘要。")
            .model(model)
            .build();

        // 顺序流水线
        SequentialPipeline pipeline = SequentialPipeline.builder()
            .addAgent(translator)
            .addAgent(summarizer)
            .build();

        Msg userMsg = Msg.builder()
            .role(MsgRole.USER)
            .content(TextBlock.builder()
                .text("Artificial intelligence is transforming every industry. "
                    + "From healthcare to finance, AI-powered solutions are enabling "
                    + "faster decision-making, reducing costs, and unlocking new possibilities.")
                .build())
            .build();

        System.out.println("=== AgentScope SequentialPipeline 结果 ===");
        Msg result = pipeline.execute(userMsg).block();
        System.out.println(result.getTextContent());
    }
}
