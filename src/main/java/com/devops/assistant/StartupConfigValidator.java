package com.devops.assistant;

import com.devops.assistant.log.IncidentCatalog;
import com.devops.assistant.log.LogFormatLoader;
import com.devops.assistant.probe.ProbeRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 應用啟動時載入/驗證所有外部配置，達成<b>開機即 fail-fast</b>：
 * 壞掉的配置（缺欄位、格式錯誤）會讓應用<b>啟動失敗</b>，而不是等到第一次用到才爆。
 *
 * <p>{@link PostConstruct} 於 Spring context 初始化時執行（早於 CommandLineRunner），
 * 因此所有執行模式（web / --collect-only / --list-probes / 診斷）都會先過這關。
 * 配置化階段 B/C 完成後，logformat / probes 的載入也在此一併觸發。
 */
@Component
public class StartupConfigValidator {

    @PostConstruct
    public void validate() {
        IncidentCatalog.load();       // #4 incidents.yaml
        LogFormatLoader.loadDefault(); // #4 logformat.yaml（regex 於此編譯驗證）
        ProbeRegistry.load();         // #3 probes.yaml（每條指令於此過白名單驗證）
    }
}
