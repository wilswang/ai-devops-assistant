package com.devops.assistant.agent;

import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 依 {@code app.llm.provider} 設定，從可用的 LLM provider 中挑一個 {@link ChatModel}。
 *
 * <p>純邏輯、與 Spring 容器無關（僅相依 Spring AI 的 {@link ChatModel} 型別），故可獨立單元測試。
 * 以 {@link Supplier} 傳入各 provider 的 model，確保<b>只有被選中的</b>才會被實體化（惰性），
 * 未啟動的 provider（例如未跑 Ollama）不會因為建立 bean 而出錯。
 */
public final class ChatModelSelector {

    private ChatModelSelector() {
    }

    /**
     * 依 provider 名稱挑選 model。名稱大小寫不敏感、前後空白忽略。
     *
     * @param provider  設定值，如 {@code anthropic} / {@code ollama}
     * @param providers provider 名稱（小寫）→ 該 model 的惰性 supplier
     * @return 選中的 {@link ChatModel}
     * @throws IllegalStateException 名稱為 null/空白或不在可用清單中
     */
    public static ChatModel select(String provider, Map<String, Supplier<ChatModel>> providers) {
        String key = provider == null ? "" : provider.trim().toLowerCase();
        Supplier<ChatModel> supplier = providers.get(key);
        if (supplier == null) {
            throw new IllegalStateException(
                    "未知或未啟用的 app.llm.provider: '" + provider + "'，可用: " + providers.keySet());
        }
        return supplier.get();
    }
}
