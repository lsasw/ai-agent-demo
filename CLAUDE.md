# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

项目需要 JDK 17+。Windows 下构建命令（必须显式指定 JDK 17 路径，Maven 的 shell wrapper 受 JAVA_HOME 环境影响）：

```bash
"E:/java/jdk-17.0.7/bin/java" \
  -classpath "E:/java/apache-maven-3.9.4/boot/plexus-classworlds-2.7.0.jar" \
  -Dclassworlds.conf="E:/java/apache-maven-3.9.4/bin/m2.conf" \
  -Dmaven.home="E:/java/apache-maven-3.9.4" \
  -Dmaven.multiModuleProjectDirectory="E:/AI/ai-agent-demo" \
  -Dfile.encoding=UTF-8 \
  org.codehaus.plexus.classworlds.launcher.Launcher \
  clean install -DskipTests
```

- 全量编译 + 跳过测试：`... Launcher clean install -DskipTests`
- 单模块编译：`... Launcher clean compile -pl agent-saa`
- 启动应用：先设置 `AI_DASHSCOPE_API_KEY`，再运行 `agent-bootstrap` 的 `Application.main()`

## 模块架构

```
ai-agent-demo-parent (pom, 版本统一管理)
├── agent-common        → 公共 DTO、跨框架工具类
├── agent-saa           → Spring AI Alibaba: ReactAgent, SequentialAgent, RAG 智能客服
├── agent-agentscope    → AgentScope-Java: ReActAgent, SequentialPipeline
└── agent-bootstrap     → Spring Boot 启动入口, REST Controller
```

依赖方向：`bootstrap` → `saa` + `agentscope` → `common`。

## Spring AI Alibaba 实际 API

`spring-ai-alibaba-agent-framework` 1.1.2.0 的实际 API 与常见预期不符：

- `ReactAgent` → `com.alibaba.cloud.ai.graph.agent.ReactAgent`（非 `...agent.framework.ReactAgent`）
- `DashScopeApi` → `com.alibaba.cloud.ai.dashscope.api.DashScopeApi`
- `DashScopeChatModel` → `com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel`
- `SequentialAgent` Builder 用 `.subAgents(List.of(...))` 而非 `.addAgent()`
- `ReactAgent.call(String)` 返回同步 `AssistantMessage`，不是 `Mono<String>`
- SAA 没有 `@Tool`/`Tool` 注解，工具通过 Spring AI 的 `ToolCallback` 接口注册

## RAG 智能客服系统

位于 `agent-saa/.../rag/`，基于 DashScope 云端向量存储：

| 类 | 职责 |
|---|---|
| `RagProperties` | `@ConfigurationProperties(prefix="rag")`，可配置参数 |
| `RagConfig` | Bean 装配：DashScopeApi → CloudStore → Retriever → Cache |
| `CustomerServiceRagService` | 核心：混合检索 + Prompt 组装 + LLM 生成 + 文档管理 + 统计 |
| `RagController` | REST API（`/api/rag/chat`, `/docs/upload`, `/docs/list`, `/stats`） |
| `RagDataInitializer` | `CommandLineRunner`，启动时自动上传 5 篇 Claude Code 种子文档 |

检索管线由 DashScope 云端完成：queryRewrite → dense(5) + sparse(3) → merge → rerank(gte-rerank, minScore=0.35) → topN(3)。

服务层额外做了查询扩展（短问题时生成 2 个语义变体并行检索）和 Caffeine 缓存（10 分钟 TTL）。

## 双框架 DashScopeChatModel 冲突

`agent-bootstrap` 同时依赖 SAA 和 AgentScope，两个框架都有 `DashScopeChatModel` 类。必须用全限定名区分。

## BOM 与仓库

- Spring Milestones 仓库：`https://repo.spring.io/milestone`（父 POM 已配置）
- AgentScope 稳定版：`1.0.12`（`1.1.0` 不存在于 Maven Central）
