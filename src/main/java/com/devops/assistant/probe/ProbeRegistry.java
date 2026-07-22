package com.devops.assistant.probe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 唯讀 probe 庫。三層：
 * <ul>
 *   <li>system : 主機層（CPU / mem / disk / process / port / network）</li>
 *   <li>docker : container 層（狀態 / 資源 / log）</li>
 *   <li>tomcat : JVM 層（thread dump / GC / 進程），透過 docker exec 唯讀取得</li>
 * </ul>
 *
 * <p>LLM 只能「選擇要跑哪些 probe」，不能自由生成指令，因此永遠不會產出
 * rm / kill / restart 這類破壞性操作。
 */
public final class ProbeRegistry {

    private static final Map<String, Probe> PROBES = build();

    private ProbeRegistry() {
    }

    public static Map<String, Probe> all() {
        return PROBES;
    }

    public static Probe get(String name) {
        return PROBES.get(name);
    }

    /** 取 container 名稱，預設 tomcat。 */
    private static String container(Map<String, String> p) {
        String c = p.get("container");
        return (c == null || c.isBlank()) ? "tomcat" : c;
    }

    /** 取 JVM PID，預設 "1"。 */
    private static String pid(Map<String, String> p) {
        String v = p.get("pid");
        return (v == null || v.isBlank()) ? "1" : v;
    }

    private static Map<String, Probe> build() {
        Map<String, Probe> m = new LinkedHashMap<>();

        // --- system 層 -----------------------------------------------------
        put(m, Probe.of("system_load", "system",
                "主機負載與開機時間（uptime）。判斷整體是否過載的第一站。",
                false,
                p -> List.of(List.of("uptime"))));

        put(m, Probe.of("system_cpu", "system",
                "CPU 使用率快照（Linux: top -bn1；macOS: top -l 1）。找出吃 CPU 的進程。",
                false,
                p -> Os.current().isMac()
                        ? List.of(List.of("top", "-l", "1", "-o", "cpu", "-n", "20"))
                        : List.of(List.of("top", "-bn1"))));

        put(m, Probe.of("system_memory", "system",
                "記憶體與 swap 使用（Linux: free -m；macOS: vm_stat）。判斷是否記憶體壓力 / 大量 swap。",
                false,
                p -> Os.current().isMac()
                        ? List.of(List.of("vm_stat"))
                        : List.of(List.of("free", "-m"))));

        put(m, Probe.of("system_disk", "system",
                "磁碟空間與 inode 使用（df）。磁碟或 inode 滿會導致服務異常變慢。",
                false,
                p -> List.of(List.of("df", "-h"), List.of("df", "-i"))));

        put(m, Probe.of("system_top_processes", "system",
                "依 CPU 排序的進程列表（ps）。定位資源大戶。",
                false,
                p -> Os.current().isMac()
                        ? List.of(List.of("ps", "-Ao", "pid,ppid,%cpu,%mem,comm", "-r"))
                        : List.of(List.of("ps", "-eo", "pid,ppid,%cpu,%mem,comm", "--sort=-%cpu"))));

        put(m, Probe.of("system_ports", "system",
                "監聽中的 TCP port（Linux: ss -tlnp；macOS: netstat）。確認 Tomcat 連接埠狀態。",
                false,
                p -> Os.current().isMac()
                        ? List.of(List.of("netstat", "-an", "-p", "tcp"))
                        : List.of(List.of("ss", "-tlnp"))));

        put(m, Probe.of("system_conn_summary", "system",
                "TCP 連線 / 協定統計（Linux: ss -s；macOS: netstat -s）。判斷連線數 / TIME-WAIT 是否異常。",
                false,
                p -> Os.current().isMac()
                        ? List.of(List.of("netstat", "-s", "-p", "tcp"))
                        : List.of(List.of("ss", "-s"))));

        // --- docker 層 -----------------------------------------------------
        put(m, Probe.of("docker_ps", "docker",
                "所有 container 狀態（docker ps -a）。看 Tomcat container 是否 Up / Restarting。",
                false,
                p -> List.of(List.of("docker", "ps", "-a"))));

        put(m, Probe.of("docker_stats", "docker",
                "container 即時資源用量（docker stats 一次性）：CPU% / MEM / NET / BLOCK IO。",
                true,
                p -> List.of(List.of("docker", "stats", "--no-stream", container(p)))));

        put(m, Probe.of("docker_inspect_health", "docker",
                "container 健康狀態、重啟次數、資源上限（docker inspect）。判斷 OOMKilled / 記憶體上限。",
                true,
                p -> List.of(List.of(
                        "docker", "inspect", "--format",
                        "State={{.State.Status}} Restarts={{.RestartCount}} "
                                + "OOMKilled={{.State.OOMKilled}} MemLimit={{.HostConfig.Memory}} "
                                + "NanoCpus={{.HostConfig.NanoCpus}}",
                        container(p)))));

        put(m, Probe.of("docker_top", "docker",
                "container 內進程列表（docker top）。確認 JVM 進程存活與 PID。",
                true,
                p -> List.of(List.of("docker", "top", container(p)))));

        put(m, Probe.of("docker_logs_tail", "docker",
                "container 最近 200 行 log（docker logs --tail）。找 error / OOM / stacktrace。",
                true,
                p -> List.of(List.of("docker", "logs", "--tail", "200", container(p)))));

        // --- tomcat / JVM 層（透過 docker exec 唯讀取得）--------------------
        put(m, Probe.of("tomcat_jvm_procs", "tomcat",
                "container 內 JVM 進程（jps -l）。取得 Tomcat 的 Java PID 供後續 GC / thread 分析。",
                true,
                p -> List.of(List.of("docker", "exec", container(p), "jps", "-l"))));

        put(m, Probe.of("tomcat_gc_stat", "tomcat",
                "JVM GC 使用率快照（jstat -gcutil，取樣 3 次）。判斷是否 GC 頻繁 / 老年代滿 / Full GC 拖慢。"
                        + " 需 params.pid（由 tomcat_jvm_procs 取得）。",
                true, List.of("pid"),
                p -> List.of(List.of("docker", "exec", container(p),
                        "jstat", "-gcutil", pid(p), "1000", "3"))));

        put(m, Probe.of("tomcat_thread_dump", "tomcat",
                "JVM thread dump（jstack）。看是否大量 BLOCKED / WAITING 執行緒、鎖競爭、慢查詢卡住。"
                        + " 需 params.pid。輸出可能較長。",
                true, List.of("pid"),
                p -> List.of(List.of("docker", "exec", container(p), "jstack", pid(p)))));

        return m;
    }

    private static void put(Map<String, Probe> m, Probe p) {
        m.put(p.name(), p);
    }
}
