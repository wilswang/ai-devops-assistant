package com.devops.assistant.probe;

import java.util.List;
import java.util.Map;

/**
 * 列出目前執行中的容器名稱，供 dashboard 的容器欄位自動填入下拉建議。
 *
 * <p>走既有唯讀路徑：{@code docker ps --format {{.Names}}} 經 {@code CommandValidator}
 * 白名單 + {@code ProbeRunner}（不經 shell）執行。此為 UI 便利功能，<b>刻意不註冊到
 * {@link ProbeRegistry}</b>——LLM 無法呼叫、也不列入蒐證目錄。
 *
 * <p>{@link #parseNames(String)} 為純函式，可獨立單元測試。
 */
public final class ContainerLister {

    private ContainerLister() {
    }

    /** 把 {@code docker ps --format {{.Names}}} 的輸出解析成名稱清單（每行一個，去空白/空行/錯誤標記）。 */
    public static List<String> parseNames(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return List.of();
        }
        return rawOutput.lines()
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.startsWith("["))  // 濾掉 [N/A]/[SAFETY BLOCKED] 等降級標記
                .toList();
    }

    /** 執行 docker ps 取得執行中的容器名稱；docker 不可用或失敗時回空清單（graceful degrade）。 */
    public static List<String> listRunning() {
        Probe probe = Probe.of("docker_ps_names", "docker",
                "列出執行中的容器名稱（dashboard UI 用）", false,
                p -> List.of(List.of("docker", "ps", "--format", "{{.Names}}")));
        ProbeResult result = ProbeRunner.run(probe, Map.of());
        return result.ok() ? parseNames(result.output()) : List.of();
    }
}
