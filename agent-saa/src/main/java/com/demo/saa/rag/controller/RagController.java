package com.demo.saa.rag.controller;

import com.demo.saa.rag.service.CustomerServiceRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能客服 RAG 系统的 REST API 控制器。
 *
 * <p>提供客服对话、文档管理、检索质量统计等接口。
 *
 * @author dmw
 * @since 2026-06-22
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final CustomerServiceRagService ragService;

    public RagController(CustomerServiceRagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 客服对话接口 —— RAG 增强回答。
     *
     * <p>请求体示例：
     * <pre>{@code
     * {
     *   "query": "Claude Code 如何安装？"
     * }
     * }</pre>
     *
     * @param body 包含 {@code query} 字段的请求体
     * @return 包含客服回答和检索元数据的响应
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "query 字段不能为空"));
        }

        log.info("RAG chat request | query: {}", query);
        long startTime = System.currentTimeMillis();

        String answer = ragService.chat(query);

        long elapsed = System.currentTimeMillis() - startTime;
        return ResponseEntity.ok(Map.of(
                "query", query,
                "answer", answer,
                "elapsedMs", elapsed
        ));
    }

    /**
     * 文档上传接口 —— 将文本知识存入 DashScope 云端向量库。
     *
     * <p>请求体示例：
     * <pre>{@code
     * {
     *   "title": "Claude Code 概述",
     *   "category": "overview",
     *   "content": "Claude Code 是 Anthropic 推出的终端 AI 编程智能体..."
     * }
     * }</pre>
     */
    @PostMapping("/docs/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String content = body.get("content");
        String category = body.getOrDefault("category", "general");

        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "title 和 content 字段不能为空"));
        }

        log.info("Upload document | title: {} | category: {}", title, category);

        CustomerServiceRagService.DocumentMeta meta = ragService.uploadDocument(content, title, category);
        return ResponseEntity.ok(Map.of(
                "id", meta.getId(),
                "title", meta.getTitle(),
                "category", meta.getCategory(),
                "charCount", meta.getCharCount(),
                "chunkCount", meta.getChunkCount(),
                "status", "indexed"
        ));
    }

    /**
     * 文档列表接口 —— 获取所有已入库文档的元数据。
     */
    @GetMapping("/docs/list")
    public ResponseEntity<List<CustomerServiceRagService.DocumentMeta>> listDocuments() {
        return ResponseEntity.ok(ragService.listDocuments());
    }

    /**
     * 文档删除接口 —— 从向量库中删除指定文档及所有粒度的索引。
     */
    @DeleteMapping("/docs/{docId}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String docId) {
        boolean deleted = ragService.deleteDocument(docId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("id", docId, "status", "deleted"));
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "删除失败，请检查文档 ID 是否正确"));
        }
    }

    /**
     * 质量统计接口 —— 获取检索召回率、缓存命中率等关键指标。
     */
    @GetMapping("/stats")
    public ResponseEntity<CustomerServiceRagService.RagStats> getStats() {
        return ResponseEntity.ok(ragService.getStats());
    }

    /**
     * 健康检查接口。
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "rag-customer-service",
                "timestamp", java.time.Instant.now().toString()
        ));
    }
}
