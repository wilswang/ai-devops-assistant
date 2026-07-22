package com.devops.assistant.agent;

import com.devops.assistant.probe.Probe;
import com.devops.assistant.probe.ProbeRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 診斷 agent：組裝系統提示 + 工具，交給 Spring AI 的 ChatClient。
 * Spring AI 會自動處理多輪 tool-calling 迴圈（呼叫 runProbe → 回填結果 → 續問），
 * 我們不需手刻 HTTP/JSON 迴圈。
 */
@Service
public class DiagnosticAgent {

    private final ChatClient chatClient;
    private final DiagnosticTools tools;

    public DiagnosticAgent(ChatClient chatClient, DiagnosticTools tools) {
        this.chatClient = chatClient;
        this.tools = tools;
    }

    public String diagnose(String question, String container) {
        tools.setDefaultContainer(container);
        String userMessage = "目標 container 名稱：" + container + "\n\n問題：" + question;

        return chatClient.prompt()
                .system(systemPrompt())
                .user(userMessage)
                .tools(tools)
                .call()
                .content();
    }

    private String systemPrompt() {
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
                - 蒐證足夠後停止呼叫工具，用繁體中文輸出最終診斷報告。

                最終報告必須嚴格用以下四段結構（Markdown）：
                ## 現象
                （觀察到的具體症狀，引用數據）
                ## 推測
                （最可能的根因，可列 1~3 個並標註信心）
                ## 證據
                （支持推測的實際 probe 輸出片段，標明來自哪個 probe）
                ## 建議
                （下一步排查或修復方向。若涉及變更操作，明確標註「此為變更操作，需人工確認後執行」，
                你自己不執行）

                可用的 probe 清單：
                """ + probeCatalog();
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
