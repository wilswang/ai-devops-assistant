package com.devops.assistant.log;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置化階段 A TDD — IncidentCatalogLoader YAML 解析。目前為 stub，此測試預期為紅燈。
 */
class IncidentCatalogLoaderTest {

    private static final String YAML = """
            incidents:
              - id: OOM
                description: "記憶體不足（OutOfMemoryError / OOMKilled）"
                suggestion: "檢查 heap 用量與 -Xmx"
                keywords: ["OutOfMemoryError", "OOMKilled", "out of memory"]
              - id: CONNECTION_REFUSED
                description: "連線被拒"
                suggestion: "確認目標服務/埠是否存活"
                keywords: ["connection refused"]
            """;

    @Test
    void parsesIncidentsInOrder() {
        List<IncidentRule> rules = IncidentCatalogLoader.parse(YAML);

        assertEquals(2, rules.size(), "應解析出 2 條規則");
        KnownEvent oom = rules.get(0).event();
        assertEquals("OOM", oom.id());
        assertEquals("記憶體不足（OutOfMemoryError / OOMKilled）", oom.description());
        assertEquals("檢查 heap 用量與 -Xmx", oom.suggestion());
        assertEquals("CONNECTION_REFUSED", rules.get(1).event().id());
    }

    @Test
    void lowercasesKeywords() {
        List<IncidentRule> rules = IncidentCatalogLoader.parse(YAML);
        assertTrue(rules.get(0).keywords().contains("outofmemoryerror"),
                "keyword 應轉小寫，實際: " + rules.get(0).keywords());
        assertTrue(rules.get(0).keywords().contains("oomkilled"), "keyword 應轉小寫");
    }

    @Test
    void failsFastOnMissingRequiredField() {
        String bad = """
                incidents:
                  - id: OOM
                    description: "缺 suggestion 與 keywords"
                """;
        assertThrows(IllegalStateException.class, () -> IncidentCatalogLoader.parse(bad),
                "缺必填欄位應 fail-fast");
    }

    @Test
    void failsFastOnEmptyOrMissingIncidentsKey() {
        assertThrows(IllegalStateException.class, () -> IncidentCatalogLoader.parse(""),
                "空 YAML 應 fail-fast");
        assertThrows(IllegalStateException.class,
                () -> IncidentCatalogLoader.parse("something_else: 1"),
                "缺 incidents 節點應 fail-fast");
    }
}
