package com.devops.assistant.log;

/**
 * 一種已知的事件樣態（incident type）。
 *
 * @param id          事件代號，如 OOM、CONNECTION_REFUSED
 * @param description 說明
 * @param suggestion  建議處置方向（供診斷報告引用）
 */
public record KnownEvent(String id, String description, String suggestion) {
}
