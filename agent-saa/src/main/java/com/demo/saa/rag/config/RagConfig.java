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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * RAG Bean 配置 + Pipeline 自动初始化。
 *
 * @author dmw
 * @since 2026-06-22
 */
@Configuration
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    /** Pipeline 是否就绪（初始化成功后置 true，失败时跳过后续入库操作） */
    private volatile boolean pipelineReady = false;

    /**
     * 确保 DashScope 云端 Pipeline 就绪。通过 upsertPipeline 自动创建 + 3 次重试应对后端初始化延迟。
     */

    @Bean
    public DashScopeApi dashScopeApi(
            @Value("${spring.ai.dashscope.api-key:${AI_DASHSCOPE_API_KEY:${DASHSCOPE_API_KEY:}}}") String apiKey) {
        return DashScopeApi.builder().apiKey(apiKey).build();
    }

    /**
     * 确保 DashScope 云端 Pipeline 就绪。
     *
     * <p>先查询已有 Pipeline；若不存在则通过 upsertPipeline 自动创建。
     * 新 Pipeline 后端初始化有延迟，通过 3 次重试（间隔 3s）应对。
     */
    @Bean
    public String pipelineId(DashScopeApi api, RagProperties properties, DashScopeStoreOptions storeOptions) {
        String name = properties.getIndexName();
        // 1. 先查询是否已存在
        try {
            String id = api.getPipelineIdByName(name);
            if (id != null && !id.isBlank()) {
                pipelineReady = true;
                log.info("Pipeline exists | name: {} | id: {}", name, id);
                return name;
            }
        } catch (Exception e) {
            log.debug("Pipeline query failed, will create: {}", e.getMessage());
        }
        // 2. 不存在则通过 upsertPipeline 创建（含重试）
        log.info("Pipeline not found, creating via upsertPipeline: {}", name);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                api.upsertPipeline(List.of(), storeOptions);
                pipelineReady = true;
                log.info("Pipeline created | name: {} | attempt: {}", name, attempt);
                return name;
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("DocEmptyError") && attempt < 3) {
                    log.warn("Pipeline initializing (attempt {}/3), waiting 3s...", attempt);
                    try { Thread.sleep(3000); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    log.warn("Pipeline create failed attempt {}. Manual: https://dashscope.console.aliyun.com/rag", attempt);
                    break;
                }
            }
        }
        return name;
    }

    public boolean isPipelineReady() { return pipelineReady; }

    @Bean
    public DashScopeEmbeddingModel embeddingModel(DashScopeApi api) { return new DashScopeEmbeddingModel(api); }

    @Bean
    public DashScopeEmbeddingOptions embeddingOptions() {
        return DashScopeEmbeddingOptions.builder().model("text-embedding-v2").textType("document").build();
    }

    @Bean
    public DashScopeDocumentTransformerOptions transformerOptions(RagProperties properties) {
        return DashScopeDocumentTransformerOptions.builder()
                .withChunkSize(properties.getChunkSize())
                .withOverlapSize(properties.getOverlapSize())
                .withLanguage("zh").build();
    }

    @Bean
    public DashScopeStoreOptions storeOptions(RagProperties properties,
            DashScopeDocumentTransformerOptions transformerOptions, DashScopeEmbeddingOptions embeddingOptions) {
        DashScopeStoreOptions options = new DashScopeStoreOptions(properties.getIndexName());
        options.setTransformerOptions(transformerOptions);
        options.setEmbeddingOptions(embeddingOptions);
        return options;
    }

    @Bean
    public DashScopeCloudStore cloudStore(DashScopeApi api, DashScopeStoreOptions storeOptions) {
        return new DashScopeCloudStore(api, storeOptions);
    }

    @Bean
    public DashScopeDocumentRetrieverOptions retrieverOptions(RagProperties p) {
        return DashScopeDocumentRetrieverOptions.builder()
                .withIndexName(p.getIndexName())
                .withDenseSimilarityTopK(p.getDenseTopK())
                .withSparseSimilarityTopK(p.getSparseTopK())
                .withEnableRewrite(p.isEnableRewrite())
                .withRewriteModelName(p.getRewriteModel())
                .withEnableReranking(p.isEnableRerank())
                .withRerankModelName(p.getRerankModel())
                .withRerankMinScore(p.getRerankMinScore())
                .withRerankTopN(p.getRerankTopN()).build();
    }

    @Bean
    public DashScopeDocumentRetriever documentRetriever(DashScopeApi api, DashScopeDocumentRetrieverOptions opts) {
        return new DashScopeDocumentRetriever(api, opts);
    }

    @Bean
    public DashScopeDocumentRetrievalAdvisor retrievalAdvisor(DashScopeDocumentRetriever retriever) {
        return new DashScopeDocumentRetrievalAdvisor(retriever, true);
    }

    @Bean
    public Cache<String, List<Document>> retrievalCache(RagProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(properties.getMaxCacheSize()).recordStats().build();
    }
}
