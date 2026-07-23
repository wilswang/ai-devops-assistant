package com.devops.assistant.probe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 配置化階段 C TDD — ProbeConfigLoader YAML 解析 + load-time 白名單驗證。
 * 目前為 stub，此測試預期為紅燈。
 */
class ProbeConfigLoaderTest {

    private static final String YAML = """
            probes:
              - name: system_load
                category: system
                description: "主機負載"
                commands:
                  default: [["uptime"]]
              - name: system_memory
                category: system
                description: "記憶體"
                commands:
                  linux: [["free", "-m"]]
                  macos: [["vm_stat"]]
              - name: tomcat_gc_stat
                category: tomcat
                description: "GC 快照"
                needsContainer: true
                params: ["pid"]
                commands:
                  default: [["docker", "exec", "${container}", "jstat", "-gcutil", "${pid}", "1000", "3"]]
            """;

    @Test
    void parsesProbesInOrderWithFlags() {
        Map<String, Probe> probes = ProbeConfigLoader.parse(YAML);

        assertEquals(List.of("system_load", "system_memory", "tomcat_gc_stat"),
                List.copyOf(probes.keySet()), "應保留順序");
        assertEquals("system", probes.get("system_load").category());
        assertTrue(probes.get("tomcat_gc_stat").needsContainer());
        assertTrue(probes.get("tomcat_gc_stat").requiredParams().contains("pid"));
        assertFalse(probes.get("system_load").needsContainer(), "未指定 needsContainer 應預設 false");
    }

    @Test
    void substitutesContainerAndPidPlaceholders() {
        Probe gc = ProbeConfigLoader.parse(YAML).get("tomcat_gc_stat");

        // 預設：container=tomcat、pid=1
        List<List<String>> def = gc.build().apply(Map.of());
        assertEquals(List.of("docker", "exec", "tomcat", "jstat", "-gcutil", "1", "1000", "3"),
                def.get(0), "未帶參數應代入預設 container=tomcat、pid=1");

        // 帶參數
        List<List<String>> withParams = gc.build().apply(Map.of("container", "apollo", "pid", "42"));
        assertEquals(List.of("docker", "exec", "apollo", "jstat", "-gcutil", "42", "1000", "3"),
                withParams.get(0), "應代入指定的 container / pid");
    }

    @Test
    void resolvesOsAwareCommand() {
        Probe mem = ProbeConfigLoader.parse(YAML).get("system_memory");
        List<List<String>> cmds = mem.build().apply(Map.of());
        assertFalse(cmds.isEmpty());
        String binary = cmds.get(0).get(0);
        assertTrue(binary.equals("free") || binary.equals("vm_stat"),
                "OS-aware 記憶體指令應為 free 或 vm_stat，實際: " + binary);
    }

    @Test
    void failsFastOnNonWhitelistedCommand() {
        // 安全紅線：配置來的非唯讀指令也不得放行
        String evil = """
                probes:
                  - name: nuke
                    category: system
                    description: "破壞性指令"
                    commands:
                      default: [["rm", "-rf", "/"]]
                """;
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ProbeConfigLoader.parse(evil), "非白名單指令應於載入時 fail-fast");
        assertTrue(ex.getMessage().contains("nuke") || ex.getMessage().toLowerCase().contains("rm"),
                "錯誤訊息應指出問題 probe/指令，實際: " + ex.getMessage());
    }

    @Test
    void failsFastOnMissingRequiredFieldOrCommands() {
        assertThrows(IllegalStateException.class, () -> ProbeConfigLoader.parse("""
                probes:
                  - name: no_cmd
                    category: system
                    description: "缺 commands"
                """), "缺 commands 應 fail-fast");
        assertThrows(IllegalStateException.class, () -> ProbeConfigLoader.parse("something: 1"),
                "缺 probes 節點應 fail-fast");
    }
}
