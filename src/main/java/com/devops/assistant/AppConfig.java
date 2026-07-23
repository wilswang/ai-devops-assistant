package com.devops.assistant;

import com.devops.assistant.agent.ChatModelSelector;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 依 {@code app.llm.provider} 選一個由 Spring AI 自動配置的 {@link ChatModel}
 * （anthropic starter 的 {@code anthropicChatModel} 或 ollama starter 的 {@code ollamaChatModel}）
 * 建立 {@link ChatClient}。
 *
 * <p>同時掛兩個 model starter 時，Spring AI 的 ChatClient.Builder 自動配置因多候選而退讓，
 * 故此處明確依設定挑選，並以 {@link ObjectProvider} 惰性解析——只實體化被選中的 model。
 */
@Configuration
public class AppConfig {

    @Bean
    public ChatClient chatClient(
            @Value("${app.llm.provider:anthropic}") String provider,
            @Qualifier("anthropicChatModel") ObjectProvider<ChatModel> anthropic,
            @Qualifier("ollamaChatModel") ObjectProvider<ChatModel> ollama) {

        Map<String, Supplier<ChatModel>> providers = Map.of(
                "anthropic", anthropic::getObject,
                "ollama", ollama::getObject);

        return ChatClient.create(ChatModelSelector.select(provider, providers));
    }
}
