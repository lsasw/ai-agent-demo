package com.demo.saa.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG（检索增强生成）系统的可配置参数。
 *
 * <p>在 application.yml 中以 {@code rag} 为前缀覆盖默认值。
 *
 * @author dmw
 * @since 2026-06-22
 */
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    /** DashScope 知识库索引名称（Pipeline 名称），对应云端存储的命名空间 */
    private String indexName = "cc-customer-service";

    // ─────────────── 混合检索参数 ───────────────

    /** 稠密检索（语义向量相似度）返回的最大条数 */
    private int denseTopK = 5;

    /** 稀疏检索（关键词 BM25）返回的最大条数 */
    private int sparseTopK = 3;

    /** 是否启用查询改写（将口语化问题改写为规范查询） */
    private boolean enableRewrite = true;

    /** 查询改写的模型名称 */
    private String rewriteModel = "qwen-plus";

    /** 是否启用精排（Rerank），对合并后的候选文档重新打分排序 */
    private boolean enableRerank = true;

    /** 精排模型名称 */
    private String rerankModel = "gte-rerank";

    /** 精排最低分数阈值，低于此分的文档直接丢弃 */
    private float rerankMinScore = 0.35f;

    /** 精排后保留的最大文档条数 */
    private int rerankTopN = 3;

    // ─────────────── 文档切片参数 ───────────────

    /** 标准切片大小（字符数） */
    private int chunkSize = 800;

    /** 相邻切片之间的重叠字符数 */
    private int overlapSize = 100;

    /** 细粒度切片大小，用于短问题精准匹配 */
    private int fineChunkSize = 400;

    /** 粗粒度切片大小，用于长问题上下文覆盖 */
    private int coarseChunkSize = 1200;

    // ─────────────── 查询增强参数 ───────────────

    /** 是否启用查询扩展（对短问题生成同义变体，多路并行检索） */
    private boolean enableQueryExpansion = true;

    // ─────────────── 缓存参数 ───────────────

    /** 检索结果缓存有效期（分钟） */
    private int cacheTtlMinutes = 10;

    /** 检索结果缓存最大条目数 */
    private int maxCacheSize = 1000;

    // ─────────────── 连接参数 ───────────────

    /** DashScope API 连接超时（秒） */
    private int connectTimeoutSeconds = 5;

    /** DashScope API 读取超时（秒） */
    private int readTimeoutSeconds = 30;

    // ─────────────── 系统 Prompt ───────────────

    /** 客服助手的系统角色提示词 */
    private String systemPrompt = """
            你是 Claude Code 智能客服助手，专门解答用户关于 Claude Code 终端 AI 编程工具的问题。
            
            ## 回答规则
            1. 仅根据下方【参考文档】中的内容回答问题，不要编造文档中没有的信息。
            2. 如果【参考文档】中没有相关信息，请明确告知用户"当前知识库暂未收录该内容，建议查阅 Claude Code 官方文档"。
            3. 回答时标注信息来源，格式为「——参考：《文档标题》」。
            4. 回答结构清晰：先给出直接答案，再补充相关细节。
            5. 涉及命令行、代码示例时，使用代码块展示并注明操作系统。
            6. 回答风格专业但友好，每段不超过 3 句话。
            """;

    // ─────────────── getters / setters ───────────────

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public int getDenseTopK() { return denseTopK; }
    public void setDenseTopK(int denseTopK) { this.denseTopK = denseTopK; }

    public int getSparseTopK() { return sparseTopK; }
    public void setSparseTopK(int sparseTopK) { this.sparseTopK = sparseTopK; }

    public boolean isEnableRewrite() { return enableRewrite; }
    public void setEnableRewrite(boolean enableRewrite) { this.enableRewrite = enableRewrite; }

    public String getRewriteModel() { return rewriteModel; }
    public void setRewriteModel(String rewriteModel) { this.rewriteModel = rewriteModel; }

    public boolean isEnableRerank() { return enableRerank; }
    public void setEnableRerank(boolean enableRerank) { this.enableRerank = enableRerank; }

    public String getRerankModel() { return rerankModel; }
    public void setRerankModel(String rerankModel) { this.rerankModel = rerankModel; }

    public float getRerankMinScore() { return rerankMinScore; }
    public void setRerankMinScore(float rerankMinScore) { this.rerankMinScore = rerankMinScore; }

    public int getRerankTopN() { return rerankTopN; }
    public void setRerankTopN(int rerankTopN) { this.rerankTopN = rerankTopN; }

    public int getChunkSize() { return chunkSize; }
    public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }

    public int getOverlapSize() { return overlapSize; }
    public void setOverlapSize(int overlapSize) { this.overlapSize = overlapSize; }

    public int getFineChunkSize() { return fineChunkSize; }
    public void setFineChunkSize(int fineChunkSize) { this.fineChunkSize = fineChunkSize; }

    public int getCoarseChunkSize() { return coarseChunkSize; }
    public void setCoarseChunkSize(int coarseChunkSize) { this.coarseChunkSize = coarseChunkSize; }

    public boolean isEnableQueryExpansion() { return enableQueryExpansion; }
    public void setEnableQueryExpansion(boolean enableQueryExpansion) { this.enableQueryExpansion = enableQueryExpansion; }

    public int getCacheTtlMinutes() { return cacheTtlMinutes; }
    public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }

    public int getMaxCacheSize() { return maxCacheSize; }
    public void setMaxCacheSize(int maxCacheSize) { this.maxCacheSize = maxCacheSize; }

    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }

    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
}
