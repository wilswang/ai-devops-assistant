package com.devops.assistant.log;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void truncatesToTopNClustersAndNotesTheRest() {
        List<ErrorCluster> many = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            many.add(new ErrorCluster("sig-" + i, "sample-" + i, 8 - i, ""));
        }
        LogAnalysis analysis = new LogAnalysis(many, 300, 40, 0);

        String out = LogSummary.format(analysis, 3);

        // 只列出前 3 群
        assertTrue(out.contains("sig-0"), "應列出第 1 群，實際:\n" + out);
        assertTrue(out.contains("sig-2"), "應列出第 3 群");
        assertFalse(out.contains("sig-3"), "超出 top-N 的群不應列出");

        // 標示尚有 5 群未列出
        assertTrue(out.contains("還有 5 群"), "應標示還有 5 群未列出，實際:\n" + out);
    }

    @Test
    void doesNotAppendTruncationNoteWhenWithinTopN() {
        LogAnalysis analysis = new LogAnalysis(
                List.of(new ErrorCluster("only one", "sample", 1, "")), 10, 1, 0);
        String out = LogSummary.format(analysis, 3);
        assertFalse(out.contains("未列出"), "群數未超過 top-N 時不應出現截斷提示，實際:\n" + out);
    }

    @Test
    void reportsWarnCountSeparatelyFromError() {
        String out = LogSummary.format(new LogAnalysis(List.of(), 100, 3, 5));
        assertTrue(out.contains("ERROR 3"), "應含 ERROR 3，實際:\n" + out);
        assertTrue(out.contains("WARN 5"), "WARN 應與 ERROR 分開呈現，實際:\n" + out);
    }

    @Test
    void annotatesClustersWithKnownIncidentAndSuggestion() {
        LogAnalysis analysis = new LogAnalysis(
                List.of(
                        new ErrorCluster("Handler dispatch failed",
                                "…ERROR java.lang.OutOfMemoryError: Java heap space",
                                3, "java.lang.OutOfMemoryError"),
                        new ErrorCluster("Some business rule violated",
                                "…ERROR business rule X", 1, "")
                ), 200, 4);

        String out = LogSummary.format(analysis);

        // 命中的群應標註已知事件代號與建議
        assertTrue(out.contains("OOM"), "命中群應標註事件代號 OOM，實際:\n" + out);
        assertTrue(out.contains("檢查 heap 用量"), "命中群應附上建議，實際:\n" + out);

        // 未命中的群不應被硬塞事件代號（該行只呈現 signature）
        int ruleIdx = out.indexOf("Some business rule violated");
        assertTrue(ruleIdx >= 0, "未命中群仍應呈現 signature");
    }
}
