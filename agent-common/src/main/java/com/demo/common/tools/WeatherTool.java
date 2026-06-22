package com.demo.common.tools;

import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 跨框架复用的天气查询工具逻辑。
 * Spring AI Alibaba 侧通过 @Tool 注解注册，AgentScope 侧通过 Toolkit.registerTool 注册。
 */
@Component
public class WeatherTool {

    private static final Map<String, String> WEATHER_DB = Map.of(
        "北京", "晴，25°C，湿度 30%",
        "上海", "多云，28°C，湿度 65%",
        "杭州", "小雨，22°C，湿度 80%",
        "深圳", "晴，30°C，湿度 55%",
        "成都", "阴，20°C，湿度 70%"
    );

    public String getWeather(String city) {
        return WEATHER_DB.getOrDefault(city, city + "：暂无天气数据");
    }
}
