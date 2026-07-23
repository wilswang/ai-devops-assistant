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
| `probe` | `Probe`/`ProbeRegistry`(15 個)/`ProbeRunner`(ProcessBuilder)/`ProbeCollector`；`Os` OS-aware | 純 Java |
| `log` | `LogAnalyzer`(過濾/正規化/分群/stacktrace)、`LogSummary`、`IncidentCatalog`(事件樣態) | 純 Java |
| `agent` | `DiagnosticTools`(@Tool)、`DiagnosticAgent`(ChatClient) | Spring AI |
| `web` | `DiagnosticController`：/api/diagnose、/api/collect、/api/probes | Spring MVC |

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
```

## 目前進度

- **Phase 1/2（完成）**：15 個唯讀 probe、安全層、CLI + REST + dashboard、OS-aware。
- **Phase 3（進行中）**：`LogAnalyzer`（分群 + stacktrace 擷取）→ `LogSummary` →
  已整合為 `analyzeContainerLog` 工具。
- **`IncidentCatalog` 已知事件樣態比對（GREEN，完成）**：`match(ErrorCluster)` 認出
  OOM / CONNECTION_REFUSED / TIMEOUT / TOO_MANY_OPEN_FILES，未知回 null。測試 `IncidentCatalogTest`。

### 剩餘規劃（屬「原本預計內容」，可在 Claude Code 自動推進到此為止）

1. 把 `IncidentCatalog` 接進 `LogSummary`：摘要每群標註命中的事件類型與建議。
2. `LogSummary` / `LogAnalyzer` 加 **top-N 截斷** 與 **WARN 分級**（WARN 納入但與 ERROR 分開統計）。

> 完成上述後，**只剩 `BACKLOG.md` 的項目 → 停下來與使用者討論，不自行開工。**

## 後續規劃（BACKLOG，需先討論）

見 `BACKLOG.md`：本地模型切換(Ollama)、遠端主機(SSH/inventory, Phase 4)、
probe 配置化、log pattern 規格化。
