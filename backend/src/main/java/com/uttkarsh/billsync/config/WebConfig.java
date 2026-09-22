package com.uttkarsh.billsync.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.math.BigDecimal;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${billsync.cors.allowed-origins}")
    private String[] allowedOrigins;

    /**
     * Browsers block a page on one origin (the Vite dev server on :5173) from reading
     * responses from another (this API on :8080) unless the API says it is allowed.
     * This is that permission, scoped to the API paths and the dev origin only.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    /**
     * Serialise every BigDecimal as a JSON string: {@code "33.34"}, never {@code 33.34}.
     * A JSON number lands in JavaScript as a double and 33.34 is already inexact
     * there; a string reaches the client byte-for-byte and keeps its two decimals.
     */
    @Bean
    JsonMapperBuilderCustomizer moneyAsStrings() {
        return builder -> builder.withConfigOverride(BigDecimal.class,
                override -> override.setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING)));
    }
}
