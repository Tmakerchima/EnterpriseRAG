package com.tmakerchima.enterpriserag.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Allows the browser frontend to call this API.
 *
 * <p>The production frontend and local Vite server are always allowed. Extra
 * origins can be added with {@code ENTERPRISE_RAG_CORS_ORIGINS}, separated by
 * commas. Keeping this in its own class makes the deployment rule easy to find.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig implements WebFluxConfigurer {

    static final String PRODUCTION_FRONTEND = "https://enterprise-rag-frontend-seven.vercel.app";
    static final String LOCAL_FRONTEND = "http://localhost:5173";

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${ENTERPRISE_RAG_CORS_ORIGINS:}") String configuredOrigins) {
        LinkedHashSet<String> origins = new LinkedHashSet<>();
        origins.add(PRODUCTION_FRONTEND);
        origins.add(LOCAL_FRONTEND);
        Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .forEach(origins::add);
        this.allowedOrigins = List.copyOf(origins);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600L);
    }
}
