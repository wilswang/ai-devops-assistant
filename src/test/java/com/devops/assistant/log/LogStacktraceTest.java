package com.devops.assistant.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 3 TDD — 多行 stacktrace 處理。
 * ERROR 之後跟隨的 exception 行與 {@code at ...} 行屬於同一錯誤事件，
 * 不應計為獨立錯誤；並應擷取 exception 類型。目前為 stub，此測試預期為紅燈。
 */
class LogStacktraceTest {

    private final LogAnalyzer analyzer = new LogAnalyzer();

    @Test
    void attachesStacktraceAndCapturesExceptionType() {
        String log = """
                2026-07-22 10:00:01 ERROR Failed to process request
                java.lang.NullPointerException: Cannot invoke "String.length()"
                	at com.foo.Service.handle(Service.java:42)
                	at com.foo.Controller.doGet(Controller.java:17)
                2026-07-22 10:00:02 INFO  Recovered
                2026-07-22 10:00:03 ERROR Failed to process request
                java.lang.NullPointerException: Cannot invoke "String.length()"
                	at com.foo.Service.handle(Service.java:42)
                """;

        LogAnalysis result = analyzer.analyze(log);

        // stacktrace 的 exception 行與 at 行不計為獨立錯誤：只有 2 個 ERROR 事件
        assertEquals(2, result.errorLines(), "stacktrace 行不應被計為獨立錯誤");

        // 兩個相同錯誤合併成一群
        assertEquals(1, result.clusters().size(), "相同錯誤事件應合併為一群");

        ErrorCluster top = result.clusters().get(0);
        assertEquals(2, top.count(), "此群 count 應為 2");
        assertEquals("java.lang.NullPointerException", top.exceptionType(),
                "應從 stacktrace 擷取 exception 類型");
    }
}
