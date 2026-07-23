package com.devops.assistant.agent;

import com.devops.assistant.probe.ProbeResult;

import java.util.List;

/**
 * 把一批 {@link ProbeResult} 格式化成給 LLM 的「唯讀蒐證」證據區塊。
 *
 * <p>用於 <b>tool-calling fallback</b>：當本地模型不支援 function calling 時，改採
 * 「先蒐全部證據 → 塞進 prompt → 模型直接產報告」而非讓模型自行呼叫工具。
 * 每個 probe 的輸出有長度上限，避免灌爆小模型的 context window。
 *
 * <p>純 Java、與 Spring 無關，可獨立單元測試。
 */
public final class EvidenceReport {

    /** 每個 probe 輸出的字元上限，超出即截斷。 */
    static final int MAX_OUTPUT_CHARS_PER_PROBE = 2000;

    private EvidenceReport() {
    }

    /** 格式化證據區塊：每個 probe 一段（狀態、指令、輸出，輸出過長則截斷）。 */
    public static String format(List<ProbeResult> results) {
        StringBuilder sb = new StringBuilder();
        for (ProbeResult res : results) {
            String status = res.ok() ? "OK" : "SKIP/ERR";
            sb.append("## [").append(status).append("] ").append(res.probe()).append('\n');
            sb.append("$ ").append(String.join("; ", res.commands())).append('\n');
            sb.append(res.output().isBlank() ? "(無輸出)" : truncate(res.output())).append("\n\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String truncate(String output) {
        if (output.length() <= MAX_OUTPUT_CHARS_PER_PROBE) {
            return output;
        }
        return output.substring(0, MAX_OUTPUT_CHARS_PER_PROBE) + "\n...[truncated]";
    }
}
