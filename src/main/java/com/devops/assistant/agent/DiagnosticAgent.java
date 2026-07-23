package com.devops.assistant.agent;

import com.devops.assistant.probe.Probe;
import com.devops.assistant.probe.ProbeCollector;
import com.devops.assistant.probe.ProbeRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 診斷 agent：組裝系統提示 + 工具，交給 Spring AI 的 ChatClient。
 *
 * <p>兩種策略（由 {@code app.llm.tool-calling} 切換）：
 * <ul>
 *   <li><b>tool-calling（預設）</b>：掛上工具，Spring AI 自動處理多輪 tool-calling 迴圈
 *       （呼叫 runProbe → 回填結果 → 續問），模型自行選擇要跑哪些 probe。</li>
 *   <li><b>collect-summarize（fallback）</b>：本地小模型常不支援 function calling，
 *       改為先蒐集全部唯讀證據、塞進 prompt，讓模型直接據此產出報告，不走工具迴圈。</li>
 * </ul>
 */
@Service
public class DiagnosticAgent {

    private final ChatClient chatClient;
    private final DiagnosticTools tools;
    private final boolean toolCalling;

    public DiagnosticAgent(ChatClient chatClient, DiagnosticTools tools,
                           @Value("${app.llm.tool-calling:true}") boolean toolCalling) {
        this.chatClient = chatClient;
        this.tools = tools;
        this.toolCalling = toolCalling;
    }

    public String diagnose(String question, String container) {
        tools.setDefaultContainer(container);
        return toolCalling
                ? diagnoseWithTools(question, container)
                : diagnoseWithCollectedEvidence(question, container);
    }

    /** tool-calling 路徑：模型自行呼叫 runProbe / analyzeContainerLog 蒐證。 */
    private String diagnoseWithTools(String question, String container) {
        String userMessage = "目標 container 名稱：" + container + "\n\n問題：" + question;
        return chatClient.prompt()
                .system(toolCallingSystemPrompt())
                .user(userMessage)
                .tools(tools)
                .call()
                .content();
    }

    /** fallback 路徑：先蒐全部唯讀證據，交給模型摘要診斷，不掛工具。 */
    private String diagnoseWithCollectedEvidence(String question, String container) {
        String evidence = EvidenceReport.format(ProbeCollector.collectAll(container));
        String userMessage = """
                目標 container 名稱：%s

                問題：%s

                以下是已「預先採集」的唯讀診斷證據，請只根據這些內容做診斷（不需、也無法再執行任何指令）：

                %s""".formatted(container, question, evidence);
        return chatClient.prompt()
                .system(fallbackSystemPrompt())
                .user(userMessage)
                .call()
                .content();
    }

    private String toolCallingSystemPrompt() {
        return """
                你是一個唯讀的 Linux/Docker/Tomcat 診斷助理。

                目標：針對使用者描述的問題，選擇並執行「唯讀」診斷 probe 蒐集證據，
                然後根據實際採集到的輸出做出診斷。

                規則：
                - 你只能透過 runProbe 工具執行預先定義好的唯讀 probe，無法執行任何其他指令。
                - 你不會、也無法執行任何變更操作（kill / restart / rm / docker stop 等）。
                - 先跑主機層概觀（system_load / system_cpu / system_memory / system_disk），
                  再視情況深入 docker 與 tomcat 層。
                - 要跑 tomcat_gc_stat / tomcat_thread_dump，先用 tomcat_jvm_procs 取得 JVM PID，
                  再把該 PID 當作 pid 參數傳入。
                - 要看 log／找錯誤時，優先用 analyzeContainerLog 工具（它會過濾雜訊、將相似錯誤
                  分群並擷取 exception 類型），而不是直接讀原始 log。
                - 蒐證足夠後停止呼叫工具，用繁體中文輸出最終診斷報告。

                """ + reportFormatInstructions() + "\n可用的 probe 清單：\n" + probeCatalog();
    }

    private String fallbackSystemPrompt() {
        return """
                你是一個唯讀的 Linux/Docker/Tomcat 診斷助理。

                目標：根據使用者訊息中「已預先採集」的唯讀 probe 證據，做出診斷。

                規則：
                - 所有證據都已附在使用者訊息中，你不需也無法執行任何指令或工具。
                - 只根據提供的證據推論；資料不足時明說「證據不足」，不要臆造數據。
                - 標為 [SKIP/ERR] 的 probe 代表該環境取不到（例如容器內沒有 JDK 工具），
                  可據此判斷但不要當成錯誤根因。
                - 用繁體中文輸出最終診斷報告。

                """ + reportFormatInstructions();
    }

    private String reportFormatInstructions() {
        return """
                最終報告必須嚴格用以下四段結構（Markdown）：
                ## 現象
                （觀察到的具體症狀，引用數據）
                ## 推測
                （最可能的根因，可列 1~3 個並標註信心）
                ## 證據
                （支持推測的實際 probe 輸出片段，標明來自哪個 probe）
                ## 建議
                （下一步排查或修復方向。若涉及變更操作，明確標註「此為變更操作，需人工確認後執行」，
                你自己不執行）""";
    }

    private String probeCatalog() {
        StringBuilder sb = new StringBuilder();
        for (Probe p : ProbeRegistry.all().values()) {
            sb.append("- ").append(p.name())
                    .append(" [").append(p.category()).append("]");
            if (p.needsContainer()) {
                sb.append(" (需 container)");
            }
            if (!p.requiredParams().isEmpty()) {
                sb.append(" (需 params: ").append(String.join(", ", p.requiredParams())).append(")");
            }
            sb.append(": ").append(p.description()).append("\n");
        }
        return sb.toString();
    }
}
