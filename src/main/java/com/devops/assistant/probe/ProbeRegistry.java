package com.devops.assistant.probe;

import java.util.Map;

/**
 * 唯讀 probe 庫。三層：
 * <ul>
 *   <li>system : 主機層（CPU / mem / disk / process / port / network）</li>
 *   <li>docker : container 層（狀態 / 資源 / log）</li>
 *   <li>tomcat : JVM 層（thread dump / GC / 進程），透過 docker exec 唯讀取得</li>
 * </ul>
 *
 * <p>probe 定義由 {@code probes.yaml}（classpath 內建預設）載入（{@link ProbeConfigLoader}），
 * 載入時每條指令都過 {@code CommandValidator} 白名單，fail-fast。為使 fail-fast 發生在
 * <b>應用啟動時</b>，由 {@code StartupConfigValidator} 顯式呼叫 {@link #load()}；未預載時
 * {@link #all()} 惰性補載。未來 BACKLOG #5 可由外部檔覆寫。
 *
 * <p>LLM 只能「選擇要跑哪些 probe」，不能自由生成指令，因此永遠不會產出
 * rm / kill / restart 這類破壞性操作。
 */
public final class ProbeRegistry {

    /** 內建預設配置資源路徑。 */
    static final String DEFAULT_RESOURCE = "probes.yaml";

    /** 已載入的 probe（有序）；null 表示尚未載入。 */
    private static volatile Map<String, Probe> probes;

    private ProbeRegistry() {
    }

    /** 顯式載入/重載配置（fail-fast）。由啟動流程呼叫以達成開機即驗證。 */
    public static void load() {
        probes = ProbeConfigLoader.loadFromClasspath(DEFAULT_RESOURCE);
    }

    public static Map<String, Probe> all() {
        if (probes == null) {
            load();  // 未經啟動流程預載時（如純單元測試）惰性補載
        }
        return probes;
    }

    public static Probe get(String name) {
        return all().get(name);
    }
}
