package com.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 启动入口，聚合 agent-saa 和 agent-agentscope 双框架测试。
 */
@SpringBootApplication(scanBasePackages = "com.demo")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
