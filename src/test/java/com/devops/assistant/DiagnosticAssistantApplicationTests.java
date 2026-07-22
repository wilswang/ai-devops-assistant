package com.devops.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 冒煙測試：整個 Spring 應用（含 Spring AI ChatClient 佈線）能正常啟動。
 * 以 dummy api-key 讓 context 可離線載入（不會發網路請求）。
 */
@SpringBootTest(properties = "spring.ai.anthropic.api-key=test-dummy")
class DiagnosticAssistantApplicationTests {

    @Test
    void contextLoads() {
        // 若任何 bean 佈線錯誤（含 Spring AI / controller / agent），此測試會失敗
    }
}
