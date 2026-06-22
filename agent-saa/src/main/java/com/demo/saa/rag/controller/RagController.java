package com.demo.saa.rag.controller;

import com.demo.saa.rag.service.CustomerServiceRagService;
import com.demo.saa.rag.service.DocumentCrawlerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "智能客服 RAG", description = "基于检索增强生成的智能客服对话与知识库管理接口")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);
    private final CustomerServiceRagService ragService;
    private final DocumentCrawlerService crawlerService;

    public RagController(CustomerServiceRagService ragService, DocumentCrawlerService crawlerService) {
        this.ragService = ragService;
        this.crawlerService = crawlerService;
    }

    // ──────────────────────── 对话 ────────────────────────

    /**
     * 客服对话接口 —— RAG 增强回答。
     *
     * <p>用户提交自然语言问题，系统从知识库中检索相关文档片段，
     * 经过查询改写、混合检索、精排后，拼入 Prompt 调用大模型生成回答。
     *
     * @param body 包含 {@code query} 字段的请求体
     * @return 客服回答、检索耗时等元数据
     */
    @PostMapping("/chat")
    @Operation(summary = "客服对话（RAG 增强）",
               description = "根据用户问题从知识库中检索相关文档，结合大模型生成回答。"
                           + "检索管线：queryRewrite → dense(5) + sparse(3) → rerank(gte-rerank) → topN(3)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功返回 AI 生成的客服回答"),
        @ApiResponse(responseCode = "400", description = "请求参数缺失 query 字段")
    })
    public ResponseEntity<Map<String, Object>> chat(
            @RequestBody Map<String, String> body) {
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

    // ──────────────────────── 文档管理 ────────────────────────

    /**
     * 文档上传接口 —— 将文本知识以三种粒度（细/标准/粗）入库到 DashScope 云端向量存储。
     *
     * @param body 包含 title、content、category 字段的请求体
     * @return 入库后的文档元数据
     */
    @PostMapping("/docs/upload")
    @Operation(summary = "上传知识库文档",
               description = "上传文本知识到 DashScope 云端向量存储，自动以细(400字)、"
                           + "标准(800字)、粗(1200字)三种粒度切片并向量化入库。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "文档入库成功，返回元数据"),
        @ApiResponse(responseCode = "400", description = "缺少 title 或 content 字段")
    })
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestBody Map<String, String> body) {
        String title = body.get("title");
        String content = body.get("content");
        String category = body.getOrDefault("category", "general");

        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "title 和 content 字段不能为空"));
        }

        log.info("Upload document | title: {} | category: {}", title, category);
        CustomerServiceRagService.DocumentMeta meta =
                ragService.uploadDocument(content, title, category);

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
     *
     * @return 文档元数据列表
     */
    @GetMapping("/docs/list")
    @Operation(summary = "查询知识库文档列表",
               description = "获取所有已入库文档的元数据，包括标题、分类、字符数、切片数。")
    @ApiResponse(responseCode = "200", description = "文档元数据列表")
    public ResponseEntity<List<CustomerServiceRagService.DocumentMeta>> listDocuments() {
        return ResponseEntity.ok(ragService.listDocuments());
    }

    /**
     * 文档删除接口 —— 从向量库中删除指定文档及所有粒度的索引。
     *
     * @param docId 文档唯一标识
     * @return 删除结果
     */
    @DeleteMapping("/docs/{docId}")
    @Operation(summary = "删除知识库文档",
               description = "根据文档 ID 从 DashScope 向量库中删除文档及其所有粒度（细/标准/粗）的向量索引。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "400", description = "文档 ID 不存在或删除失败")
    })
    public ResponseEntity<Map<String, Object>> deleteDocument(
            @Parameter(description = "文档唯一标识 ID", required = true, example = "abc123")
            @PathVariable String docId) {
        boolean deleted = ragService.deleteDocument(docId);
        if (deleted) {
            return ResponseEntity.ok(Map.of("id", docId, "status", "deleted"));
        }
        return ResponseEntity.badRequest()
                .body(Map.of("error", "删除失败，请检查文档 ID 是否正确"));
    }

    // ──────────────────────── 统计 ────────────────────────

    /**
     * 质量统计接口 —— 获取检索召回率、缓存命中率等关键指标。
     */
    @GetMapping("/stats")
    @Operation(summary = "检索质量统计",
               description = "获取检索系统的关键质量指标：总查询次数、有结果率、"
                           + "缓存命中率（指示检索效率和重复查询占比）、已入库文档数。")
    @ApiResponse(responseCode = "200", description = "检索质量统计数据")
    public ResponseEntity<CustomerServiceRagService.RagStats> getStats() {
        return ResponseEntity.ok(ragService.getStats());
    }

    /**
     * 健康检查接口。
     */
    @PostMapping("/docs/crawl")
    @Operation(summary = "全量爬取 Claude Code 中文文档",
               description = "从 code.claude.com/docs/zh-CN/ 爬取 35 篇核心中文文档全文，自动提取正文并批量入库 DashScope。耗时约 1-2 分钟。")
    @ApiResponse(responseCode = "200", description = "爬取完成，返回各页面的入库结果")
    public ResponseEntity<Map<String, Object>> crawlDocs() {
        log.info("Starting full crawl of Claude Code zh-CN docs...");
        Map<String, Object> result = crawlerService.crawlAll();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    @Operation(summary = "服务健康检查", description = "检查 RAG 智能客服服务是否正常运行。")
    @ApiResponse(responseCode = "200", description = "服务正常")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "rag-customer-service",
                "timestamp", java.time.Instant.now().toString()
        ));
    }
}
