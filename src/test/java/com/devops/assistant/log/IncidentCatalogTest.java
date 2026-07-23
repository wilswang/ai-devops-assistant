package com.devops.assistant.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BACKLOG #4 TDD — 已知事件樣態比對。目前 IncidentCatalog 為 stub，此測試預期為紅燈。
 */
class IncidentCatalogTest {

    @Test
    void matchesOutOfMemory() {
        ErrorCluster c = new ErrorCluster(
                "Handler dispatch failed",
                "ERROR ... java.lang.OutOfMemoryError: Java heap space",
                3, "java.lang.OutOfMemoryError");

        KnownEvent event = IncidentCatalog.match(c);

        assertNotNull(event, "OutOfMemoryError 應命中已知事件");
        assertEquals("OOM", event.id());
    }

    @Test
    void matchesConnectionRefused() {
        ErrorCluster c = new ErrorCluster(
                "Failed to connect to <IP>:<N>",
                "ERROR ... java.net.ConnectException: Connection refused",
                2, "java.net.ConnectException");

        KnownEvent event = IncidentCatalog.match(c);

        assertNotNull(event, "Connection refused 應命中已知事件");
        assertEquals("CONNECTION_REFUSED", event.id());
    }

    @Test
    void returnsNullForUnknown() {
        ErrorCluster c = new ErrorCluster(
                "Some business rule violated", "ERROR ... business rule X", 1, "");

        assertNull(IncidentCatalog.match(c), "未知錯誤不應命中任何事件");
    }
}
