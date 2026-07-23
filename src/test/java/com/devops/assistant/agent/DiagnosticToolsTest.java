package com.devops.assistant.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** DiagnosticTools 單元測試：LLM 只能選預定義 probe，未知名稱安全回錯。 */
class DiagnosticToolsTest {

    private final DiagnosticTools tools = new DiagnosticTools();

    @Test
    void unknownProbeReturnsError() {
        String out = tools.runProbe("definitely_not_a_probe", null, null);
        assertTrue(out.contains("[ERROR]"), "未知 probe 應回錯，實際: " + out);
        assertTrue(out.contains("未知 probe"));
    }

    @Test
    void knownProbeExecutes() {
        // system_load = uptime，跨平台存在
        String out = tools.runProbe("system_load", null, null);
        assertTrue(out.startsWith("$ "), "應回傳指令與輸出，實際: " + out);
        assertTrue(out.contains("uptime"));
    }

    @Test
    void defaultContainerIsApplied() {
        tools.setDefaultContainer("my-tomcat");
        // docker_stats 會把 container 名稱帶進指令；docker 未必存在，但指令行應含設定的名稱
        String out = tools.runProbe("docker_stats", null, null);
        assertTrue(out.contains("my-tomcat"), "指令應帶入預設 container 名稱，實際: " + out);
    }

    @Test
    void explicitContainerOverridesDefault() {
        tools.setDefaultContainer("my-tomcat");
        String out = tools.runProbe("docker_stats", "other-ctr", null);
        assertTrue(out.contains("other-ctr"), "顯式 container 應覆寫預設，實際: " + out);
    }

    @Test
    void analyzeContainerLogDegradesGracefullyWhenDockerUnavailable() {
        // 測試環境通常無此 container；工具不應拋例外，而是回傳可讀的 probe 輸出
        String out = tools.analyzeContainerLog("no-such-container");
        assertTrue(out != null && !out.isBlank(), "應回傳非空字串，實際: " + out);
        assertTrue(out.contains("docker logs") || out.contains("Log 摘要"),
                "應為原始探針輸出或 log 摘要，實際: " + out);
    }
}
