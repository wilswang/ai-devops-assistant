package com.devops.assistant;

import com.devops.assistant.config.ConfigSource;
import com.devops.assistant.log.IncidentCatalog;
import com.devops.assistant.log.LogFormatLoader;
import com.devops.assistant.probe.ProbeRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 應用啟動時載入/驗證所有外部配置，達成<b>開機即 fail-fast</b>：
 * 壞掉的配置（缺欄位、格式錯誤、非白名單指令）會讓應用<b>啟動失敗</b>，而不是等到第一次用到才爆。
 *
 * <p>{@link PostConstruct} 於 Spring context 初始化時執行（早於 CommandLineRunner），
 * 因此所有執行模式（web / --collect-only / --list-probes / 診斷）都會先過這關。
 *
 * <p>若設定 {@code app.config.dir}（BACKLOG #5），先指向該外部目錄再載入——同名檔整檔覆蓋
 * 內建預設，外部內容一樣過 fail-fast + 白名單驗證。
 */
@Component
public class StartupConfigValidator {

    private final String configDir;

    public StartupConfigValidator(@Value("${app.config.dir:}") String configDir) {
        this.configDir = configDir;
    }

    @PostConstruct
    public void validate() {
        if (configDir != null && !configDir.isBlank()) {
            ConfigSource.setExternalDir(Path.of(configDir.trim()));  // #5 外部覆寫
        }
        IncidentCatalog.load();        // #4 incidents.yaml
        LogFormatLoader.loadDefault(); // #4 logformat.yaml（regex 於此編譯驗證）
        ProbeRegistry.load();          // #3 probes.yaml（每條指令於此過白名單驗證）
    }
}
