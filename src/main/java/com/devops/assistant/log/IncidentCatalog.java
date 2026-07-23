package com.devops.assistant.log;

import java.util.List;

/**
 * 已知事件樣態庫：把錯誤群集比對到已知的 incident 類型（OOM、連線被拒、timeout…）。
 *
 * <p>樣態由 {@code incidents.yaml}（classpath 內建預設）於類別載入時讀入，
 * 採 fail-fast（缺檔或格式錯誤即啟動失敗）。未來 BACKLOG #5 可由外部檔覆寫。
 */
public final class IncidentCatalog {

    /** 內建預設配置資源路徑。 */
    static final String DEFAULT_RESOURCE = "incidents.yaml";

    /** 依序比對；命中即回傳（順序即優先序）。keywords 皆以小寫比對。 */
    private static final List<IncidentRule> RULES =
            IncidentCatalogLoader.loadFromClasspath(DEFAULT_RESOURCE);

    private IncidentCatalog() {
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
        for (IncidentRule rule : RULES) {
            for (String keyword : rule.keywords()) {
                if (text.contains(keyword)) {
                    return rule.event();
                }
            }
        }
        return null;
    }
}
