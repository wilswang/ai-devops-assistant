# CLAUDE.md

給 AI 協作者的專案指南。開始工作前先讀這份、`README.md` 與 `BACKLOG.md`，並跑 `mvn test` 看現況。

## 專案

唯讀 AI DevOps 診斷 agent：針對「為什麼這台 server 的 Tomcat 變慢？」情境，
用自然語言問題 → LLM 選擇唯讀 probe 蒐證 → 輸出「現象 / 推測 / 證據 / 建議」。
目標環境：單機 Linux + Docker 化 Tomcat（開發機為 macOS，probe 為 OS-aware）。

## 技術棧

Java 21、Spring Boot 3.4、Spring AI 1.0.0（Anthropic）。建置需 JDK 21 + Maven。

## 架構與分層（重要）

| package | 職責 | 依賴 |
|---|---|---|
| `safety` | `CommandValidator`：唯讀白名單、禁 shell metachar | 純 Java |
| `config` | `ConfigSource`：外部目錄優先、否則 classpath 內建（供三份 YAML 覆寫） | 純 Java |
| `probe` | `Probe`/`ProbeRegistry`(15 個)/`ProbeRunner`/`ProbeCollector`/`ContainerLister`；`Os` OS-aware；`ProbeConfigLoader` ← `probes.yaml`（載入即過白名單） | 純 Java |
| `log` | `LogAnalyzer`/`LogSummary`/`IncidentCatalog`；`LogFormatLoader` ← `logformat.yaml`、`IncidentCatalogLoader` ← `incidents.yaml` | 純 Java |
| `agent` | `DiagnosticTools`(@Tool)、`DiagnosticAgent`(ChatClient)、`ChatModelSelector`(provider 切換)、`EvidenceReport`(fallback) | Spring AI |
| `web` | `DiagnosticController`：/api/diagnose、/api/collect、/api/probes、/api/containers；`ContainerProvider` | Spring MVC |
| （根）| `StartupConfigValidator`：`@PostConstruct` 開機載入三份配置 → fail-fast；`AppConfig`：依 provider 建 `ChatClient` | Spring |

> 配置化：`incidents.yaml` / `logformat.yaml` / `probes.yaml` 內建於 `resources`，SnakeYAML 載入、
> fail-fast；可由 `CONFIG_DIR` 外部整檔覆蓋，外部來源一樣過白名單驗證。

## 核心不可違反的原則

- **唯讀**：LLM 只能透過 `runProbe` / `analyzeContainerLog` 呼叫**預先定義**的唯讀 probe，
  不能生成任意指令。沒有任何變更/破壞性操作路徑（kill/rm/restart 一律不在白名單）。
- 任何指令（含未來配置來的）都必須過 `CommandValidator` 白名單，不得繞過。
- 純 Java 核心層（safety/probe/log）不依賴 Spring，維持可獨立單元測試。

## 開發流程（TDD）

嚴格 red → green → refactor：

1. **Red**：先寫描述期望行為的測試 + 最小骨架/stub（讓其可編譯），跑 `mvn test` 確認**失敗**。
2. **Green**：最小實作讓測試通過。
3. **Refactor**：測試保護下重構。

**Commit 慣例**：
- 不 commit 紅燈狀態。
- message 用 conventional commits（feat/fix/test/docs/ci…），可加 scope 如 `feat(log):`。

**兩種協作模式**：

- **Cowork（Claude 桌面）**：沙盒無 JDK/Maven，AI 無法自跑 `mvn test`。
  由使用者本機驗證；AI 提供 commit message，**使用者同意後才 commit**；push 由使用者執行。
- **Claude Code（本機 CLI，建議用於本專案的 TDD 迴圈）**：AI 直接在本機跑 `mvn test` 自我閉環。
  流程為 **red → green → `mvn test` 通過 → commit（conventional message）→ push**，一個 TDD 循環一個 commit，
  持續推進。
  **停下點**：做完「原本預計內容」（見下方進度的『剩餘規劃』）後，**接下來屬於 `BACKLOG.md` 的項目，
  一律先停下來與使用者討論，不自行開工。**

## 常用指令

