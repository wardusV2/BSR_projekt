package com.example.service2.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service2.DTO.*;
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

    private static final String SUBSCRIPTIONS_URL =
            "http://localhost:8080/getSubscriptions/";

    private static final String USER_VIDEOS_URL =
            "http://localhost:8080/videosByUser/";

    private static final String SERVICE_API_KEY =
            "SUPER_SECRET_SERVICE_KEY_123";

    /* ================= CONFIG ================= */

    @Value("${satellite.name:Service2}")
    private String serviceName;

    @Value("${satellite.weight:1.2}")
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

                    List<SubscribedUserDTO> subscriptions =
                            fetchSubscriptions(user.id());

                    List<VideoDTO> videos =
                            fetchVideosOfSubscribedUsers(subscriptions);

                    String bestCategory =
                            calculateCategory(videos);

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
                            "votes.topic",   // exchange
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

                    Thread.sleep(200); // throttle
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("{} loop przerwany", serviceName);
            } catch (Exception e) {
                logger.error("{} błąd w pętli", serviceName, e);
            }

        }, 10, 45, TimeUnit.SECONDS);
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
       SUBSCRIPTIONS
       ========================================================= */

    private List<SubscribedUserDTO> fetchSubscriptions(int userId) {

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUBSCRIPTIONS_URL + userId))
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
                            SubscribedUserDTO[].class
                    )
            );

        } catch (Exception e) {
            logger.warn("No subscriptions for user {}", userId);
            return List.of();
        }
    }

    /* =========================================================
       VIDEOS OF SUBSCRIBED USERS
       ========================================================= */

    private List<VideoDTO> fetchVideosOfSubscribedUsers(
            List<SubscribedUserDTO> users
    ) {

        List<VideoDTO> result = new ArrayList<>();

        for (SubscribedUserDTO user : users) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(
                                USER_VIDEOS_URL + user.id()
                        ))
                        .header("X-SERVICE-KEY", SERVICE_API_KEY)
                        .GET()
                        .build();

                HttpResponse<String> response =
                        httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                result.addAll(
                        Arrays.asList(
                                mapper.readValue(
                                        response.body(),
                                        VideoDTO[].class
                                )
                        )
                );

            } catch (Exception ignored) {}
        }

        return result;
    }

    /* =========================================================
       CATEGORY LOGIC
       ========================================================= */

    private String calculateCategory(List<VideoDTO> videos) {

        return videos.stream()
                .map(VideoDTO::category)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        c -> c,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
    }
}