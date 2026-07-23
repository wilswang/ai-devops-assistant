package com.devops.assistant.log;

import java.util.List;

/**
 * 已知事件樣態庫：把錯誤群集比對到已知的 incident 類型（OOM、連線被拒、timeout…）。
 *
 * <p>目前樣態內建於程式碼；未來可依 BACKLOG #4 externalize 成配置。
 */
public final class IncidentCatalog {

    private record Rule(KnownEvent event, List<String> keywords) {
    }

    /** 依序比對；命中即回傳（順序即優先序）。keywords 皆以小寫比對。 */
    private static final List<Rule> RULES = List.of(
            new Rule(new KnownEvent("OOM",
                    "記憶體不足（OutOfMemoryError / OOMKilled）",
                    "檢查 heap 用量與 -Xmx、容器記憶體上限；可用 tomcat_gc_stat、docker_inspect_health 佐證"),
                    List.of("outofmemoryerror", "oomkilled", "out of memory")),
            new Rule(new KnownEvent("CONNECTION_REFUSED",
                    "連線被拒（Connection refused）",
                    "確認目標服務/埠是否存活、網路與防火牆；可用 system_ports 佐證"),
                    List.of("connection refused")),
            new Rule(new KnownEvent("TIMEOUT",
                    "逾時（timeout）",
                    "檢查下游延遲、執行緒阻塞（tomcat_thread_dump）、連線池設定"),
                    List.of("timeout", "timed out")),
            new Rule(new KnownEvent("TOO_MANY_OPEN_FILES",
                    "檔案描述元耗盡（too many open files）",
                    "檢查 fd / ulimit 與資源（連線、檔案）洩漏"),
                    List.of("too many open files"))
    );

    private IncidentCatalog() {
    }

    /** 比對一個錯誤群集是否命中已知事件；未命中回 null。比對 signature + exceptionType + sample。 */
    public static KnownEvent match(ErrorCluster cluster) {
        if (cluster == null) {
            return null;
        }
        String text = (cluster.signature() + " " + cluster.exceptionType() + " " + cluster.sample())
                .toLowerCase();
        for (Rule rule : RULES) {
            for (String keyword : rule.keywords()) {
                if (text.contains(keyword)) {
                    return rule.event();
                }
            }
        }
        return null;
    }
}
