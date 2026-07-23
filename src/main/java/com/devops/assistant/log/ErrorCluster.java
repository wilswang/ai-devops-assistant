package com.devops.assistant.log;

/**
 * 一群相似的錯誤訊息（正規化後 signature 相同者歸為一群）。
 *
 * @param signature     正規化後的訊息樣態（去除 IP / 數字等變動值）
 * @param sample        代表性的原始 log 行
 * @param count         此群出現次數
 * @param exceptionType 若後續有 stacktrace，擷取到的 exception 類型（否則為空字串）
 * @param level         事件等級（ERROR / WARN）
 */
public record ErrorCluster(String signature, String sample, int count, String exceptionType,
                           LogLevel level) {

    /** 相容建構子：未指定等級者預設為 {@link LogLevel#ERROR}。 */
    public ErrorCluster(String signature, String sample, int count, String exceptionType) {
        this(signature, sample, count, exceptionType, LogLevel.ERROR);
    }
}
