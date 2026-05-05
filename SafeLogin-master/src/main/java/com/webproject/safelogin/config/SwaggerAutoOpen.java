package com.webproject.safelogin.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.net.URI;

@Component
public class SwaggerAutoOpen {

    @PostConstruct
    public void openSwaggerUi() {
        try {
            String url = "http://localhost:8080/swagger-ui.html";
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                System.out.println("Swagger UI available at: " + url);
            }
        } catch (Exception e) {
            System.err.println("Could not open Swagger UI: " + e.getMessage());
        }
    }
}

