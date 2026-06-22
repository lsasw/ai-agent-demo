package com.demo.saa.rag.service;

import com.alibaba.cloud.ai.dashscope.rag.DashScopeCloudStore;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrievalAdvisor;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentTransformerOptions;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentTransformer;
import com.demo.saa.rag.config.RagProperties;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 智能客服 RAG 核心服务。
 *
 * <p>职责：混合检索 + Prompt 增强 + LLM 生成 + 文档管理 + 质量统计。
 * 检索管线由 DashScope 云端完成（queryRewrite → dense + sparse → merge → rerank），
 * 服务层负责缓存、查询扩展、上下文组装和统计。
 *
 * @author dmw
 * @since 2026-06-22
 */
@Service
public class CustomerServiceRagService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceRagService.class);

    private final DashScopeDocumentRetriever retriever;
    private final DashScopeCloudStore cloudStore;
    private final DashScopeApi dashScopeApi;
    private final ChatModel chatModel;
    private final RagProperties properties;
    private final Cache<String, List<Document>> retrievalCache;

    /** 总查询次数 */
    private final AtomicLong totalQueries = new AtomicLong(0);

    /** 有结果返回的查询次数 */
    private final AtomicLong hitQueries = new AtomicLong(0);

    /** 已入库文档计数（内存快照） */
    private final AtomicLong documentCount = new AtomicLong(0);

    /** 文档元数据存储（内存副本，用于快速查询文档列表） */
    private final Map<String, DocumentMeta> documentMetaMap = new ConcurrentHashMap<>();

    public CustomerServiceRagService(
            DashScopeDocumentRetriever retriever,
            DashScopeCloudStore cloudStore,
            DashScopeApi dashScopeApi,
            ChatModel chatModel,
            RagProperties properties,
            Cache<String, List<Document>> retrievalCache) {
        this.retriever = retriever;
        this.cloudStore = cloudStore;
        this.dashScopeApi = dashScopeApi;
        this.chatModel = chatModel;
        this.properties = properties;
        this.retrievalCache = retrievalCache;
    }

    /**
     * 执行 RAG 增强对话。
     *
     * <p>流程：查询扩展 → 缓存检查 → 混合检索 → 上下文组装 → LLM 生成。
     *
     * @param userQuery 用户原始问题（口语化）
     * @return LLM 生成的客服回答（含文档引用标记）
     */
    public String chat(String userQuery) {
        long startTime = System.currentTimeMillis();
        totalQueries.incrementAndGet();

        try {
            // 1. 查询扩展：生成 2 个语义变体，提升召回率
            List<String> queries = properties.isEnableQueryExpansion()
                    ? expandQuery(userQuery)
                    : List.of(userQuery);

            // 2. 多路并行检索 + 缓存
            List<Document> allDocs = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            for (String q : queries) {
                List<Document> docs = retrieveFromCacheOrRemote(q);
                for (Document doc : docs) {
                    if (seenIds.add(doc.getId())) {
                        allDocs.add(doc);
                    }
                }
            }

            if (!allDocs.isEmpty()) {
                hitQueries.incrementAndGet();
            }

            // 3. 上下文压缩（如果文档总长度超过 3000 字符，做摘要压缩）
            List<Document> finalDocs = compressIfNeeded(allDocs, 3000);

            // 4. 拼装上下文
            String context = buildContextText(finalDocs);
            String systemPrompt = properties.getSystemPrompt();

            // 5. 调用 LLM 生成回答
            String prompt = buildUserPrompt(context, userQuery);

            ChatClient client = ChatClient.builder(chatModel)
                    .defaultSystem(systemPrompt)
                    .build();

            String answer = client.prompt()
                    .user(prompt)
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("RAG chat done | {}ms | {} docs retrieved | query: {}",
                    elapsed, finalDocs.size(), userQuery);

            return answer;

        } catch (Exception e) {
            log.error("RAG chat failed | query: {}", userQuery, e);
            return "抱歉，系统处理您的请求时出现异常，请稍后重试。错误信息：" + e.getMessage();
        }
    }

    /**
     * 带缓存的检索。先查 Caffeine 缓存，未命中则调 DashScope 云端检索。
     */
    private List<Document> retrieveFromCacheOrRemote(String query) {
        String normalizedKey = normalizeQuery(query);
        List<Document> cached = retrievalCache.getIfPresent(normalizedKey);
        if (cached != null) {
            log.debug("cache hit | key: {}", normalizedKey);
            return cached;
        }

        List<Document> docs = retriever.retrieve(Query.builder().text(query).build());
        if (docs != null && !docs.isEmpty()) {
            retrievalCache.put(normalizedKey, docs);
        }
        return docs != null ? docs : List.of();
    }

    /**
     * 查询扩展：利用 ChatModel 对短问题生成 2 个语义等价变体。
     */
    private List<String> expandQuery(String original) {
        if (original.length() >= 30) {
            return List.of(original);
        }
        try {
            String response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .user("将以下问题改写为 2 个语义等价但用词不同的表达，用 ||| 分隔，只输出改写结果：\n" + original)
                    .call()
                    .content();

            List<String> variants = new ArrayList<>();
            variants.add(original);
            if (response != null) {
                for (String part : response.split("\\|\\|\\|")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(original)) {
                        variants.add(trimmed);
                    }
                }
            }
            return variants;
        } catch (Exception e) {
            log.warn("query expansion failed, using original", e);
            return List.of(original);
        }
    }

    private String buildContextText(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            String title = doc.getMetadata() != null
                    ? String.valueOf(doc.getMetadata().getOrDefault("title", "unknown"))
                    : "unknown";
            sb.append("[Doc ").append(i + 1).append("] Source: ").append(title).append("\n");
            sb.append(doc.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private String buildUserPrompt(String context, String query) {
        return "## Reference Documents\n" + context
                + "\n## User Question\n" + query
                + "\n\n## Instructions\n"
                + "Answer based on the reference documents above. "
                + "If no relevant info found, clearly state so. "
                + "Cite sources at the end.";
    }

    private List<Document> compressIfNeeded(List<Document> docs, int maxTotalChars) {
        int totalChars = docs.stream().mapToInt(d -> d.getText().length()).sum();
        if (totalChars <= maxTotalChars) return docs;
        log.info("Compressing context: {} chars > {} threshold", totalChars, maxTotalChars);

        List<Document> compressed = new ArrayList<>();
        for (Document doc : docs) {
            if (doc.getText().length() < 200) {
                compressed.add(doc);
                continue;
            }
            try {
                String summary = ChatClient.builder(chatModel).build().prompt()
                        .user("Summarize in one sentence (max 80 chars):\n" + doc.getText())
                        .call().content();
                compressed.add(new Document(summary != null ? summary
                        : doc.getText().substring(0, 200), doc.getMetadata()));
            } catch (Exception e) {
                compressed.add(new Document(
                        doc.getText().substring(0, Math.min(300, doc.getText().length())),
                        doc.getMetadata()));
            }
        }
        return compressed;
    }

    // ── 文档管理 ──

    /**
     * 上传文档并入库到 DashScope 云端向量存储，以细/标准/粗三种粒度同时索引。
     */
    public DocumentMeta uploadDocument(String text, String title, String category) {
        Document doc = new Document(text, Map.of(
                "title", title, "category", category,
                "upload_time", String.valueOf(System.currentTimeMillis())));

        // 标准粒度
        cloudStore.add(List.of(doc));

        // 细粒度
        Document fineDoc = new Document(text, Map.of(
                "title", title + "(fine)", "category", category, "granularity", "fine"));
        DashScopeDocumentTransformer fineTransformer = new DashScopeDocumentTransformer(dashScopeApi,
                DashScopeDocumentTransformerOptions.builder()
                        .withChunkSize(properties.getFineChunkSize())
                        .withOverlapSize(properties.getOverlapSize())
                        .withLanguage("zh").build());
        cloudStore.add(fineTransformer.apply(List.of(fineDoc)));

        // 粗粒度
        Document coarseDoc = new Document(text, Map.of(
                "title", title + "(coarse)", "category", category, "granularity", "coarse"));
        DashScopeDocumentTransformer coarseTransformer = new DashScopeDocumentTransformer(dashScopeApi,
                DashScopeDocumentTransformerOptions.builder()
                        .withChunkSize(properties.getCoarseChunkSize())
                        .withOverlapSize(properties.getOverlapSize())
                        .withLanguage("zh").build());
        cloudStore.add(coarseTransformer.apply(List.of(coarseDoc)));

        String docId = doc.getId();
        DocumentMeta meta = new DocumentMeta(docId, title, category, text.length(),
                1 + fineTransformer.apply(List.of(fineDoc)).size()
                        + coarseTransformer.apply(List.of(coarseDoc)).size());
        documentMetaMap.put(docId, meta);
        documentCount.incrementAndGet();

        log.info("Document indexed | id: {} | title: {}", docId, title);
        return meta;
    }

    /**
     * 删除指定文档及其所有粒度的索引。
     */
    public boolean deleteDocument(String docId) {
        try {
            cloudStore.delete(List.of(docId));
            documentMetaMap.remove(docId);
            documentCount.decrementAndGet();
            log.info("Document deleted | id: {}", docId);
            return true;
        } catch (Exception e) {
            log.error("Delete failed | id: {}", docId, e);
            return false;
        }
    }

    /**
     * 获取所有已入库文档的元数据列表。
     */
    public List<DocumentMeta> listDocuments() {
        return new ArrayList<>(documentMetaMap.values());
    }

    // ── 质量统计 ──

    public RagStats getStats() {
        RagStats stats = new RagStats();
        stats.setTotalQueries(totalQueries.get());
        stats.setHitQueries(hitQueries.get());
        stats.setDocumentCount(documentCount.get());
        stats.setCacheHitCount(retrievalCache.stats().hitCount());
        stats.setCacheMissCount(retrievalCache.stats().missCount());
        stats.setHitRate(totalQueries.get() > 0
                ? (double) hitQueries.get() / totalQueries.get() * 100 : 0.0);
        stats.setCacheHitRate(
                (retrievalCache.stats().hitCount() + retrievalCache.stats().missCount()) > 0
                        ? retrievalCache.stats().hitRate() * 100 : 0.0);
        return stats;
    }

    private String normalizeQuery(String query) {
        return query.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    // ── DTO 内部类 ──

    public static class DocumentMeta {
        private String id;
        private String title;
        private String category;
        private int charCount;
        private int chunkCount;

        public DocumentMeta() {}
        public DocumentMeta(String id, String title, String category, int charCount, int chunkCount) {
            this.id = id; this.title = title; this.category = category;
            this.charCount = charCount; this.chunkCount = chunkCount;
        }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public int getCharCount() { return charCount; }
        public void setCharCount(int charCount) { this.charCount = charCount; }
        public int getChunkCount() { return chunkCount; }
        public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
    }

    public static class RagStats {
        private long totalQueries;
        private long hitQueries;
        private long documentCount;
        private long cacheHitCount;
        private long cacheMissCount;
        private double hitRate;
        private double cacheHitRate;

        public long getTotalQueries() { return totalQueries; }
        public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }
        public long getHitQueries() { return hitQueries; }
        public void setHitQueries(long hitQueries) { this.hitQueries = hitQueries; }
        public long getDocumentCount() { return documentCount; }
        public void setDocumentCount(long documentCount) { this.documentCount = documentCount; }
        public long getCacheHitCount() { return cacheHitCount; }
        public void setCacheHitCount(long cacheHitCount) { this.cacheHitCount = cacheHitCount; }
        public long getCacheMissCount() { return cacheMissCount; }
        public void setCacheMissCount(long cacheMissCount) { this.cacheMissCount = cacheMissCount; }
        public double getHitRate() { return hitRate; }
        public void setHitRate(double hitRate) { this.hitRate = hitRate; }
        public double getCacheHitRate() { return cacheHitRate; }
        public void setCacheHitRate(double cacheHitRate) { this.cacheHitRate = cacheHitRate; }
    }
}
