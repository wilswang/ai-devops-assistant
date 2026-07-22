package com.devops.assistant.probe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ProbeRegistry / ProbeCollector 特徵測試：釘住 probe 數量、分層與蒐證行為。 */
class ProbeRegistryTest {

    @Test
    void hasExpectedProbeCount() {
        assertEquals(15, ProbeRegistry.all().size());
    }

    @Test
    void coversThreeLayers() {
        List<String> categories = ProbeRegistry.all().values().stream()
                .map(Probe::category).distinct().sorted().toList();
        assertEquals(List.of("docker", "system", "tomcat"), categories);
    }

    @Test
    void jvmProbesRequireContainer() {
        assertTrue(ProbeRegistry.get("tomcat_thread_dump").needsContainer());
        assertTrue(ProbeRegistry.get("tomcat_gc_stat").requiredParams().contains("pid"));
    }

    @Test
    void osAwareMemoryProbeProducesNonEmptyCommand() {
        // 不論在 Linux 或 macOS，system_memory 都要能產生一條可執行指令
        List<List<String>> cmds = ProbeRegistry.get("system_memory").build().apply(Map.of());
        assertFalse(cmds.isEmpty());
        String binary = cmds.get(0).get(0);
        assertTrue(binary.equals("free") || binary.equals("vm_stat"), "非預期的記憶體指令: " + binary);
    }

    @Test
    void collectorReturnsAllProbesInOrder() {
        List<ProbeResult> results = ProbeCollector.collectAll("tomcat");
        assertEquals(15, results.size());
        assertNotNull(results.get(0).probe());
    }
}
