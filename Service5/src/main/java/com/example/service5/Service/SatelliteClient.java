package com.example.service5.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service5.DTO.MostWatchedCategoryMessage;
import com.example.service5.DTO.UserDTO;
import com.example.service5.DTO.VideoDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private StompSession session;
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

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

        client.setMessageConverter(new MappingJackson2MessageConverter());

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
                List<VideoDTO> videos = fetchVideos();

                for (UserDTO user : users) {

                    String bestCategory = calculateMostPopularCategory(videos);

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
                            "Sending #{} → user {} → ({})",
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
       FETCH USERS FROM REST
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
       FETCH VIDEOS FROM REST
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
       CALCULATE MOST POPULAR CATEGORY
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
