package com.devops.assistant.agent;

import com.devops.assistant.probe.Probe;
import com.devops.assistant.probe.ProbeRegistry;
import com.devops.assistant.probe.ProbeResult;
import com.devops.assistant.probe.ProbeRunner;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 暴露給 LLM 的工具。只有一個工具 {@code runProbe}，且只能執行 {@link ProbeRegistry}
 * 裡預先定義好的唯讀 probe——LLM 無法生成任意指令，也沒有任何變更/破壞性操作路徑。
 *
 * <p>每次呼叫仍會再過一次安全驗證（於 {@link ProbeRunner} 內），雙重保險。
 */
@Component
public class DiagnosticTools {

    private static final int MAX_OUTPUT_CHARS = 6000;

    /** CLI 指定的預設 container；LLM 未帶 container 參數時使用。 */
    private volatile String defaultContainer = "tomcat";

    public void setDefaultContainer(String container) {
        if (container != null && !container.isBlank()) {
            this.defaultContainer = container;
        }
    }

    @Tool(description = """
            執行一個唯讀診斷 probe 並回傳其輸出。只能執行預先定義好的唯讀 probe，
            無法執行任何其他指令。要跑 tomcat_gc_stat / tomcat_thread_dump 前，
            先用 tomcat_jvm_procs 取得 JVM 的 PID，再把該 PID 當作 pid 參數傳入。
            可用的 probe 名稱請見系統提示中的清單。""")
    public String runProbe(
            @ToolParam(description = "要執行的 probe 名稱，例如 system_load、docker_stats")
            String probeName,
            @ToolParam(required = false, description = "目標 container 名稱（docker/tomcat 類 probe 需要）")
            String container,
            @ToolParam(required = false, description = "JVM PID（tomcat_gc_stat / tomcat_thread_dump 需要）")
            String pid) {

        Probe probe = ProbeRegistry.get(probeName);
        if (probe == null) {
            return "[ERROR] 未知 probe: " + probeName;
        }

        Map<String, String> params = new HashMap<>();
        params.put("container", (container == null || container.isBlank()) ? defaultContainer : container);
        params.put("pid", (pid == null || pid.isBlank()) ? "1" : pid);

        ProbeResult result = ProbeRunner.run(probe, params);
        String out = result.output();
        if (out.length() > MAX_OUTPUT_CHARS) {
            out = out.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]";
        }
        return "$ " + String.join("; ", result.commands()) + "\n"
                + (out.isBlank() ? "(無輸出)" : out);
    }
}
