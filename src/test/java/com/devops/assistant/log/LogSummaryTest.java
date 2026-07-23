package com.devops.assistant.log;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 TDD — LogSummary 精簡摘要格式。目前為 stub，此測試預期為紅燈。
 */
class LogSummaryTest {

    @Test
    void formatsClustersWithCountsAndExceptionType() {
        LogAnalysis analysis = new LogAnalysis(
                List.of(
                        new ErrorCluster("Failed to process request",
                                "…ERROR Failed to process request", 3, "java.lang.NullPointerException"),
                        new ErrorCluster("Disk full on <IP>",
                                "…ERROR Disk full on 10.0.0.9", 1, "")
                ), 120, 4);

        String out = LogSummary.format(analysis);

        // 摘要行：總行數與 ERROR 行數
        assertTrue(out.contains("120"), "應含總行數 120，實際:\n" + out);
        assertTrue(out.contains("ERROR 4"), "應含 ERROR 4，實際:\n" + out);

        // 每群呈現次數
        assertTrue(out.contains("×3"), "應含 ×3");
        assertTrue(out.contains("×1"), "應含 ×1");

        // 有 exception 類型者顯示類型；否則顯示 signature
        assertTrue(out.contains("java.lang.NullPointerException"), "應含 exception 類型");
        assertTrue(out.contains("Failed to process request"), "應含 signature");
        assertTrue(out.contains("Disk full on <IP>"), "無 exception 者應顯示 signature");

        // 依 count 由多到少呈現：NPE 群在磁碟群之前
        assertTrue(out.indexOf("NullPointerException") < out.indexOf("Disk full"),
                "count 高者應排前面");
    }

    @Test
    void handlesNoErrors() {
        String out = LogSummary.format(new LogAnalysis(List.of(), 50, 0));
        assertTrue(out.contains("ERROR 0"), "無錯誤時應顯示 ERROR 0，實際:\n" + out);
    }
}
