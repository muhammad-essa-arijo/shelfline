package com.shelfline.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the Vercel frontend call this API. The origin comes from ALLOWED_ORIGIN. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origins;

    public CorsConfig(@Value("${allowed.origin}") String allowedOrigin) {
        this.origins = allowedOrigin.trim().split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition");
    }
}
