# Backlog / 待辦功能

依「先讓核心穩、再逐步外部化配置」的原則排序。共同主軸：把寫死的東西
（模型、機器、probe、log pattern）逐步變成**可配置**，同時安全防線（唯讀白名單）不繞過。

---

## 1. LLM 可切換本地模型 ✅（完成）

**目標**：除了 Anthropic，能切換到本地模型（Ollama：Llama / Qwen 等），離線或降低成本時使用。

**做法**
- 加 `spring-ai-starter-model-ollama`，用 Spring profile 或設定（如 `app.llm.provider=anthropic|ollama`）決定注入哪個 `ChatModel`。
- `ChatClient` 本身 provider 無關，`DiagnosticAgent` 幾乎不用改。

**已完成**（commit：ChatModelSelector + AppConfig 切換）
- `spring-ai-starter-model-ollama` 已加；`app.llm.provider`（預設 anthropic）用 `ChatModelSelector`
  惰性挑選 `ChatModel` 建立 `ChatClient`；未知 provider 於啟動即快速失敗並列出可用清單。
- 設定：`spring.ai.ollama`（`OLLAMA_BASE_URL` / `OLLAMA_MODEL`，預設 `qwen2.5`，`pull-model-strategy=never`）。
- `DiagnosticAgent` 未改。實測 anthropic/ollama 兩路 bean 皆可建立（ollama 未啟動不需網路）。

**tool-calling fallback（完成）**
- 由 `app.llm.tool-calling`（預設 true，或 `LLM_TOOL_CALLING`）切換。true 走原工具迴圈；
  false 走 fallback：`ProbeCollector.collectAll` 先蒐全部唯讀證據 → `EvidenceReport` 格式化
  （每 probe 上限 2000 字）→ 不掛 tools 讓模型直接產四段報告。給不支援 function calling 的
  本地小模型用。挑到支援 function calling 的模型（qwen2.5、llama3.1）則維持 true 即可。

---

## 2. 查詢指定 / 遠端機器（不限部署那台）

**目標**：對 inventory 中任一台主機/容器做診斷，而非只有本機。（原路線圖 Phase 4）

**做法**
- 把 `ProbeRunner` 的「執行」抽成介面 `CommandExecutor`：`LocalExecutor`（現況）+ `SshExecutor`（sshj 或 Apache MINA SSHD）。
- 新增 host inventory 配置（YAML：host / port / user / 認證 / 標籤）。
- probe 指令不變，只換傳輸層；REST/CLI 增加 `--host` / `host` 參數。

**風險 / 注意**
- 認證與金鑰管理、連線逾時、並發 fan-out、錯誤隔離。攻擊面變大，需嚴格限制可連的主機清單。

---

## 3. Probe 改為配置驅動（不寫死在程式碼）

**目標**：probe 定義從 `ProbeRegistry` 的 Java 程式碼，移到外部 YAML，方便新增/調整而不改碼。

**做法**
- 定義 probe schema（name / category / description / 各 OS 指令 argv / 需要的參數）。
- 啟動時載入成 `Probe`；OS-aware 分支也放進配置。

**風險 / 注意**
- **安全紅線**：配置來的指令仍必須過 `CommandValidator` 白名單，不得因為「來自配置」就放行。這反而讓「配置（要跑什麼）」與「白名單（准不准跑）」的分離更清楚。

**進行中**：配置化採漸進落地（先內建於 resources 的預設 YAML、fail-fast、SnakeYAML）。
階段 A（IncidentCatalog → `incidents.yaml`）→ B（LogAnalyzer pattern → `logformat.yaml`）
→ C（ProbeRegistry → `probes.yaml`）。

---

## 5. 配置外部覆寫（不重編即可調整）

**目標**：#3/#4 的配置除了內建預設，還能被「外部檔案」覆寫，讓使用者不用重新編譯就能
新增/調整 probe、log pattern、incident 樣態。

**做法**
- 內建預設 YAML 於 classpath；啟動時若指定的外部路徑（如 `--config-dir` 或屬性）存在則 override/merge。
- 外部來源與內建同樣走 fail-fast + `CommandValidator` 驗證，不因「來自外部」而放行。

**風險 / 注意**
- 外部檔可信度：仍須過白名單與 load-time 驗證。合併策略（覆蓋 vs 疊加）要明確定義。

---

## 4. 定義 log 內容規格與 pattern 庫

**目標**：把目前寫死在 `LogAnalyzer` 的 pattern（ERROR 等級、時間戳、exception regex）變成可定義的「log 格式規格」，並建立「已知事件樣態庫」。

**做法**
- Log 格式規格（可配置）：時間戳格式、等級關鍵字、續行/stacktrace 判定規則。
- 已知事件樣態庫：timeout、OOMKilled、connection refused、too many open files… 每種一個 pattern + 說明 + 建議，命中即標記。
- 讓分析結果能對應到「事件類型」，提高 LLM 診斷的準確度與一致性。

**風險 / 注意**
- 不同框架/容器 log 格式差異大；規格要能覆蓋多種來源。與第 3 點同源（config-driven），可一起設計配置檔結構。

---

## 目前狀態（已完成）

- Phase 1/2：唯讀診斷 agent（15 probe）、安全層白名單、CLI + REST + dashboard、OS-aware（Linux/macOS）。
- Phase 3（完成）：`LogAnalyzer` 過濾/正規化/分群/stacktrace 擷取 → `LogSummary` 精簡摘要 →
  整合為 `analyzeContainerLog` 工具。含 `IncidentCatalog` 已知事件比對（並涵蓋 stacktrace 續行）、
  top-N 截斷、WARN 分級。
- BACKLOG #1（核心完成）：LLM provider 可切換（anthropic / ollama），見上。
