package com.tmakerchima.enterpriserag.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.config.CorsRegistry;

class CorsConfigTest {

    @Test
    void productionFrontendIsAllowedEvenWhenEnvironmentOnlyContainsLocalhost() {
        CorsConfiguration cors = configuration("http://localhost:5173");

        assertEquals(CorsConfig.PRODUCTION_FRONTEND, cors.checkOrigin(CorsConfig.PRODUCTION_FRONTEND));
    }

    @Test
    void configuredExtraOriginIsTrimmedAndAllowed() {
        CorsConfiguration cors = configuration(" https://preview.example.com ");

        assertEquals("https://preview.example.com", cors.checkOrigin("https://preview.example.com"));
    }

    @Test
    void unknownOriginIsRejected() {
        CorsConfiguration cors = configuration("");

        assertNull(cors.checkOrigin("https://untrusted.example.com"));
    }

    @Test
    void humanReviewPatchRequestsAreAllowed() {
        CorsConfiguration cors = configuration("");

        assertTrue(cors.getAllowedMethods().contains("PATCH"));
    }

    private CorsConfiguration configuration(String configuredOrigins) {
        ExposedCorsRegistry registry = new ExposedCorsRegistry();
        new CorsConfig(configuredOrigins).addCorsMappings(registry);
        return registry.configurations().get("/api/**");
    }

    static class ExposedCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}
