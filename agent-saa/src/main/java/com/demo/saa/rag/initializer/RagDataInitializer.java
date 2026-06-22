package com.demo.saa.rag.initializer;

import com.demo.saa.rag.config.RagConfig;
import com.demo.saa.rag.service.CustomerServiceRagService;
import com.demo.saa.rag.service.DocumentCrawlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class RagDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RagDataInitializer.class);
    private final CustomerServiceRagService ragService;
    private final RagConfig ragConfig;

    public RagDataInitializer(CustomerServiceRagService ragService, RagConfig ragConfig) {
        this.ragService = ragService;
        this.ragConfig = ragConfig;
    }

    @Override
    public void run(String... args) {
        if (!ragConfig.isPipelineReady()) {
            log.warn("DashScope Pipeline 未就绪，跳过知识库初始化。");
            return;
        }

        if (ragService.listDocuments().isEmpty()) {
            log.info("知识库为空。");
            log.info("请调 POST /api/rag/docs/crawl 全量爬取 Claude Code 中文文档（35 篇）入库。");
        } else {
            log.info("知识库已有 {} 篇文档，可调 POST /api/rag/docs/crawl 增量爬取。",
                    ragService.listDocuments().size());
        }
    }
}
