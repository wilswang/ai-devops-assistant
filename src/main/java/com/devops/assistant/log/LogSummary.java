package com.devops.assistant.log;

import java.util.List;

/**
 * 把 {@link LogAnalysis} 格式化成精簡摘要字串，供 LLM 消化（取代整份原始 log）。
 *
 * <p>範例輸出：
 * <pre>
 * Log 摘要：總行數 120、ERROR 4 行、錯誤群集 2 個
 * [×3] java.lang.NullPointerException — Failed to process request
 * [×1] Disk full on &lt;IP&gt;
 * </pre>
 */
public final class LogSummary {

    /** 預設最多列出的群集數；超出者以一行提示帶過，避免摘要本身又過長。 */
    public static final int DEFAULT_TOP_N = 10;

    private LogSummary() {
    }

    /** 以預設 top-N（{@link #DEFAULT_TOP_N}）產生精簡摘要。 */
    public static String format(LogAnalysis analysis) {
        return format(analysis, DEFAULT_TOP_N);
    }

    /**
     * 產生精簡摘要：一行總覽 + 依排序的前 {@code topN} 群集
     * （ERROR 群優先、有 exception 類型者顯示類型、命中已知事件者附建議）。
     * 群數超過 {@code topN} 時，末行提示尚有多少群未列出。
     */
    public static String format(LogAnalysis analysis, int topN) {
        StringBuilder sb = new StringBuilder();
        sb.append("Log 摘要：總行數 ").append(analysis.totalLines())
                .append("、ERROR ").append(analysis.errorLines())
                .append(" 行、WARN ").append(analysis.warnLines())
                .append(" 行、錯誤群集 ").append(analysis.clusters().size()).append(" 個");

        List<ErrorCluster> clusters = analysis.clusters();
        int shown = Math.min(topN, clusters.size());
        for (ErrorCluster c : clusters.subList(0, shown)) {
            String label = c.exceptionType().isEmpty()
                    ? c.signature()
                    : c.exceptionType() + " — " + c.signature();
            sb.append("\n[×").append(c.count()).append("] ").append(label);

            KnownEvent event = IncidentCatalog.match(c);
            if (event != null) {
                sb.append("\n    ⮑ 已知事件 ").append(event.id())
                        .append("：").append(event.description())
                        .append("\n      建議：").append(event.suggestion());
            }
        }

        int rest = clusters.size() - shown;
        if (rest > 0) {
            sb.append("\n…還有 ").append(rest).append(" 群未列出（依重要性截斷）");
        }
        return sb.toString();
    }
}
