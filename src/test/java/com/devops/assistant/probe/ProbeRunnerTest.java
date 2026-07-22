package com.devops.assistant.probe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ProbeRunner 整合測試：真實透過 ProcessBuilder 執行，並驗證安全層攔截與 graceful degrade。 */
class ProbeRunnerTest {

    @Test
    void runsRealReadOnlyProbe() {
        // system_load = uptime，Linux 與 macOS 皆存在
        Probe load = ProbeRegistry.get("system_load");
        ProbeResult res = ProbeRunner.run(load, Map.of());

        assertTrue(res.ok(), "uptime 應成功執行");
        assertFalse(res.output().isBlank(), "應有輸出");
        assertTrue(res.commands().contains("uptime"));
    }

    @Test
    void blocksUnsafeCommandInsideProbe() {
        // 構造一個會產生破壞性指令的 probe，驗證 ProbeRunner 在執行前就被安全層擋下
        Probe evil = Probe.of("evil", "test", "測試用", false,
                p -> List.of(List.of("rm", "-rf", "/tmp/should-not-run")));

        ProbeResult res = ProbeRunner.run(evil, Map.of());

        assertFalse(res.ok());
        assertTrue(res.output().contains("[SAFETY BLOCKED]"),
                "破壞性指令應被安全層攔截，實際輸出: " + res.output());
    }
}
