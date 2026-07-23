package com.devops.assistant.agent;

import com.devops.assistant.probe.ProbeResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BACKLOG #1 fallback TDD — EvidenceReport 證據格式化。目前為 stub，此測試預期為紅燈。
 */
class EvidenceReportTest {

    @Test
    void formatsEachProbeWithStatusCommandAndOutput() {
        List<ProbeResult> results = List.of(
                new ProbeResult("system_load", List.of("uptime"),
                        "11:02 up 2 days, load averages: 5.90 6.41 6.42", true),
                new ProbeResult("tomcat_jvm_procs", List.of("docker exec c jps -l"),
                        "jps: executable file not found", false));

        String out = EvidenceReport.format(results);

        // 成功者標 OK、失敗者標 SKIP/ERR
        assertTrue(out.contains("[OK] system_load"), "成功 probe 應標 [OK]，實際:\n" + out);
        assertTrue(out.contains("[SKIP/ERR] tomcat_jvm_procs"), "失敗 probe 應標 [SKIP/ERR]");

        // 呈現實際指令與輸出
        assertTrue(out.contains("uptime"), "應含指令");
        assertTrue(out.contains("load averages"), "應含輸出");

        // system_load 段應排在 tomcat 段之前（保留輸入順序）
        assertTrue(out.indexOf("system_load") < out.indexOf("tomcat_jvm_procs"),
                "應保留輸入順序");
    }

    @Test
    void truncatesLongOutputPerProbe() {
        String huge = "x".repeat(EvidenceReport.MAX_OUTPUT_CHARS_PER_PROBE + 500);
        String out = EvidenceReport.format(List.of(
                new ProbeResult("docker_logs_tail", List.of("docker logs c"), huge, true)));

        assertTrue(out.contains("truncated"), "過長輸出應標示截斷，實際尾段:\n"
                + out.substring(Math.max(0, out.length() - 80)));
        assertFalse(out.contains("x".repeat(EvidenceReport.MAX_OUTPUT_CHARS_PER_PROBE + 1)),
                "截斷後不應保留完整超長輸出");
    }

    @Test
    void handlesBlankOutput() {
        String out = EvidenceReport.format(List.of(
                new ProbeResult("system_ports", List.of("netstat"), "   ", true)));
        assertTrue(out.contains("system_ports"), "空白輸出仍應呈現 probe 段");
        assertTrue(out.contains("無輸出"), "空白輸出應標示（無輸出），實際:\n" + out);
    }
}
