package com.devops.assistant.log;

/**
 * 一群相似的錯誤訊息（正規化後 signature 相同者歸為一群）。
 *
 * @param signature 正規化後的訊息樣態（去除 IP / 數字等變動值）
 * @param sample    代表性的原始 log 行
 * @param count     此群出現次數
 */
public record ErrorCluster(String signature, String sample, int count) {
}
