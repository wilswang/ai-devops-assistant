package com.devops.assistant.log;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置化階段 B TDD — LogFormatLoader YAML 解析與 regex 編譯。目前為 stub，此測試預期為紅燈。
 */
class LogFormatLoaderTest {

    private static final String YAML = """
            logFormat:
              levels:
                ERROR: ["ERROR", "SEVERE"]
                WARN: ["WARN"]
              timestampHeader: '^\\d{4}-\\d{2}-\\d{2}[ T]'
              stackFrame: '^at\\s'
              exceptionType: '((?:[\\w$]+\\.)+[\\w$]*(?:Exception|Error|Throwable))'
            """;

    @Test
    void parsesLevelsInOrderAndCompilesPatterns() {
        LogFormat fmt = LogFormatLoader.parse(YAML);

        // 等級順序即優先序：ERROR 先於 WARN
        assertEquals(2, fmt.levels().size());
        assertEquals(LogLevel.ERROR, fmt.levels().get(0).level());
        assertEquals(LogLevel.WARN, fmt.levels().get(1).level());

        // ERROR 的 pattern 應涵蓋多關鍵字（ERROR / SEVERE）並具字界
        assertTrue(fmt.levels().get(0).pattern().matcher("10:00 SEVERE boom").find(),
                "ERROR 等級應含 SEVERE 關鍵字");

        // 時間戳 header / exception 擷取
        assertTrue(fmt.timestampHeader().matcher("2026-07-22 10:00:01 ERROR x").find(),
                "timestampHeader 應匹配日期開頭");
        var m = fmt.exceptionType().matcher("java.lang.NullPointerException: boom");
        assertTrue(m.find(), "exceptionType 應匹配");
        assertEquals("java.lang.NullPointerException", m.group(1), "應擷取完整類名");
    }

    @Test
    void failsFastOnBadRegex() {
        String bad = """
                logFormat:
                  levels:
                    ERROR: ["ERROR"]
                  timestampHeader: '([unclosed'
                  stackFrame: '^at\\s'
                  exceptionType: '(x)'
                """;
        assertThrows(IllegalStateException.class, () -> LogFormatLoader.parse(bad),
                "regex 編譯失敗應 fail-fast");
    }

    @Test
    void failsFastOnUnknownLevelName() {
        String bad = """
                logFormat:
                  levels:
                    TRACE: ["TRACE"]
                  timestampHeader: '^x'
                  stackFrame: '^at\\s'
                  exceptionType: '(x)'
                """;
        assertThrows(IllegalStateException.class, () -> LogFormatLoader.parse(bad),
                "未知等級名（非 ERROR/WARN）應 fail-fast");
    }

    @Test
    void failsFastOnMissingField() {
        String bad = """
                logFormat:
                  levels:
                    ERROR: ["ERROR"]
                  timestampHeader: '^x'
                """;
        assertThrows(IllegalStateException.class, () -> LogFormatLoader.parse(bad),
                "缺 stackFrame / exceptionType 應 fail-fast");
    }
}
