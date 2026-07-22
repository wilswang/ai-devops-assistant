package com.devops.assistant.log;

import java.util.List;

/**
 * log 分析結果：精簡後的錯誤群集，供 LLM 消化（避免整份 log 灌入）。
 *
 * @param clusters   錯誤群集，依 count 由多到少排序
 * @param totalLines log 總行數
 * @param errorLines ERROR 等級行數
 */
public record LogAnalysis(List<ErrorCluster> clusters, int totalLines, int errorLines) {
}
