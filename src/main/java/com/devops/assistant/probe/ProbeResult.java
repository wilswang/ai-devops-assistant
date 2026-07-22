package com.devops.assistant.probe;

import java.util.List;

/**
 * probe 執行結果。
 *
 * @param probe    probe 名稱
 * @param commands 實際執行的指令（顯示用）
 * @param output   合併後的輸出
 * @param ok       是否全部成功
 */
public record ProbeResult(
        String probe,
        List<String> commands,
        String output,
        boolean ok
) {
}
