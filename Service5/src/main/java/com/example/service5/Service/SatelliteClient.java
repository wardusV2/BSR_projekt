package com.example.service5.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service5.DTO.MostWatchedCategoryMessage;
import com.example.service5.DTO.UserDTO;
import com.example.service5.DTO.VideoDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class SatelliteClient {

    private static final Logger logger =
            LoggerFactory.getLogger(SatelliteClient.class);

    private static final String SERVICE_API_KEY =
            "SUPER_SECRET_SERVICE_KEY_123";

    private static final String USERS_URL =
            "http://localhost:8080/api/users/all";

    private static final String VIDEOS_URL =
            "http://localhost:8080/AllVideos";

    @Value("${satellite.name:Service5}")
    private String serviceName;

    @Value("${satellite.weight:1.0}")
    private double weight;

    private final RabbitTemplate rabbitTemplate;
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public SatelliteClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /* =========================================================
       LOOP – start po 10s, co 30s
       ========================================================= */

    @PostConstruct
    public void startSendingLoop() {

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, serviceName + "-loop");
                    t.setDaemon(true);
                    return t;
                });

        scheduler.scheduleAtFixedRate(() -> {

            try {
                List<UserDTO> users = fetchUsers();
                List<VideoDTO> videos = fetchVideos();

                logger.info("{} → users={}, videos={}",
                        serviceName, users.size(), videos.size());

                for (UserDTO user : users) {

                    String bestCategory =
                            calculateMostPopularCategory(videos);

                    MostWatchedCategoryMessage payload =
                            new MostWatchedCategoryMessage(
                                    user.id(),
                                    bestCategory
                            );

                    ServiceMessage message =
                            new ServiceMessage(
                                    serviceName,
                                    payload,
                                    weight
                            );

                    String routingKey = "vote." + serviceName;

                    rabbitTemplate.convertAndSend(
                            "votes.topic",
                            routingKey,
                            message
                    );

                    int msgNum = messageCounter.incrementAndGet();

                    logger.info(
                            "#{} → [{}] user={} category={}",
                            msgNum,
                            routingKey,
                            user.id(),
                            bestCategory
                    );

                    Thread.sleep(300);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("{} loop przerwany", serviceName);
            } catch (Exception e) {
                logger.error("{} błąd w pętli", serviceName, e);
            }

        }, 10, 30, TimeUnit.SECONDS);
    }

    /* =========================================================
       FETCH USERS
       ========================================================= */

    private List<UserDTO> fetchUsers() {

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USERS_URL))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            return Arrays.asList(
                    mapper.readValue(
                            response.body(),
                            UserDTO[].class
                    )
            );

        } catch (Exception e) {
            logger.error("Cannot fetch users", e);
            return List.of();
        }
    }

    /* =========================================================
       FETCH VIDEOS
       ========================================================= */

    private List<VideoDTO> fetchVideos() {

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VIDEOS_URL))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            return Arrays.asList(
                    mapper.readValue(
                            response.body(),
                            VideoDTO[].class
                    )
            );

        } catch (Exception e) {
            logger.error("Cannot fetch videos", e);
            return List.of();
        }
    }

    /* =========================================================
       GLOBAL TREND (najpopularniejsza kategoria)
       ========================================================= */

    private String calculateMostPopularCategory(List<VideoDTO> videos) {

        return videos.stream()
                .filter(v -> v.getCategory() != null)
                .collect(Collectors.groupingBy(
                        VideoDTO::getCategory,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
    }
}