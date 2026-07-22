# AI DevOps Assistant — 唯讀診斷 agent (Java 21 / Spring Boot + Spring AI)

針對「為什麼這台 server 的 Tomcat 變慢？」情境的**唯讀**自動診斷工具。
目標環境：單機 Linux + Docker 化 Tomcat。

輸入自然語言問題 → agent 選擇並執行唯讀 probe 蒐證 → 輸出
**現象 → 推測 → 證據 → 建議**。

## 技術棧

- **Java 21**（record、text block、switch expression）
- **Spring Boot 3.4**（`spring-boot-starter-web`，同時支援 CLI 與 REST）
- **Spring AI 1.0.0（Anthropic）**：`ChatClient` + `@Tool` 自動 tool-calling 迴圈——
  LLM 的多輪工具呼叫由框架處理，不需手刻 HTTP/JSON。

主機層 probe 為 **OS-aware**：自動偵測 Linux / macOS 用對應指令（見 `Os`），
方便在 macOS 開發機本機測試；docker exec 進 container 的 JVM probe 不受主機 OS 影響。

> 版本提醒：Spring AI 在版本間 API 會變動，本專案以 1.0.0 GA 撰寫；
> 若你本機用不同版本，`@Tool` / `ChatClient.tools(...)` 等 API 可能要微調。

## 核心設計：採集與推理分離

LLM **不會生成任意 shell 指令**。它只能透過唯一的工具 `runProbe`，從
`ProbeRegistry` 裡預先定義好的唯讀 probe 中「選擇要跑哪些」。這在架構層直接消除
「AI 亂執行 kill / rm / restart」的風險——MVP 完全沒有變更/破壞性操作路徑。

```
自然語言問題
   │
   ▼
 ChatClient (Spring AI) ──自動 tool-calling──▶ @Tool runProbe(probeName, ...)
   │                                               │
   │                                     CommandValidator.validate()
   │                                     ← 白名單、禁 metachar、argv 不經 shell
   │                                               │
   │                                     ProcessBuilder 執行（shell=false）
   ◀──────────────── 證據 ─────────────────────────┘
   │
   ▼
現象 / 推測 / 證據 / 建議
```

## 分層（乾淨、可獨立測試）

| 層 | package | 依賴 |
|---|---|---|
| 安全層 | `safety` (`CommandValidator`) | 純 Java，零框架 |
| Probe 核心 | `probe` (`Probe` / `ProbeRegistry` / `ProbeRunner`) | 純 Java，`ProcessBuilder` |
| LLM 層 | `agent` (`DiagnosticTools` / `DiagnosticAgent`) | Spring AI |
| CLI | `DiagnosticAssistantApplication` | Spring Boot |

安全層與 probe 核心不依賴 Spring，可獨立單元測試；Spring AI 只負責 LLM 那一層。

## 安全層（`CommandValidator`）

- **白名單、預設拒絕**：未列出的 binary 一律拒絕。
- **docker 只允許唯讀子命令**（ps / stats / inspect / top / logs / info / version）；
  `docker exec` 內層也只允許 `jps / jstat / jstack / jcmd / jinfo / ps / cat`。
- **禁 shell metacharacter**（`; | & $ \` > < \\`），指令以 `List<String>` argv 經
  `ProcessBuilder` 執行、不經 shell，從源頭杜絕 injection。
- 單元測試 `CommandValidatorTest` 涵蓋：`rm -rf /`、`kill`、`docker stop/rm`、
  `docker exec … rm`、含 `;`/`&&`/`|`/`$()` 的參數皆被擋下。

## Probe 庫（`ProbeRegistry`，15 個）

| 層 | probe |
|---|---|
| system | load / cpu / memory / disk(含 inode) / top_processes / ports / conn_summary |
| docker | ps / stats / inspect_health(含 OOMKilled、mem limit) / top / logs_tail |
| tomcat(JVM) | jvm_procs(取 PID) / gc_stat(jstat) / thread_dump(jstack) |

每個 probe graceful degrade：指令不存在 → `[N/A]`、逾時 → `[TIMEOUT]`、
被安全層擋下 → `[SAFETY BLOCKED]`。

## 建置與執行

需求：JDK 21、Maven。

```bash
# 建置
mvn clean package

# 完整診斷（需 API key）
export ANTHROPIC_API_KEY=sk-...
java -jar target/ai-devops-assistant-0.1.0.jar "為什麼這台 server 的 Tomcat 變慢？" --container tomcat

# 不呼叫 LLM，跑全部 probe 印原始證據（離線 / 無金鑰時用）
java -jar target/ai-devops-assistant-0.1.0.jar --collect-only --container tomcat

# 列出所有 probe
java -jar target/ai-devops-assistant-0.1.0.jar --list-probes

# 只跑安全層測試
mvn test
```

模型可在 `application.yml` 或環境變數調整（預設 `claude-sonnet-4-5`）。

## Web / REST 模式

**不帶任何參數**啟動時，會改跑 REST API + dashboard（帶參數則為純 CLI，不啟 web server）：

```bash
export ANTHROPIC_API_KEY=sk-...
java -jar target/ai-devops-assistant-0.1.0.jar     # 啟動於 http://localhost:8080
```

- Dashboard：瀏覽器開 `http://localhost:8080/`（輸入問題 → AI 診斷 / 純蒐證 / 列 probe）。
- `GET  /api/probes`                — 列出所有唯讀 probe
- `GET  /api/collect?container=X`   — 純蒐證（不呼叫 LLM，回 JSON）
- `POST /api/diagnose`              — 完整 LLM 診斷，body：`{"question":"...","container":"tomcat"}`

```bash
curl -X POST http://localhost:8080/api/diagnose \
  -H 'Content-Type: application/json' \
  -d '{"question":"為什麼 Tomcat 變慢？","container":"tomcat"}'
```

## 演進路線對應

- **Phase 1/2（本 MVP）**：唯讀 AI Terminal + Docker 蒐證，單機。
- **Phase 3 Log Analysis**：擴充 `docker_logs_tail`，加入 time-window + error pattern
  預過濾與 error clustering，再餵 LLM（避免整份 log 灌入）。
- **Phase 4 Multi-Server**：把 `ProbeRunner` 抽象成可透過 SSH / agent 對多台主機
  fan-out；加 inventory 與 health check。Spring Boot 可直接加 REST controller /
  web dashboard。
- **Phase 5 自動修復**：在 `CommandValidator` 擴充三層分類
  （READ_ONLY 自動 / REVERSIBLE 需確認 / DESTRUCTIVE 需確認+二次打字確認+dry-run），
  agent 只「提出 runbook」，每步由人工核准後才執行。
