package com.devops.assistant.log;

import com.devops.assistant.config.ConfigSource;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 從 YAML 載入 {@link LogFormat}。純 Java（SnakeYAML），可獨立單元測試。
 *
 * <p><b>fail-fast</b>：缺欄位、未知等級名、或 regex 編譯失敗即拋 {@link IllegalStateException}。
 *
 * <p>YAML 結構：
 * <pre>
 * logFormat:
 *   levels:                 # 順序即優先序
 *     ERROR: ["ERROR"]
 *     WARN:  ["WARN"]
 *   timestampHeader: '^\d{4}-\d{2}-\d{2}[ T]'
 *   stackFrame:      '^at\s'
 *   exceptionType:   '((?:[\w$]+\.)+[\w$]*(?:Exception|Error|Throwable))'
 * </pre>
 */
public final class LogFormatLoader {

    private LogFormatLoader() {
    }

    /** 解析 YAML 字串成 {@link LogFormat}（regex 立即編譯，錯誤即 fail-fast）。 */
    public static LogFormat parse(String yaml) {
        Object root = new Yaml().load(yaml);
        if (!(root instanceof Map<?, ?> r) || !(r.get("logFormat") instanceof Map<?, ?> fmt)) {
            throw new IllegalStateException("logFormat 配置格式錯誤：缺少 logFormat 節點");
        }
        return new LogFormat(
                parseLevels(fmt.get("levels")),
                compile(requireString(fmt, "timestampHeader"), "timestampHeader"),
                compile(requireString(fmt, "stackFrame"), "stackFrame"),
                compile(requireString(fmt, "exceptionType"), "exceptionType"));
    }

    /** 載入資源（外部優先，否則 classpath；找不到或解析失敗即 fail-fast）。 */
    public static LogFormat load(String resource) {
        return parse(ConfigSource.read(resource));
    }

    /** 便捷：載入內建預設 {@link LogFormat#DEFAULT_RESOURCE}。 */
    public static LogFormat loadDefault() {
        return load(LogFormat.DEFAULT_RESOURCE);
    }

    /** 供測試/內部：等級關鍵字清單編成 {@code \b(kw1|kw2)\b} 的 pattern 字串。 */
    static String levelRegex(List<String> keywords) {
        return "\\b(" + String.join("|", keywords) + ")\\b";
    }

    private static List<LogFormat.LevelPattern> parseLevels(Object raw) {
        if (!(raw instanceof Map<?, ?> levels) || levels.isEmpty()) {
            throw new IllegalStateException("logFormat.levels 缺少或為空");
        }
        List<LogFormat.LevelPattern> result = new ArrayList<>();
        for (Map.Entry<?, ?> e : levels.entrySet()) {
            String name = String.valueOf(e.getKey());
            LogLevel level;
            try {
                level = LogLevel.valueOf(name);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException(
                        "logFormat.levels 含未知等級名（僅支援 " + java.util.Arrays.toString(LogLevel.values())
                                + "）：" + name);
            }
            result.add(new LogFormat.LevelPattern(level,
                    compile(levelRegex(requireKeywords(e.getValue(), name)), "levels." + name)));
        }
        return List.copyOf(result);
    }

    private static List<String> requireKeywords(Object raw, String levelName) {
        if (!(raw instanceof List<?> kw) || kw.isEmpty()) {
            throw new IllegalStateException("logFormat.levels." + levelName + " 的關鍵字缺少或為空");
        }
        List<String> result = new ArrayList<>();
        for (Object k : kw) {
            if (!(k instanceof String s) || s.isBlank()) {
                throw new IllegalStateException("logFormat.levels." + levelName + " 含空白關鍵字");
            }
            result.add(s);
        }
        return result;
    }

    private static Pattern compile(String regex, String field) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalStateException("logFormat." + field + " 的 regex 無法編譯：" + regex, e);
        }
    }

    private static String requireString(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalStateException("logFormat 缺少必填欄位或為空：" + key);
        }
        return s;
    }
}
