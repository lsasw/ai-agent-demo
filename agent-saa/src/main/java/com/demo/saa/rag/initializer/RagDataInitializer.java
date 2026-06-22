package com.demo.saa.rag.initializer;

import com.demo.saa.rag.service.CustomerServiceRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class RagDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RagDataInitializer.class);
    private final CustomerServiceRagService ragService;

    public RagDataInitializer(CustomerServiceRagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public void run(String... args) {
        if (!ragService.listDocuments().isEmpty()) {
            log.info("KB has {} docs, skip seeding", ragService.listDocuments().size());
            return;
        }
        log.info("Seeding Claude Code knowledge base...");

        seed("Claude Code Overview", "overview",
            "Claude Code is Anthropic terminal AI coding agent. "
            + "Unlike IDE plugins, it autonomously understands entire codebases, "
            + "plans multi-step changes, edits files across projects, runs tests, "
            + "and iterates until tasks complete. "
            + "Available on: Terminal CLI, VS Code, JetBrains, Desktop, Web. "
            + "Core: multi-file editing, shell execution, agentic loop "
            + "(plan-execute-verify-iterate), Git integration, MCP, Hooks, Skills, "
            + "Worktree parallelism, Agent Teams. Requires Claude subscription.");

        seed("Claude Code Installation", "install",
            "macOS/Linux/WSL: curl -fsSL https://claude.ai/install.sh | bash. "
            + "Windows PowerShell: irm https://claude.ai/install.ps1 | iex. "
            + "Homebrew: brew install --cask claude-code. "
            + "WinGet: winget install Anthropic.ClaudeCode. "
            + "After install run 'claude' in project dir. First run prompts login. "
            + "Windows: install Git for Windows for Bash tool. "
            + "Native install auto-updates. Homebrew/WinGet need manual upgrade. "
            + "CLI: claude, claude -p, claude -c, claude update. "
            + "Session commands: /clear, /help, /exit, /model.");

        seed("Claude Code How It Works", "concept",
            "Agentic Loop with 3 phases: "
            + "1) Gather Context: project files, git state, CLAUDE.md, auto memory. "
            + "2) Take Action via 5 tool categories: File Ops, Search, Execute, "
            + "Network, Code Intelligence. "
            + "3) Verify Results: checks each step, auto-retries on failure. "
            + "Models: Sonnet (most coding), Opus (complex reasoning), "
            + "Haiku (fast), Fable 5 (highest tier). "
            + "Extensions: Skills, MCP, Hooks, Subagents, Plugins.");

        seed("Claude Code CLI Reference", "reference",
            "CLI: claude (interactive), claude -p (query+exit), claude -c (continue), "
            + "claude -r name (resume), claude update, claude install [ver], "
            + "claude agents (monitor). Pipes: cat logs | claude -p 'analyze'. "
            + "Models: fable-5, opus-4-8 (default), sonnet-4-6, haiku-4-5. "
            + "Pricing: Fast Mode 2x rate 2.5x speed. "
            + "Advanced: dynamic workflows, fallbackModel, --safe-mode, /cd.");

        seed("Claude Code Common Workflows", "workflow",
            "1) Understand: cd project && claude, ask 'explain architecture'. "
            + "2) Fix bugs: claude 'fix failing test in UserService'. "
            + "3) Refactor: /plan to review before executing. "
            + "4) Write tests: claude 'add unit tests for OrderController'. "
            + "5) Create PR: claude 'create PR for this branch'. "
            + "6) CI/CD: claude -p 'check security vulnerabilities'. "
            + "7) Parallel: git worktree add for concurrent tasks. "
            + "8) Subagents: delegate research to keep context clean. "
            + "Best: use CLAUDE.md, /plan first, split complex tasks.");

        log.info("Seeding done: {} docs", ragService.listDocuments().size());
    }

    private void seed(String title, String category, String content) {
        try {
            CustomerServiceRagService.DocumentMeta meta =
                    ragService.uploadDocument(content, title, category);
            log.info("  Indexed: [{}] {} ({} chars, {} chunks)",
                    category, title, meta.getCharCount(), meta.getChunkCount());
        } catch (Exception e) {
            log.error("  Failed: [{}] {} - {}", category, title, e.getMessage());
        }
    }
}
