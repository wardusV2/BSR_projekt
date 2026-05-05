package com.example.mainservice.Service;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
@Component
public class RecommendationClient {
    private static final Logger logger =
            LoggerFactory.getLogger(RecommendationClient.class);

    private static final String SERVICE_KEY = "SUPER_SECRET_SERVICE_KEY_123";

    private static final String BASE_URL =
            "http://localhost:8080/recommendations/user/";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void saveRecommendation(Integer userId, String category) {

        try {
            URI uri = URI.create(
                    BASE_URL + userId + "?category=" + category
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("X-SERVICE-KEY", SERVICE_KEY)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            logger.info(
                    "Recommendation saved → user={}, category={}, status={}",
                    userId, category, response.statusCode()
            );

        } catch (Exception e) {
            logger.error("Failed to save recommendation", e);
        }
    }
}