```bash
mvn test                                   # 全部測試
mvn package                                # 打包
java -jar target/ai-devops-assistant-0.1.0.jar --collect-only   # 純蒐證（不需 API key）
java -jar target/ai-devops-assistant-0.1.0.jar                  # 啟動 REST + dashboard (:8080)
# AI 診斷需先 export ANTHROPIC_API_KEY=sk-...

# 環境變數開關：
#   LLM_PROVIDER=anthropic|ollama       切換 LLM provider（預設 anthropic）
#   OLLAMA_MODEL / OLLAMA_BASE_URL      ollama 模型與位址（預設 qwen2.5 / localhost:11434）
#   LLM_TOOL_CALLING=true|false         false 走「蒐證+摘要」fallback（給不支援 tool calling 的本地小模型）
#   CONFIG_DIR=/path/to/dir             外部配置目錄，整檔覆蓋內建 incidents/logformat/probes.yaml
```

## 目前進度

> 最後更新：2026-07-23。全套 65 測試綠、CI 綠（HEAD `062c23e`）。

### 已完成

- **Phase 1/2**：15 個唯讀 probe、安全層白名單、CLI + REST + dashboard、OS-aware。
- **Phase 3（log 分析）**：`LogAnalyzer`（過濾/正規化/分群/stacktrace 擷取）→ `LogSummary`
  → `analyzeContainerLog` 工具。含：
  - `IncidentCatalog` 已知事件比對（OOM / 連線被拒 / timeout / fd 耗盡 / disk full…），
    命中群在 `LogSummary` 標註事件類型 + 說明 + 建議。
  - **top-N 截斷**（`DEFAULT_TOP_N=10`）與 **WARN 分級**（`LogLevel`；`warnLines` 與 ERROR 分開）。
  - **stacktrace 續行納入比對**（`ErrorCluster.detail`，上限 500 字）——修補根因埋在
    `Caused by…` 比對不到的盲點；真實 apollo-portal log 驗證命中 CONNECTION_REFUSED。
- **BACKLOG #1 — LLM provider 可切換**：`app.llm.provider`（anthropic / ollama）由
  `ChatModelSelector` 惰性挑 `ChatModel`。含 **tool-calling fallback**（`app.llm.tool-calling=false`
  時走「先蒐全部證據→模型摘要」`EvidenceReport`，給不支援 function calling 的本地小模型）。
- **BACKLOG #3/#4 — 配置化**（三份內建 YAML，SnakeYAML，fail-fast）：
  - `incidents.yaml` ← `IncidentCatalogLoader`（事件樣態庫）
  - `logformat.yaml` ← `LogFormatLoader`（等級關鍵字 / 時間戳 / stackFrame / exceptionType regex，
    載入時編譯驗證）
  - `probes.yaml` ← `ProbeConfigLoader`（15 probe，OS-aware `commands` + `${container}`/`${pid}`
    佔位，**載入時每條 argv 過 `CommandValidator` 白名單**）
  - `StartupConfigValidator`（`@PostConstruct`）**開機即載入三份配置**——壞配置/壞 regex/
    非白名單指令一律**啟動失敗**（fail-fast 提前到開機，非首次使用）。
- **BACKLOG #5 — 配置外部覆寫**：`ConfigSource` 外部目錄優先、否則 classpath 內建；
  由 `app.config.dir`（env `CONFIG_DIR`）設定，**整檔覆蓋**。外部來源一樣過 fail-fast + 白名單
  （實測外部 `probes.yaml` 含 `rm -rf` → 開機失敗）。
- **dashboard**：container 欄位改 `datalist`，載入時 `GET /api/containers`（走 `docker ps` 唯讀路徑）
  自動偵測執行中容器。
- **CI**：actions 升 node24（checkout@v5 / setup-java@v5）。

### 尚未完成（BACKLOG，需先討論才動工）

- **BACKLOG #2 — 遠端主機 SSH（原 Phase 4）**：抽 `CommandExecutor` 介面（`LocalExecutor` +
  `SshExecutor`）、host inventory 配置、REST/CLI `--host` 參數。**攻擊面最大、工作量最大**；
  動工前先把設計與安全邊界討論清楚。
- **BACKLOG #5 延伸（可選）**：配置逐條 merge（按名覆寫單一 probe / 追加 incident），
  目前僅整檔覆蓋。

> **停下點**：只剩上述 BACKLOG 項目 → 一律先與使用者討論，不自行開工。
