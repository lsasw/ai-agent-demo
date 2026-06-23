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

@Configuration
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);
    private volatile boolean pipelineReady = false;

    @Bean
    public DashScopeApi dashScopeApi(
            @Value("${spring.ai.dashscope.api-key:${AI_DASHSCOPE_API_KEY:${DASHSCOPE_API_KEY:}}}") String apiKey) {
        return DashScopeApi.builder().apiKey(apiKey).build();
    }

    @Bean
    public String pipelineId(RagProperties properties) {
        pipelineReady = true;
        return properties.getIndexName();
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
    public DashScopeStoreOptions storeOptions(RagProperties properties) {
        return new DashScopeStoreOptions(properties.getIndexName());
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
