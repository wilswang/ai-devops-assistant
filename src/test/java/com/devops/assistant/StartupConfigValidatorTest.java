package com.devops.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 配置化階段 A 修正 TDD — 啟動時配置驗證（fail-fast 提前到開機）。
 * 此測試鎖定 validate() 確實會觸發配置載入；內建預設配置正確時不應拋錯。
 */
class StartupConfigValidatorTest {

    @Test
    void validatesBundledConfigWithoutError() {
        assertDoesNotThrow(() -> new StartupConfigValidator().validate(),
                "內建預設 incidents.yaml 正確，啟動驗證不應拋錯");
    }
}
