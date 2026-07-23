package com.devops.assistant.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void capturesStacktraceDetailSoBuriedKeywordsCanMatchIncidents() {
        // 常見真實情境：ERROR header 與最上層例外都不含關鍵字，
        // 真正根因（Connection refused）埋在 Caused by 續行。
        String log = """
                2026-07-22 10:00:01 ERROR Get admin server address from meta server failed
                org.springframework.web.client.ResourceAccessException: I/O error on GET
                	at org.apache.http.SomeClient.execute(SomeClient.java:100)
                	at com.foo.Locator.locate(Locator.java:20)
                Caused by: java.net.ConnectException: Connection refused (Connection refused)
                """;

        LogAnalysis result = analyzer.analyze(log);
        ErrorCluster c = result.clusters().get(0);

        // detail 應吸附續行中的關鍵字（但不含純 stack frame 的雜訊）
        assertTrue(c.detail().contains("Connection refused"),
                "detail 應保留 Caused by 續行的關鍵字，實際: " + c.detail());

        // 進而讓 IncidentCatalog 能命中——修補「關鍵字埋在 stacktrace 內」的比對盲點
        KnownEvent event = IncidentCatalog.match(c);
        assertNotNull(event, "埋在 stacktrace 的 Connection refused 應命中已知事件");
        assertEquals("CONNECTION_REFUSED", event.id());
    }
}
