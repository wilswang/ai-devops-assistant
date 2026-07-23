package com.devops.assistant.log;

import java.util.List;

/**
 * 一條已知事件比對規則：命中任一 keyword 即回報對應的 {@link KnownEvent}。
 *
 * @param event    命中時回報的事件（id / 說明 / 建議）
 * @param keywords 比對關鍵字（皆為小寫，比對時對文字做 lowercase）
 */
public record IncidentRule(KnownEvent event, List<String> keywords) {
}
