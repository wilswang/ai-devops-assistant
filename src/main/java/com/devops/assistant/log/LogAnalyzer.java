package com.devops.assistant.log;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 從原始 log 抽取錯誤群集：過濾雜訊（INFO/DEBUG）→ 正規化訊息 → 相似者分群 → 依次數排序。
 * ERROR 之後跟隨的多行 stacktrace（exception 行、{@code at ...}）視為同一錯誤事件，
 * 不計為獨立錯誤，並從中擷取 exception 類型。
 *
 * <p>目的：把可能很大的 log 壓縮成少數「錯誤樣態 + 次數 + exception 類型」，才能有效餵給 LLM。
 */
public final class LogAnalyzer {

    private static final Pattern ERROR_LEVEL = Pattern.compile("\\bERROR\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b\\d+\\.\\d+\\.\\d+\\.\\d+\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    /** 一筆新 log 記錄的開頭（時間戳，如 2026-07-22 10:00:01）。 */
    private static final Pattern LOG_HEADER = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]");
    /** 完整類名結尾為 Exception / Error / Throwable。 */
    private static final Pattern EXCEPTION =
            Pattern.compile("((?:[\\w$]+\\.)+[\\w$]*(?:Exception|Error|Throwable))");

    /** 分析原始 log 文字，回傳依次數排序的錯誤群集。 */
    public LogAnalysis analyze(String rawLog) {
        if (rawLog == null || rawLog.isBlank()) {
            return new LogAnalysis(List.of(), 0, 0);
        }

        int totalLines = 0;
        int errorLines = 0;
        Map<String, Acc> bySignature = new LinkedHashMap<>();
        Acc currentEvent = null; // 目前 ERROR 事件，供後續 stacktrace 續行吸附

        for (String raw : rawLog.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            totalLines++;

            if (isLogHeader(line)) {
                // 新的一筆 log 記錄
                String message = errorMessage(line);
                if (message == null) {
                    currentEvent = null; // 非 ERROR 記錄，結束前一事件的續行吸附
                    continue;
                }
                errorLines++;
                String signature = normalize(message);
                currentEvent = bySignature.computeIfAbsent(signature, k -> new Acc(signature, line));
                currentEvent.count++;
            } else if (currentEvent != null) {
                // 續行（stacktrace）：嘗試擷取 exception 類型（僅取第一個，即最上層例外）
                if (currentEvent.exceptionType.isEmpty()) {
                    String ex = exceptionTypeOf(line);
                    if (ex != null) {
                        currentEvent.exceptionType = ex;
                    }
                }
            }
        }

        List<ErrorCluster> clusters = bySignature.values().stream()
                .map(a -> new ErrorCluster(a.signature, a.sample, a.count, a.exceptionType))
                .sorted(Comparator.comparingInt(ErrorCluster::count).reversed())
                .toList();

        return new LogAnalysis(clusters, totalLines, errorLines);
    }

    private static boolean isLogHeader(String line) {
        return LOG_HEADER.matcher(line).find();
    }

    /** 若為 ERROR 行，回傳 ERROR 等級之後的訊息；否則回 null。 */
    private static String errorMessage(String line) {
        var m = ERROR_LEVEL.matcher(line);
        return m.find() ? line.substring(m.end()).strip() : null;
    }

    /** 從續行擷取完整 exception 類名（如 java.lang.NullPointerException），無則回 null。 */
    private static String exceptionTypeOf(String line) {
        var m = EXCEPTION.matcher(line);
        return m.find() ? m.group(1) : null;
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
        String exceptionType = "";

        Acc(String signature, String sample) {
            this.signature = signature;
            this.sample = sample;
        }
    }
}
