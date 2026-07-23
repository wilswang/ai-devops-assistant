package com.devops.assistant.log;

import java.util.List;
import java.util.regex.Pattern;

/**
 * log 格式規格：可配置的 pattern 集合，決定 {@link LogAnalyzer} 如何辨識等級、
 * 新記錄開頭（時間戳）、stacktrace 續行與 exception 類型。
 *
 * <p>由 {@code logformat.yaml} 載入（{@link LogFormatLoader}），regex 於載入時即編譯 →
 * 打錯 pattern 會 fail-fast。純資料物件，與 Spring 無關。
 *
 * @param levels         等級比對（順序即優先序，如 ERROR 先於 WARN）；命中即取其後為訊息
 * @param timestampHeader 一筆新 log 記錄的開頭（時間戳）
 * @param stackFrame     stacktrace 堆疊框行（如 {@code at ...}）
 * @param exceptionType  完整 exception 類名擷取（需含一個擷取群組）
 */
public record LogFormat(
        List<LevelPattern> levels,
        Pattern timestampHeader,
        Pattern stackFrame,
        Pattern exceptionType
) {
    /** 內建預設配置資源路徑。 */
    public static final String DEFAULT_RESOURCE = "logformat.yaml";

    /** 一個等級與其比對 pattern（以關鍵字組成 {@code \b(kw1|kw2)\b}）。 */
    public record LevelPattern(LogLevel level, Pattern pattern) {
    }
}
