package com.devops.assistant.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3 TDD — LogAnalyzer 行為定義。
 * 目前 analyzer 為 stub，此測試預期為紅燈。
 */
class LogAnalyzerTest {

    private final LogAnalyzer analyzer = new LogAnalyzer();

    @Test
    void clustersSimilarErrorsAndIgnoresInfo() {
        String log = """
                2026-07-22 10:00:00 INFO  Starting service
                2026-07-22 10:00:01 ERROR Connection timeout to db host=10.0.0.1
                2026-07-22 10:00:05 INFO  Handled request
                2026-07-22 10:00:06 ERROR Connection timeout to db host=10.0.0.2
                2026-07-22 10:00:10 ERROR NullPointerException at com.foo.Bar
                """;

        LogAnalysis result = analyzer.analyze(log);

        // 只計入 3 行 ERROR（INFO 被濾掉）
        assertEquals(3, result.errorLines(), "應只計入 3 行 ERROR");

        // 兩個 host 不同的 timeout 正規化後應合併成一群；NPE 另一群
        assertEquals(2, result.clusters().size(), "timeout 合併為一群、NPE 一群，共 2 群");

        // 依 count 由多到少排序，最大群為 timeout（count=2）
        ErrorCluster top = result.clusters().get(0);
        assertEquals(2, top.count(), "最大群應為 timeout，count=2");
        assertTrue(top.signature().toLowerCase().contains("timeout"),
                "最大群 signature 應含 timeout，實際: " + top.signature());
    }
}
