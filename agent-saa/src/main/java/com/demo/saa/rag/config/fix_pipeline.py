import os, sys
path = r"E:/AI/ai-agent-demo/agent-saa/src/main/java/com/demo/saa/rag/config/RagConfig.java"
with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()
new_block = [
    "    /**
",
    "     * Ensure DashScope Pipeline is ready.
",
    "     * Query first; if not found, create via upsertPipeline with 3 retries.
",
    "     */
",
    "    @Bean
",
    "    public String pipelineId(DashScopeApi api, RagProperties properties, DashScopeStoreOptions storeOptions) {
",
    "        String name = properties.getIndexName();
",
    "        try {
",
    "            String id = api.getPipelineIdByName(name);
",
    "            if (id != null && !id.isBlank()) { pipelineReady = true; log.info("Pipeline exists: {}", name); return name; }
",
    "        } catch (Exception e) { log.debug("Pipeline query failed: {}", e.getMessage()); }
",
    "        log.info("Creating pipeline: {}", name);
",
    "        for (int a = 1; a <= 3; a++) {
",
    "            try { api.upsertPipeline(List.of(), storeOptions); pipelineReady = true; log.info("Pipeline created, attempt: {}", a); return name; }
",
    "            catch (Exception e) {
",
    "                String msg = e.getMessage() != null ? e.getMessage() : "";
",
    "                if (msg.contains("DocEmptyError") && a < 3) {
",
    "                    log.warn("Pipeline init retry {}/3, wait 3s", a);
",
    "                    try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
",
    "                } else { log.warn("Pipeline create failed (attempt {}): {}", a, msg.length() > 200 ? msg.substring(0,200) : msg); break; }
",
    "            }
",
    "        }
",
    "        return name;
",
    "    }
",
    "
",
    "    public boolean isPipelineReady() { return pipelineReady; }
",
    "
",
]
new_lines = lines[:48] + new_block + lines[64:]
with open(path, "w", encoding="utf-8") as f:
    f.writelines(new_lines)
print("RagConfig updated")
