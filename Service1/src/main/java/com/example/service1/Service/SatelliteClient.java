package com.example.service1.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service1.Config.RabbitMQSatelliteConfig;
import com.example.service1.DTO.MostWatchedCategoryMessage;
import com.example.service1.DTO.UserDTO;
import com.example.service1.DTO.WatchHistoryDTO;
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
 * Satelita Service1 – wysyła głosy do MainService przez RabbitMQ.
 *
 * Routing key: vote.{serviceName}  np. "vote.Service1"
 * Exchange:    votes.topic (Topic Exchange, deklarowany przez MainService)
 *
 * Skopiuj do service2 / service3 – zmień tylko package i domyślne wartości
 * @Value (satellite.name, satellite.weight).
 */
@Component
public class SatelliteClient {

    private static final Logger logger =
            LoggerFactory.getLogger(SatelliteClient.class);

    private static final String SERVICE_API_KEY   = "SUPER_SECRET_SERVICE_KEY_123";
    private static final String USERS_URL          = "http://localhost:8080/api/users/all";
    private static final String WATCH_HISTORY_BASE = "http://localhost:8080/api/history/get/";

    @Value("${satellite.name:Service1}")
    private String serviceName;

    @Value("${satellite.weight:2.0}")
    private double weight;

    private final RabbitTemplate rabbitTemplate;
    private final AtomicInteger  messageCounter = new AtomicInteger(0);
    private final HttpClient     httpClient     = HttpClient.newHttpClient();
    private final ObjectMapper   mapper;

    public SatelliteClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /* ================================================================
       MAIN LOOP – startuje 10 s po uruchomieniu, powtarza co 30 s
       ================================================================ */

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
                logger.info("{} → przetwarzam {} użytkowników", serviceName, users.size());

                for (UserDTO user : users) {

                    List<WatchHistoryDTO> history = fetchWatchHistory(user.id());
                    String bestCategory = calculateMostWatchedCategory(history);

                    MostWatchedCategoryMessage payload =
                            new MostWatchedCategoryMessage(user.id(), bestCategory);

                    ServiceMessage message =
                            new ServiceMessage(serviceName, payload, weight);

                    // Routing key: vote.Service1 / vote.Service2 / …
                    String routingKey = "vote." + serviceName;

                    rabbitTemplate.convertAndSend(
                            RabbitMQSatelliteConfig.VOTES_EXCHANGE,
                            routingKey,
                            message
                    );

                    int msgNum = messageCounter.incrementAndGet();
                    logger.info("#{} → [{}] user={} category={}",
                            msgNum, routingKey, user.id(), bestCategory);

                    Thread.sleep(300); // throttle między użytkownikami
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("{} loop przerwany", serviceName);
            } catch (Exception e) {
                logger.error("{} błąd w pętli głównej", serviceName, e);
            }

        }, 10, 30, TimeUnit.SECONDS);
    }

    /* ================================================================
       HTTP – pobierz użytkowników
       ================================================================ */

    private List<UserDTO> fetchUsers() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(USERS_URL))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY)
                    .GET().build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return Arrays.asList(mapper.readValue(response.body(), UserDTO[].class));
        } catch (Exception e) {
            logger.error("Nie można pobrać użytkowników", e);
            return List.of();
        }
    }

    /* ================================================================
       HTTP – historia oglądania dla użytkownika
       ================================================================ */

    private List<WatchHistoryDTO> fetchWatchHistory(int userId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WATCH_HISTORY_BASE + userId))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY)
                    .GET().build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return Arrays.asList(mapper.readValue(response.body(), WatchHistoryDTO[].class));
        } catch (Exception e) {
            logger.error("Nie można pobrać historii dla user {}", userId, e);
            return List.of();
        }
    }

    /* ================================================================
       LOGIKA – najczęściej oglądana kategoria
       ================================================================ */

    private String calculateMostWatchedCategory(List<WatchHistoryDTO> history) {
        return history.stream()
                .filter(h -> h.getCategory() != null)
                .collect(Collectors.groupingBy(
                        WatchHistoryDTO::getCategory,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
    }
}