package com.devops.assistant.log;

/**
 * log 事件等級。順序即呈現優先序：{@link #ERROR} 先於 {@link #WARN}。
 */
public enum LogLevel {
    ERROR,
    WARN
}
