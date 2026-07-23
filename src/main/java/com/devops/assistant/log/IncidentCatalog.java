package com.devops.assistant.log;

import java.util.List;

/**
 * 已知事件樣態庫：把錯誤群集比對到已知的 incident 類型（OOM、連線被拒、timeout…）。
 *
 * <p>樣態由 {@code incidents.yaml}（classpath 內建預設）讀入，採 fail-fast
 * （缺檔或格式錯誤即報錯）。為了讓 fail-fast 發生在<b>應用啟動時</b>（而非第一次用到才爆），
 * 由啟動時的 {@code StartupConfigValidator} 顯式呼叫 {@link #load()}；未顯式載入時
 * {@link #match} 會惰性補載。未來 BACKLOG #5 可由外部檔覆寫。
 */
public final class IncidentCatalog {

    /** 內建預設配置資源路徑。 */
    static final String DEFAULT_RESOURCE = "incidents.yaml";

    /** 已載入的規則（依序比對，順序即優先序）；null 表示尚未載入。 */
    private static volatile List<IncidentRule> rules;

    private IncidentCatalog() {
    }

    /** 顯式載入/重載配置（fail-fast）。由啟動流程呼叫以達成開機即驗證。 */
    public static void load() {
        rules = IncidentCatalogLoader.load(DEFAULT_RESOURCE);
    }

    private static List<IncidentRule> rules() {
        if (rules == null) {
            load();  // 未經啟動流程預載時（如純單元測試）惰性補載
        }
        return rules;
    }

    /**
     * 比對一個錯誤群集是否命中已知事件；未命中回 null。
     * 比對範圍涵蓋 signature + exceptionType + sample + detail——
     * detail 讓埋在 stacktrace（Caused by…）裡的根因關鍵字也能被認出。
     */
    public static KnownEvent match(ErrorCluster cluster) {
        if (cluster == null) {
            return null;
        }
        String text = (cluster.signature() + " " + cluster.exceptionType() + " "
                + cluster.sample() + " " + cluster.detail())
                .toLowerCase();
        for (IncidentRule rule : rules()) {
            for (String keyword : rule.keywords()) {
                if (text.contains(keyword)) {
                    return rule.event();
                }
            }
        }
        return null;
    }
}
