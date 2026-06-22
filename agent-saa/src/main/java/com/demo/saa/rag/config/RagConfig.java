package com.demo.saa.rag.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeCloudStore;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrievalAdvisor;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeStoreOptions;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentTransformerOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * RAG 系统的 Spring Bean 配置。
 *
 * <p>负责创建 DashScope API 客户端、向量存储、检索器、精排顾问、
 * ChatClient 以及检索结果缓存。
 *
 * @author dmw
 * @since 2026-06-22
 */
@Configuration
public class RagConfig {

    // ==================== DashScopeApi ====================

    /**
     * DashScope API 客户端单例 Bean。
     *
     * <p>复用同一个 HTTP 连接池，提升检索效率。
     * API Key 通过 {@code spring.ai.dashscope.api-key} 或环境变量注入。
     */
    @Bean
    public DashScopeApi dashScopeApi(
            @Value("${spring.ai.dashscope.api-key:${AI_DASHSCOPE_API_KEY:${DASHSCOPE_API_KEY:}}}") String apiKey,
            RagProperties properties) {

        return DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
    }

    // ==================== Embedding ====================

    /**
     * DashScope Embedding 模型，用于将文档转为向量。
     */
    @Bean
    public DashScopeEmbeddingModel embeddingModel(DashScopeApi api) {
        return new DashScopeEmbeddingModel(api);
    }

    /**
     * Embedding 配置选项。
     */
    @Bean
    public DashScopeEmbeddingOptions embeddingOptions() {
        return DashScopeEmbeddingOptions.builder()
                .model("text-embedding-v2")
                .textType("document")
                .build();
    }

    // ==================== 文档切片配置 ====================

    /**
     * 标准粒度切片器（chunkSize=800，overlap=100）。
     */
    @Bean
    public DashScopeDocumentTransformerOptions transformerOptions(RagProperties properties) {
        return DashScopeDocumentTransformerOptions.builder()
                .withChunkSize(properties.getChunkSize())
                .withOverlapSize(properties.getOverlapSize())
                .withLanguage("zh")
                .build();
    }

    // ==================== VectorStore ====================

    /**
     * DashScope 云端向量存储 —— 阿里巴巴提供的托管文档存储服务。
     *
     * <p>文档上传后将自动切片、向量化并持久化到 DashScope Pipeline。
     * {@code indexName} 对应 DashScope 控制台中的 Pipeline 名称。
     */
    @Bean
    public DashScopeStoreOptions storeOptions(RagProperties properties,
                                               DashScopeDocumentTransformerOptions transformerOptions,
                                               DashScopeEmbeddingOptions embeddingOptions) {
        DashScopeStoreOptions options = new DashScopeStoreOptions(properties.getIndexName());
        options.setTransformerOptions(transformerOptions);
        options.setEmbeddingOptions(embeddingOptions);
        return options;
    }

    /**
     * 云端向量存储 Bean，实现 {@link org.springframework.ai.vectorstore.VectorStore}。
     */
    @Bean
    public DashScopeCloudStore cloudStore(DashScopeApi api, DashScopeStoreOptions storeOptions) {
        return new DashScopeCloudStore(api, storeOptions);
    }

    // ==================== 检索器 ====================

    /**
     * 混合检索配置：稠密检索（语义） + 稀疏检索（关键词） + 查询改写 + 精排。
     */
    @Bean
    public DashScopeDocumentRetrieverOptions retrieverOptions(RagProperties properties) {
        return DashScopeDocumentRetrieverOptions.builder()
                .withIndexName(properties.getIndexName())
                // 稠密检索：语义向量匹配
                .withDenseSimilarityTopK(properties.getDenseTopK())
                // 稀疏检索：关键词 BM25 匹配
                .withSparseSimilarityTopK(properties.getSparseTopK())
                // 查询改写
                .withEnableRewrite(properties.isEnableRewrite())
                .withRewriteModelName(properties.getRewriteModel())
                // 精排 Rerank
                .withEnableReranking(properties.isEnableRerank())
                .withRerankModelName(properties.getRerankModel())
                .withRerankMinScore(properties.getRerankMinScore())
                .withRerankTopN(properties.getRerankTopN())
                .build();
    }

    /**
     * 文档检索器 —— 混合检索的入口。
     *
     * <p>内部执行流程：queryRewrite → denseRetrieval + sparseRetrieval →
     * mergeAndDedup → rerank → topN。一次调用完成全部检索管线。
     */
    @Bean
    public DashScopeDocumentRetriever documentRetriever(
            DashScopeApi api, DashScopeDocumentRetrieverOptions options) {
        return new DashScopeDocumentRetriever(api, options);
    }

    // ==================== ChatClient ====================

    /**
     * RAG 增强的 ChatClient，自动在每次调用时先检索后增强。
     *
     * <p>通过 {@link DashScopeDocumentRetrievalAdvisor} 实现：
     * 每次用户提问 → 检索相关文档 → 拼入 Prompt → 调用 LLM 生成回答。
     */
    @Bean
    public DashScopeDocumentRetrievalAdvisor retrievalAdvisor(
            DashScopeDocumentRetriever retriever) {
        // 使用 Spring AI 标准的 RetrievalAugmentationAdvisor
        return new DashScopeDocumentRetrievalAdvisor(retriever, true);
    }

    // ==================== 检索缓存 ====================

    /**
     * Caffeine 本地缓存，缓存检索结果以提升响应速度和降低 API 调用成本。
     *
     * <p>Key 为规范化后的查询文本，Value 为检索到的文档列表。
     * 过期策略：写入后 TTL 过期（默认 10 分钟），大小上限驱逐。
     */
    @Bean
    public Cache<String, List<Document>> retrievalCache(RagProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(properties.getMaxCacheSize())
                .recordStats()
                .build();
    }
}
