package com.devops.assistant.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * BACKLOG #1 TDD — LLM provider 切換選擇器。目前 ChatModelSelector 為 stub，此測試預期為紅燈。
 */
class ChatModelSelectorTest {

    private final ChatModel anthropic = mock(ChatModel.class);
    private final ChatModel ollama = mock(ChatModel.class);

    /** 未被選中的 provider supplier 若被呼叫就代表不夠惰性 → 讓它爆炸以驗證。 */
    private Map<String, Supplier<ChatModel>> providersWithGuard() {
        return Map.of(
                "anthropic", () -> anthropic,
                "ollama", () -> {
                    throw new AssertionError("未被選中的 ollama supplier 不應被呼叫");
                });
    }

    @Test
    void selectsChosenProviderLazily() {
        ChatModel chosen = ChatModelSelector.select("anthropic", providersWithGuard());
        assertSame(anthropic, chosen, "應回傳被選中的 provider model，且不觸發其他 supplier");
    }

    @Test
    void isCaseInsensitiveAndTrimmed() {
        Map<String, Supplier<ChatModel>> providers = Map.of(
                "anthropic", () -> anthropic,
                "ollama", () -> ollama);
        assertSame(ollama, ChatModelSelector.select("  Ollama ", providers),
                "provider 名稱應大小寫不敏感並忽略前後空白");
    }

    @Test
    void throwsForUnknownProviderWithAvailableList() {
        Map<String, Supplier<ChatModel>> providers = Map.of(
                "anthropic", () -> anthropic,
                "ollama", () -> ollama);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ChatModelSelector.select("gpt-9", providers));
        assertTrue(ex.getMessage().contains("gpt-9"), "訊息應含未知的 provider 名稱");
        assertTrue(ex.getMessage().contains("anthropic") && ex.getMessage().contains("ollama"),
                "訊息應列出可用 provider 以利排錯，實際: " + ex.getMessage());
    }

    @Test
    void throwsForNullOrBlankProvider() {
        Map<String, Supplier<ChatModel>> providers = Map.of("anthropic", () -> anthropic);
        assertThrows(IllegalStateException.class,
                () -> ChatModelSelector.select(null, providers));
        assertThrows(IllegalStateException.class,
                () -> ChatModelSelector.select("   ", providers));
    }
}
