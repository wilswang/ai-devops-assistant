package com.devops.assistant.probe;

/**
 * 主機作業系統偵測。主機層 probe（top / free / ss ...）在 Linux 與 macOS 語法不同，
 * 依此選擇對應指令。docker exec 進 container 的 probe 不受影響（container 為 Linux）。
 */
public enum Os {
    LINUX, MAC, OTHER;

    public static Os current() {
        String n = System.getProperty("os.name", "").toLowerCase();
        if (n.contains("linux")) {
            return LINUX;
        }
        if (n.contains("mac") || n.contains("darwin")) {
            return MAC;
        }
        return OTHER;
    }

    public boolean isMac() {
        return this == MAC;
    }
}
