package com.devops.assistant.log;

import java.util.List;

/**
 * log 分析結果：精簡後的錯誤群集，供 LLM 消化（避免整份 log 灌入）。
 *
 * @param clusters   錯誤群集，ERROR 群優先、群內依 count 由多到少排序
 * @param totalLines log 總行數
 * @param errorLines ERROR 等級行數
 * @param warnLines  WARN 等級行數（與 ERROR 分開統計）
 */
public record LogAnalysis(List<ErrorCluster> clusters, int totalLines, int errorLines, int warnLines) {

    /** 相容建構子：未提供 WARN 統計者預設為 0。 */
    public LogAnalysis(List<ErrorCluster> clusters, int totalLines, int errorLines) {
        this(clusters, totalLines, errorLines, 0);
    }
}
