package com.devops.assistant.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BACKLOG #5 TDD — ConfigSource 外部優先解析。目前為 stub，此測試預期為紅燈。
 */
class ConfigSourceTest {

    @Test
    void externalFileOverridesClasspath(@TempDir Path dir) throws Exception {
        // 外部目錄放一份自訂 incidents.yaml → 應用它，而非內建
        Files.writeString(dir.resolve("incidents.yaml"), "incidents: [custom-external]");
        assertEquals("incidents: [custom-external]",
                ConfigSource.read(dir, "incidents.yaml"),
                "外部檔存在時應覆蓋內建");
    }

    @Test
    void fallsBackToClasspathWhenExternalMissing(@TempDir Path dir) {
        // 外部目錄沒有該檔 → 退回 classpath 內建（真的存在於 resources）
        String content = ConfigSource.read(dir, "incidents.yaml");
        assertTrue(content.contains("incidents:"), "應退回 classpath 內建 incidents.yaml");
    }

    @Test
    void nullDirUsesClasspath() {
        String content = ConfigSource.read((Path) null, "logformat.yaml");
        assertTrue(content.contains("logFormat"), "外部目錄為 null 應直接用 classpath");
    }

    @Test
    void failsWhenNeitherExternalNorClasspathHasIt(@TempDir Path dir) {
        assertThrows(IllegalStateException.class,
                () -> ConfigSource.read(dir, "does-not-exist.yaml"),
                "外部與內建皆無應 fail-fast");
    }
}
