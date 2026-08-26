package com.tmakerchima.enterpriserag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** AI client configuration. */
@Configuration(proxyBeanMethods = false)
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("""
                You are an enterprise knowledge assistant. Answer only from authorized retrieved evidence.
                If evidence is insufficient, abstain clearly. Never invent facts, permissions, dates, or metrics.
                Keep answers concise and cite source identifiers when possible.
                """).build();
    }
}
