package com.devops.assistant.log;

/**
 * 一群相似的錯誤訊息（正規化後 signature 相同者歸為一群）。
 *
 * @param signature     正規化後的訊息樣態（去除 IP / 數字等變動值）
 * @param sample        代表性的原始 log 行
 * @param count         此群出現次數
 * @param exceptionType 若後續有 stacktrace，擷取到的 exception 類型（否則為空字串）
 * @param level         事件等級（ERROR / WARN）
 * @param detail        stacktrace 續行中可供比對的精簡文字（例外訊息、Caused by…；
 *                      不含純 stack frame），供 {@link IncidentCatalog} 深入比對埋在
 *                      stacktrace 內的關鍵字（如根因的 Connection refused）
 */
public record ErrorCluster(String signature, String sample, int count, String exceptionType,
                           LogLevel level, String detail) {

    /** 相容建構子：未指定等級者預設為 {@link LogLevel#ERROR}、無 detail。 */
    public ErrorCluster(String signature, String sample, int count, String exceptionType) {
        this(signature, sample, count, exceptionType, LogLevel.ERROR, "");
    }

    /** 相容建構子：指定等級但無 detail。 */
    public ErrorCluster(String signature, String sample, int count, String exceptionType,
                        LogLevel level) {
        this(signature, sample, count, exceptionType, level, "");
    }
}
