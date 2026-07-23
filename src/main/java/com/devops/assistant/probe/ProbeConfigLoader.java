package com.devops.assistant.probe;

import com.devops.assistant.config.ConfigSource;
import com.devops.assistant.safety.CommandValidator;
import com.devops.assistant.safety.UnsafeCommandException;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 從 YAML 載入 probe 定義成 {@link Probe}。純 Java（SnakeYAML），可獨立單元測試。
 *
 * <p><b>安全紅線</b>：載入時，每個 probe 於當前 OS 解析出的每條 argv 都會過
 * {@link com.devops.assistant.safety.CommandValidator} 白名單——配置<b>不能</b>因為
 * 「來自 YAML」就放行非唯讀指令。任何一條不合法即 fail-fast（{@link IllegalStateException}）。
 *
 * <p>YAML 結構：
 * <pre>
 * probes:
 *   - name: system_cpu
 *     category: system
 *     description: "..."
 *     needsContainer: false        # 預設 false
 *     params: ["pid"]              # 預設 []
 *     commands:                    # 依 OS 分支；linux / macos / default
 *       linux:  [["top", "-bn1"]]
 *       macos:  [["top", "-l", "1"]]
 *   - name: docker_stats
 *     category: docker
 *     needsContainer: true
 *     commands:
 *       default: [["docker", "stats", "--no-stream", "${container}"]]
 * </pre>
 * 佔位符 {@code ${container}}（預設 tomcat）、{@code ${pid}}（預設 1）於執行時由 params 代入。
 */
public final class ProbeConfigLoader {

    private ProbeConfigLoader() {
    }

    /** 解析 YAML 成有序的 probe map（含 load-time 白名單驗證）。 */
    public static Map<String, Probe> parse(String yaml) {
        Object root = new Yaml().load(yaml);
        if (!(root instanceof Map<?, ?> r) || !(r.get("probes") instanceof List<?> items)) {
            throw new IllegalStateException("probes 配置格式錯誤：缺少 probes 清單");
        }
        Map<String, Probe> result = new LinkedHashMap<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) {
                throw new IllegalStateException("probe 項目格式錯誤：" + item);
            }
            Probe probe = toProbe(m);
            result.put(probe.name(), probe);
        }
        // 安全紅線：載入時每個 probe（當前 OS）的每條 argv 都要過白名單。
        result.values().forEach(ProbeConfigLoader::validateWhitelist);
        return result;
    }

    /** 載入資源（外部優先，否則 classpath；找不到或解析/驗證失敗即 fail-fast）。 */
    public static Map<String, Probe> load(String resource) {
        return parse(ConfigSource.read(resource));
    }

    private static Probe toProbe(Map<?, ?> m) {
        String name = requireString(m, "name");
        String category = requireString(m, "category");
        String description = requireString(m, "description");
        boolean needsContainer = Boolean.TRUE.equals(m.get("needsContainer"));
        List<String> params = parseParams(m.get("params"));
        Map<String, List<List<String>>> commandsByOs = parseCommands(m.get("commands"), name);
        return Probe.of(name, category, description, needsContainer, params,
                buildFunction(commandsByOs));
    }

    /** 產生 build 函式：依 OS 解析指令、再代入 ${container}/${pid} 佔位。 */
    private static Function<Map<String, String>, List<List<String>>> buildFunction(
            Map<String, List<List<String>>> commandsByOs) {
        return params -> {
            List<List<String>> template = resolveForOs(commandsByOs);
            String container = value(params, "container", "tomcat");
            String pid = value(params, "pid", "1");
            List<List<String>> out = new ArrayList<>();
            for (List<String> argv : template) {
                List<String> sub = new ArrayList<>(argv.size());
                for (String tok : argv) {
                    sub.add(tok.replace("${container}", container).replace("${pid}", pid));
                }
                out.add(List.copyOf(sub));
            }
            return List.copyOf(out);
        };
    }

    /** mac → macos，其餘（linux/other）→ linux；找不到再退回 default。 */
    private static List<List<String>> resolveForOs(Map<String, List<List<String>>> byOs) {
        String osKey = Os.current().isMac() ? "macos" : "linux";
        List<List<String>> cmds = byOs.getOrDefault(osKey, byOs.get("default"));
        if (cmds == null) {
            throw new IllegalStateException("probe 在當前 OS 沒有可用指令（缺 " + osKey + " 或 default）");
        }
        return cmds;
    }

    private static void validateWhitelist(Probe probe) {
        for (List<String> argv : probe.build().apply(Map.of())) {
            try {
                CommandValidator.validate(argv);
            } catch (UnsafeCommandException e) {
                throw new IllegalStateException(
                        "probe '" + probe.name() + "' 的指令未過唯讀白名單：" + e.getMessage(), e);
            }
        }
    }

    private static Map<String, List<List<String>>> parseCommands(Object raw, String probeName) {
        if (!(raw instanceof Map<?, ?> byOs) || byOs.isEmpty()) {
            throw new IllegalStateException("probe '" + probeName + "' 缺少 commands");
        }
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : byOs.entrySet()) {
            String osKey = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof List<?> cmds) || cmds.isEmpty()) {
                throw new IllegalStateException(
                        "probe '" + probeName + "' 的 commands." + osKey + " 缺少或為空");
            }
            List<List<String>> argvList = new ArrayList<>();
            for (Object cmd : cmds) {
                if (!(cmd instanceof List<?> argv) || argv.isEmpty()) {
                    throw new IllegalStateException(
                            "probe '" + probeName + "' 的指令須為非空字串陣列");
                }
                List<String> tokens = new ArrayList<>();
                for (Object tok : argv) {
                    if (!(tok instanceof String s) || s.isEmpty()) {
                        throw new IllegalStateException(
                                "probe '" + probeName + "' 的指令 token 須為非空字串");
                    }
                    tokens.add(s);
                }
                argvList.add(List.copyOf(tokens));
            }
            result.put(osKey, List.copyOf(argvList));
        }
        return result;
    }

    private static List<String> parseParams(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new IllegalStateException("params 須為字串陣列");
        }
        List<String> result = new ArrayList<>();
        for (Object p : list) {
            if (!(p instanceof String s) || s.isBlank()) {
                throw new IllegalStateException("params 含空白項目");
            }
            result.add(s);
        }
        return List.copyOf(result);
    }

    private static String requireString(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalStateException("probe 缺少必填欄位或為空：" + key);
        }
        return s;
    }

    private static String value(Map<String, String> params, String key, String fallback) {
        String v = params.get(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /** 便捷：載入內建預設 probes.yaml。 */
    public static Map<String, Probe> loadDefault() {
        return load("probes.yaml");
    }
}
