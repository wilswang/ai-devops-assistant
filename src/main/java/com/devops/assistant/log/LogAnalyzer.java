package com.devops.assistant.log;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 從原始 log 抽取錯誤群集：過濾雜訊（INFO/DEBUG）→ 正規化訊息 → 相似者分群 → 依次數排序。
 *
 * <p>目的：把可能很大的 log 壓縮成少數「錯誤樣態 + 次數」，才能有效餵給 LLM。
 */
public final class LogAnalyzer {

    private static final Pattern ERROR_LEVEL = Pattern.compile("\\bERROR\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d+\\.\\d+\\.\\d+\\.\\d+\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** 分析原始 log 文字，回傳依次數排序的錯誤群集。 */
    public LogAnalysis analyze(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) {
            return new LogAnalysis(List.of(), 0, 0);
        }

        int totalLines = 0;
        int errorLines = 0;
        // 以正規化 signature 為 key；LinkedHashMap 保留首次出現順序，作為穩定的排序 tiebreak
        Map<String, Acc> bySignature = new LinkedHashMap<>();

        for (String raw : rawLog.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            totalLines++;

            String message = errorMessage(line);
            if (message == null) {
                continue; // 非 ERROR 行，視為雜訊濾掉
            }
            errorLines++;

            String signature = normalize(message);
            bySignature.computeIfAbsent(signature, k -> new Acc(signature, line)).count++;
        }

        List<ErrorCluster> clusters = bySignature.values().stream()
                .map(a -> new ErrorCluster(a.signature, a.sample, a.count))
                .sorted(Comparator.comparingInt(ErrorCluster::count).reversed())
                .toList();

        return new LogAnalysis(clusters, totalLines, errorLines);
    }

    /** 若為 ERROR 行，回傳 ERROR 等級之後的訊息；否則回 null。 */
    private static String errorMessage(String line) {
        var m = ERROR_LEVEL.matcher(line);
        return m.find() ? line.substring(m.end()).strip() : null;
    }

    /** 正規化訊息：抹去變動值（IP、數字），讓相似錯誤歸為同一 signature。 */
    private static String normalize(String message) {
        String s = IPV4.matcher(message).replaceAll("<IP>");
        s = NUMBER.matcher(s).replaceAll("<N>");
        return s.strip();
    }

    /** 分群累加器（可變）。 */
    private static final class Acc {
        final String signature;
        final String sample;
        int count;

        Acc(String signature, String sample) {
            this.signature = signature;
            this.sample = sample;
        }
    }
}
