package com.devops.assistant.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置來源解析：<b>外部目錄優先，否則退回 classpath 內建預設</b>（BACKLOG #5）。
 *
 * <p>外部目錄由啟動流程設定（{@code app.config.dir}）。同名檔存在就整檔覆蓋內建預設；
 * 不存在就用 classpath。外部與內建讀進來的內容,後續一律走各 loader 既有的 fail-fast +
 * （probe 的）白名單驗證——不因「來自外部」而放行。純 Java，與 Spring 無關。
 */
public final class ConfigSource {

    /** 外部配置目錄；null 表示只用 classpath 內建預設。 */
    private static volatile Path externalDir;

    private ConfigSource() {
    }

    /** 設定外部配置目錄（由啟動流程呼叫）；null / 空白視為未設定。 */
    public static void setExternalDir(Path dir) {
        externalDir = dir;
    }

    /** 讀取指定資源的 YAML 內容（外部優先，否則 classpath）。 */
    public static String read(String resource) {
        return read(externalDir, resource);
    }

    /** 可測試核心：給定外部目錄（可為 null）與資源名，回傳內容字串。 */
    static String read(Path dir, String resource) {
        if (dir != null) {
            Path file = dir.resolve(resource);
            if (Files.isRegularFile(file)) {
                try {
                    return Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("讀取外部配置失敗：" + file, e);
                }
            }
        }
        try (InputStream in = ConfigSource.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("找不到配置資源（外部與內建皆無）：" + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("讀取內建配置失敗：" + resource, e);
        }
    }
}
