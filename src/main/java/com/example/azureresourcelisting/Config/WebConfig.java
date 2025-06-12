package com.example.azureresourcelisting.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;    

@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {

    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
      registry.addMapping("/api/**") // Apply to ALL endpoints under /api/*
        // IMPORTANT: Whitelist the exact origin from the error message, plus its "localhost" equivalent.
        .allowedOrigins("http://localhost:3000", "http://127.0.0.1:3000") 
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allow all necessary methods
        .allowedHeaders("*") // Allow all headers to be sent
        .allowCredentials(true) // Crucial for sending session cookies
        .maxAge(3600); // Optional: cache preflight response for 1 hour
    }
}
