package com.example.service6.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service6.DTO.MostWatchedCategoryMessage;
import com.example.service6.DTO.UserDTO;
import com.example.service6.DTO.WatchHistoryDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

/**
 * SERVICE 6
 *
 * Algorytm: Najczęściej oglądana kategoria
 * Waga: 0.6
 *
 * Fault injection:
 * 30% - corrupted data
 */
@Component
public class SatelliteClient {

    private static final Logger logger =
            LoggerFactory.getLogger(SatelliteClient.class);

    private static final Random random = new Random();

    private static final String SERVICE_API_KEY =
            "SUPER_SECRET_SERVICE_KEY_123";

    private static final String USERS_URL =
            "http://localhost:8080/api/users/all";

    private static final String WATCH_HISTORY_BASE_URL =
            "http://localhost:8080/api/history/get/";

    @Value("${satellite.name:Service6}")
    private String serviceName;

    @Value("${satellite.weight:0.6}")
    private double weight;

    @Value("${fault.injection.corrupted-data:0.3}")
    private double corruptedDataProbability;

    private final RabbitTemplate rabbitTemplate;
    private final AtomicInteger messageCounter = new AtomicInteger(0);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public SatelliteClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper.registerModule(new JavaTimeModule());
    }

    /* =========================================================
       LOOP – start po 15s, co 20s
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

                logger.info("{} → przetwarzam {} użytkowników",
                        serviceName, users.size());

                for (UserDTO user : users) {

                    List<WatchHistoryDTO> history =
                            fetchWatchHistory(user.id());

                    String bestCategory =
                            calculateMostWatchedCategory(history);

                    /* ===============================
                       FAULT INJECTION
                       =============================== */
                    if (random.nextDouble() < corruptedDataProbability) {

                        bestCategory =
                                String.valueOf(random.nextInt(1000) + 999);

                        logger.warn(
                                "⚠️ FAULT INJECTION → corrupted category: {}",
                                bestCategory
                        );
                    }

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

                    int msgNum =
                            messageCounter.incrementAndGet();

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

        }, 5, 30, TimeUnit.SECONDS);
    }

    /* =========================================================
       FETCH USERS
       ========================================================= */

    private List<UserDTO> fetchUsers() {

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
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
       FETCH HISTORY
       ========================================================= */

    private List<WatchHistoryDTO> fetchWatchHistory(int userId) {

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(
                                    WATCH_HISTORY_BASE_URL + userId
                            ))
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
                            WatchHistoryDTO[].class
                    )
            );

        } catch (Exception e) {

            logger.error(
                    "Cannot fetch history for user {}",
                    userId,
                    e
            );

            return List.of();
        }
    }

    /* =========================================================
       CATEGORY LOGIC
       ========================================================= */

    private String calculateMostWatchedCategory(
            List<WatchHistoryDTO> history
    ) {

        return history.stream()
                .filter(h -> h.getCategory() != null)
                .collect(Collectors.groupingBy(
                        WatchHistoryDTO::getCategory,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
    }
}