package com.demo.saa.rag.service;

import com.demo.saa.rag.config.RagConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Claude Code 官方文档全量爬虫服务。
 *
 * <p>从 code.claude.com/docs/zh-CN/ 爬取中文文档，
 * 提取正文后通过 {@link CustomerServiceRagService} 批量入库 DashScope。
 *
 * @author dmw
 * @since 2026-06-23
 */
@Service
public class DocumentCrawlerService {

    private static final Logger log = LoggerFactory.getLogger(DocumentCrawlerService.class);
    private static final String BASE_URL = "https://code.claude.com/docs/zh-CN";
    private static final long REQUEST_INTERVAL_MS = 1200;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final CustomerServiceRagService ragService;
    private final RagConfig ragConfig;

    public DocumentCrawlerService(CustomerServiceRagService ragService, RagConfig ragConfig) {
        this.ragService = ragService;
        this.ragConfig = ragConfig;
    }

    /** 精选 35 篇核心中文文档 */
    public static final List<DocPage> CORE_PAGES = List.of(
        page("overview","概述","getting-started"),
        page("quickstart","快速开始","getting-started"),
        page("how-claude-code-works","Claude Code 如何工作","getting-started"),
        page("common-workflows","常见工作流","getting-started"),
        page("best-practices","最佳实践","getting-started"),
        page("interactive-mode","交互模式","getting-started"),
        page("terminal-config","终端配置","getting-started"),
        page("setup","安装设置","setup"),
        page("authentication","身份认证","setup"),
        page("troubleshoot-install","安装故障排除","setup"),
        page("desktop","桌面应用","setup"),
        page("desktop-quickstart","桌面应用快速开始","setup"),
        page("vs-code","VS Code 集成","ide"),
        page("jetbrains","JetBrains 集成","ide"),
        page("cli-reference","CLI 参考","cli"),
        page("commands","命令一览","cli"),
        page("keybindings","快捷键","cli"),
        page("sessions","会话管理","cli"),
        page("env-vars","环境变量","cli"),
        page("settings","配置选项","cli"),
        page("permissions","权限系统","cli"),
        page("permission-modes","权限模式","cli"),
        page("memory","CLAUDE.md 项目记忆","extend"),
        page("skills","Skills 技能","extend"),
        page("mcp","MCP 协议","extend"),
        page("mcp-quickstart","MCP 快速入门","extend"),
        page("hooks","Hooks 钩子","extend"),
        page("hooks-guide","Hooks 钩子指南","extend"),
        page("plugins","插件系统","extend"),
        page("sub-agents","Subagents 子代理","extend"),
        page("worktrees","Git Worktree 并行","extend"),
        page("channels","Channels 频道","extend"),
        page("security","安全性","advanced"),
        page("sandboxing","沙箱机制","advanced"),
        page("headless","无头模式/CI","advanced"),
        page("troubleshooting","常见问题排查","advanced"),
        page("context-window","上下文窗口管理","advanced"),
        page("costs","费用与成本","advanced"),
        page("model-config","模型配置","advanced"),
        page("prompt-caching","提示缓存","advanced")
    );

    public Map<String, Object> crawlAll() {
        if (!ragConfig.isPipelineReady()) {
            return Map.of("error", "DashScope Pipeline not ready");
        }
        AtomicInteger ok = new AtomicInteger(0);
        AtomicInteger fail = new AtomicInteger(0);
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());
        long start = System.currentTimeMillis();

        log.info("Start crawl: {} pages", CORE_PAGES.size());
        for (int i = 0; i < CORE_PAGES.size(); i++) {
            DocPage p = CORE_PAGES.get(i);
            log.info("[{}/{}] {}", i + 1, CORE_PAGES.size(), p.title);
            try { Thread.sleep(REQUEST_INTERVAL_MS); } catch (InterruptedException e) { break; }
            try {
                String text = fetchAndExtract(p.path);
                if (text == null || text.isBlank()) {
                    results.add(Map.of("path", p.path, "title", p.title, "status", "skipped"));
                    fail.incrementAndGet();
                    continue;
                }
                var meta = ragService.uploadDocument(text, p.title, p.category);
                results.add(Map.of("path", p.path, "title", p.title, "status", "indexed",
                        "chars", meta.getCharCount(), "chunks", meta.getChunkCount()));
                ok.incrementAndGet();
            } catch (Exception e) {
                log.error("  Error: {} - {}", p.title, e.getMessage());
                results.add(Map.of("path", p.path, "title", p.title, "status", "error", "error", e.getMessage()));
                fail.incrementAndGet();
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("Crawl done: {}ms ok={} fail={}", elapsed, ok.get(), fail.get());
        return Map.of("total", CORE_PAGES.size(), "success", ok.get(), "failed", fail.get(),
                "elapsedMs", elapsed, "details", results);
    }

    public String fetchAndExtract(String path) throws IOException {
        String url = BASE_URL + "/" + path;
        Document doc = Jsoup.connect(url)
                .timeout((int) TIMEOUT.toMillis())
                .userAgent("Mozilla/5.0 (compatible; AIDemoCrawler/1.0)")
                .get();
        Elements content = doc.select("main");
        if (content.isEmpty()) content = doc.select("article");
        if (content.isEmpty()) content = doc.select(".content, .markdown, .prose");
        if (content.isEmpty()) {
            doc.select("nav, footer, header, script, style, noscript").remove();
            content = doc.select("body");
        }
        String text = content.text().replaceAll("\\s{3,}", "\n\n").trim();
        return text.length() >= 100 ? text : null;
    }

    private static DocPage page(String p, String t, String c) { return new DocPage(p, t, c); }
    public record DocPage(String path, String title, String category) {}
}
