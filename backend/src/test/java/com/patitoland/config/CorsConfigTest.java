package com.patitoland.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    @Test
    @DisplayName("corsConfigurer() devuelve una instancia de WebMvcConfigurer")
    void testCorsConfigurerReturnsWebMvcConfigurer() {
        WebMvcConfigurer configurer = corsConfig.corsConfigurer();
        assertNotNull(configurer, "El bean WebMvcConfigurer no debe ser null");
    }

    @Test
    @DisplayName("CorsConfig está anotada con @Configuration")
    void testClassIsAnnotatedAsConfiguration() {
        assertTrue(
                CorsConfig.class.isAnnotationPresent(
                        org.springframework.context.annotation.Configuration.class),
                "CorsConfig debe tener la anotación @Configuration"
        );
    }
}
