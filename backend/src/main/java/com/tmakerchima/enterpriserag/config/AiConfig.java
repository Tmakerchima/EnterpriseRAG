package com.tmakerchima.enterpriserag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/** AI client and environment-driven CORS configuration. */
@Configuration(proxyBeanMethods = false)
public class AiConfig implements WebFluxConfigurer {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("""
                You are an enterprise knowledge assistant. Answer only from authorized retrieved evidence.
                If evidence is insufficient, abstain clearly. Never invent facts, permissions, dates, or metrics.
                Keep answers concise and cite source identifiers when possible.
                """).build();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = System.getenv().getOrDefault("ENTERPRISE_RAG_CORS_ORIGINS", "http://localhost:5173");
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.split(","))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
