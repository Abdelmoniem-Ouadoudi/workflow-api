package ma.dev.workflow.classification.common.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the client used to talk to the model.
 *
 * <p>Only created when the provider is {@code groq}. Without this condition the application would
 * need a valid OpenAI configuration to start at all, which would make running the project without
 * an API key impossible — and running it without a key is exactly what the stub is for.
 */
@Configuration
@ConditionalOnProperty(name = "app.classification.provider", havingValue = "groq")
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
