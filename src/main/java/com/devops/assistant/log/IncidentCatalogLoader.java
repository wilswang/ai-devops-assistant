package com.devops.assistant.log;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 從 YAML 載入已知事件比對規則（{@link IncidentRule}）。
 *
 * <p>純 Java（SnakeYAML，非 Spring），可獨立單元測試。載入採 <b>fail-fast</b>：
 * 格式錯誤或缺必填欄位（id / description / suggestion / keywords）即拋
 * {@link IllegalStateException}，不靜默略過。
 *
 * <p>YAML 結構：
 * <pre>
 * incidents:
 *   - id: OOM
 *     description: "記憶體不足…"
 *     suggestion: "檢查 heap…"
 *     keywords: ["outofmemoryerror", "oomkilled"]
 * </pre>
 */
public final class IncidentCatalogLoader {

    private IncidentCatalogLoader() {
    }

    /** 解析 YAML 字串成規則清單；keywords 一律轉小寫。 */
    public static List<IncidentRule> parse(String yaml) {
        Object root = new Yaml().load(yaml);
        if (!(root instanceof Map<?, ?> map) || !(map.get("incidents") instanceof List<?> items)) {
            throw new IllegalStateException("incidents 配置格式錯誤：缺少 incidents 清單");
        }
        List<IncidentRule> rules = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) {
                throw new IllegalStateException("incidents 項目格式錯誤：" + item);
            }
            String id = requireString(m, "id");
            String description = requireString(m, "description");
            String suggestion = requireString(m, "suggestion");
            List<String> keywords = requireKeywords(m, id);
            rules.add(new IncidentRule(new KnownEvent(id, description, suggestion), keywords));
        }
        return List.copyOf(rules);
    }

    /** 從 classpath 資源載入（找不到或解析失敗即 fail-fast）。 */
    public static List<IncidentRule> loadFromClasspath(String resource) {
        try (InputStream in = IncidentCatalogLoader.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("找不到 incidents 配置資源：" + resource);
            }
            return parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("讀取 incidents 配置失敗：" + resource, e);
        }
    }

    private static String requireString(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalStateException("incident 缺少必填欄位或為空：" + key);
        }
        return s;
    }

    private static List<String> requireKeywords(Map<?, ?> m, String id) {
        if (!(m.get("keywords") instanceof List<?> kw) || kw.isEmpty()) {
            throw new IllegalStateException("incident '" + id + "' 的 keywords 缺少或為空");
        }
        List<String> result = new ArrayList<>();
        for (Object k : kw) {
            if (!(k instanceof String s) || s.isBlank()) {
                throw new IllegalStateException("incident '" + id + "' 含空白 keyword");
            }
            result.add(s.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(result);
    }
}
