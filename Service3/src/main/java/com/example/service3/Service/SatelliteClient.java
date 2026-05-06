package com.example.service3.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service3.DTO.LikedVideoDTO;
import com.example.service3.DTO.SubscribedCategoryMessage;
import com.example.service3.DTO.UserDTO;
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

    /* ================= API ================= */

    private static final String USERS_URL =
            "http://localhost:8080/api/users/all";

    private static final String USER_LIKED_URL =
            "http://localhost:8080/users/";

    private static final String SERVICE_API_KEY =
            "SUPER_SECRET_SERVICE_KEY_123";

    /* ================= CONFIG ================= */

    @Value("${satellite.name:Service3}")
    private String serviceName;

    @Value("${satellite.weight:1.0}")
    private double weight;

    /* ================= STATE ================= */

    private final RabbitTemplate rabbitTemplate;
    private final AtomicInteger counter = new AtomicInteger();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public SatelliteClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /* =========================================================
       LOOP – start po 10s, co 45s
       ========================================================= */

    @PostConstruct
    public void startLoop() {

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, serviceName + "-loop");
                    t.setDaemon(true);
                    return t;
                });

        scheduler.scheduleAtFixedRate(() -> {

            try {
                List<UserDTO> users = fetchUsers();

                logger.info("{} → przetwarzam {} użytkowników",
                        serviceName, users.size());

                for (UserDTO user : users) {

                    List<LikedVideoDTO> likedVideos =
                            fetchLikedVideos(user.id());

                    String bestCategory =
                            calculateCategoryFromLikes(likedVideos);

                    ServiceMessage message =
                            new ServiceMessage(
                                    serviceName,
                                    new SubscribedCategoryMessage(
                                            user.id(),
                                            bestCategory
                                    ),
                                    weight
                            );

                    String routingKey = "vote." + serviceName;

                    rabbitTemplate.convertAndSend(
                            "votes.topic",
                            routingKey,
                            message
                    );

                    int msgNum = counter.incrementAndGet();

                    logger.info(
                            "#{} → [{}] user={} category={}",
                            msgNum,
                            routingKey,
                            user.id(),
                            bestCategory
                    );

                    Thread.sleep(200);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("{} loop przerwany", serviceName);
            } catch (Exception e) {
                logger.error("{} błąd w pętli", serviceName, e);
            }

        }, 5, 25, TimeUnit.SECONDS);
    }

    /* =========================================================
       USERS
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
                    mapper.readValue(response.body(), UserDTO[].class)
            );

        } catch (Exception e) {
            logger.error("Cannot fetch users", e);
            return List.of();
        }
    }

    /* =========================================================
       LIKED VIDEOS
       ========================================================= */

    private List<LikedVideoDTO> fetchLikedVideos(int userId) {

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USER_LIKED_URL + userId + "/liked"))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            return Arrays.asList(
                    mapper.readValue(response.body(), LikedVideoDTO[].class)
            );

        } catch (Exception e) {
            logger.warn("No liked videos for user {}", userId, e);
            return List.of();
        }
    }

    /* =========================================================
       CATEGORY LOGIC
       ========================================================= */

    private String calculateCategoryFromLikes(List<LikedVideoDTO> videos) {

        if (videos.isEmpty()) {
            return "OTHER";
        }

        return videos.stream()
                .map(LikedVideoDTO::category)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        c -> c,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("OTHER");
    }
}