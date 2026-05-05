package com.webproject.safelogin.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.net.URI;

@Component
public class StartupLogger {

    @PostConstruct
    public void logSwaggerUrl() {
        System.out.println("✅ Swagger UI dostępny pod: http://localhost:8080/swagger-ui.html");
    }
}
