package com.example.service1.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service1.DTO.MostWatchedCategoryMessage;
import com.example.service1.DTO.UserDTO;
import com.example.service1.DTO.WatchHistoryDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
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

    private static final String WATCH_HISTORY_BASE_URL =
            "http://localhost:8080/api/history/get/";

    @Value("${satellite.name:Service1}")
    private String serviceName;

    @Value("${satellite.weight:2.0}")
    private double weight;

    private StompSession session;
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public SatelliteClient() {
        mapper.registerModule(new JavaTimeModule());
    }

    /* =========================================================
        CONNECT TO MAIN SERVICE
       ========================================================= */
    @PostConstruct
    public void connect() {

        logger.info("{} connecting to MainService...", serviceName);

        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(
                        new WebSocketTransport(new StandardWebSocketClient())
                ))
        );

        client.setMessageConverter(
                new MappingJackson2MessageConverter()
        );

        client.connectAsync(
                "ws://localhost:8081/main-ws",
                new StompSessionHandlerAdapter() {

                    @Override
                    public void afterConnected(
                            StompSession session,
                            StompHeaders headers
                    ) {
                        logger.info("{} CONNECTED", serviceName);
                        SatelliteClient.this.session = session;
                        startSendingLoop();
                    }
                }
        );
    }

    /* =========================================================
        MAIN LOOP
       ========================================================= */
    private void startSendingLoop() {

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {

            if (session == null || !session.isConnected()) {
                logger.warn("WebSocket not connected");
                return;
            }

            try {
                List<UserDTO> users = fetchUsers();

                for (UserDTO user : users) {

                    List<WatchHistoryDTO> history =
                            fetchWatchHistory(user.id());

                    String bestCategory =
                            calculateMostWatchedCategory(history);

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

                    int msgNum = messageCounter.incrementAndGet();

                    logger.info(
                            "Sending #{} → user {} → {}",
                            msgNum,
                            user.id(),
                            bestCategory
                    );

                    session.send("/app/from-service", message);

                    Thread.sleep(300);
                }

            } catch (Exception e) {
                logger.error("Error in main loop", e);
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
        FETCH WATCH HISTORY
       ========================================================= */
    private List<WatchHistoryDTO> fetchWatchHistory(int userId) {

        try {
            HttpRequest request = HttpRequest.newBuilder()
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
                    "Cannot fetch watch history for user {}",
                    userId,
                    e
            );
            return List.of();
        }
    }

    /* =========================================================
        CALCULATE CATEGORY
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
