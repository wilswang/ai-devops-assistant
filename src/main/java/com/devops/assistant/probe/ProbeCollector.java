package com.devops.assistant.probe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 依序執行全部 probe 的蒐證器（CLI --collect-only 與 REST /api/collect 共用）。
 * 會自動把 tomcat_jvm_procs 取得的 JVM PID 串接給後續 gc/thread probe。
 */
public final class ProbeCollector {

    private ProbeCollector() {
    }

    public static List<ProbeResult> collectAll(String container) {
        List<ProbeResult> results = new ArrayList<>();
        String pid = "1";

        for (Probe probe : ProbeRegistry.all().values()) {
            Map<String, String> params = new HashMap<>();
            params.put("container", container);
            params.put("pid", pid);

            ProbeResult res = ProbeRunner.run(probe, params);
            results.add(res);

            if ("tomcat_jvm_procs".equals(probe.name()) && res.ok()) {
                pid = firstPid(res.output(), pid);
            }
        }
        return results;
    }

    private static String firstPid(String jpsOutput, String fallback) {
        for (String line : jpsOutput.split("\n")) {
            String[] tok = line.trim().split("\\s+");
            if (tok.length > 0 && tok[0].matches("\\d+")) {
                return tok[0];
            }
        }
        return fallback;
    }
}
