package com.devops.assistant.log;

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

    private LogSummary() {
    }

    /** 產生精簡摘要：一行總覽 + 依 count 排序的群集（有 exception 類型者優先顯示類型）。 */
    public static String format(LogAnalysis analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("Log 摘要：總行數 ").append(analysis.totalLines())
                .append("、ERROR ").append(analysis.errorLines())
                .append(" 行、錯誤群集 ").append(analysis.clusters().size()).append(" 個");

        for (ErrorCluster c : analysis.clusters()) {
            String label = c.exceptionType().isEmpty()
                    ? c.signature()
                    : c.exceptionType() + " — " + c.signature();
            sb.append("\n[×").append(c.count()).append("] ").append(label);
        }
        return sb.toString();
    }
}
