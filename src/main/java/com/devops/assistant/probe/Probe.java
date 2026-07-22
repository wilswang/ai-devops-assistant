package com.devops.assistant.probe;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 唯讀診斷探針定義。
 *
 * @param name           probe 名稱（唯一）
 * @param category       層別：system / docker / tomcat
 * @param description    給 LLM 看的用途說明
 * @param needsContainer 是否需要 container 名稱
 * @param requiredParams 需要的額外參數（例如 pid）
 * @param build          依 params 建構一到多條 argv 指令（不含 shell）
 */
public record Probe(
        String name,
        String category,
        String description,
        boolean needsContainer,
        List<String> requiredParams,
        Function<Map<String, String>, List<List<String>>> build
) {
    public static Probe of(String name, String category, String description,
                           boolean needsContainer,
                           Function<Map<String, String>, List<List<String>>> build) {
        return new Probe(name, category, description, needsContainer, List.of(), build);
    }

    public static Probe of(String name, String category, String description,
                           boolean needsContainer, List<String> requiredParams,
                           Function<Map<String, String>, List<List<String>>> build) {
        return new Probe(name, category, description, needsContainer, requiredParams, build);
    }
}
