package com.demo.agentscope;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;
import com.demo.common.tools.WeatherTool;

/**
 * AgentScope ReActAgent 最小可运行示例：带工具调用的天气助手。
 */
public class HelloAgentScope {

    public static void main(String[] args) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("请设置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        // 注册工具
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new WeatherToolAdapter());

        // 构建 ReActAgent
        ReActAgent agent = ReActAgent.builder()
            .name("WeatherAgent")
            .sysPrompt("你是一个天气助手。当用户询问天气时，必须调用 get_weather 工具获取数据。")
            .model(DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .build())
            .toolkit(toolkit)
            .build();

        // 调用
        Msg response = agent.call(Msg.builder()
            .role(MsgRole.USER)
            .content(TextBlock.builder().text("杭州今天天气怎么样？").build())
            .build()
        ).block();

        System.out.println("=== AgentScope ReActAgent 结果 ===");
        System.out.println(response.getTextContent());
    }

    /**
     * 适配器：将公共 WeatherTool 包装为 AgentScope 的 @Tool 注解形式。
     */
    public static class WeatherToolAdapter {
        private final WeatherTool delegate = new WeatherTool();

        @io.agentscope.core.tool.Tool(name = "get_weather", description = "获取指定城市的天气信息")
        public String getWeather(
            @ToolParam(name = "city", description = "城市名称，如 北京、上海") String city) {
            return delegate.getWeather(city);
        }
    }
}
