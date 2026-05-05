package com.example.service7.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service7.DTO.*;
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

/**
 * SERVICE7
 *
 * Algorytm:
 * Połączenie kategorii z:
 *  - subskrypcji
 *  - polubień
 *
 * Fault:
 *  CRASH 20%
 */

@Component
public class SatelliteClient {

    private static final Logger logger =
            LoggerFactory.getLogger(SatelliteClient.class);

    private static final Random random = new Random();


    /* ================= API ================= */

    private static final String USERS_URL =
            "http://localhost:8080/api/users/all";

    private static final String SUBSCRIPTIONS_URL =
            "http://localhost:8080/getSubscriptions/";

    private static final String USER_VIDEOS_URL =
            "http://localhost:8080/videosByUser/";

    private static final String USER_LIKED_URL =
            "http://localhost:8080/users/";


    private static final String SERVICE_API_KEY =
            "SUPER_SECRET_SERVICE_KEY_123";


    /* ================= CONFIG ================= */

    @Value("${satellite.name:Service7}")
    private String serviceName;

    @Value("${satellite.weight:0.9}")
    private double weight;

    @Value("${fault.injection.crash:0.2}")
    private double crashProbability;


    /* ================= STATE ================= */

    private StompSession session;

    private final AtomicInteger counter =
            new AtomicInteger();

    private final HttpClient httpClient =
            HttpClient.newHttpClient();

    private final ObjectMapper mapper =
            new ObjectMapper();


    /* =========================================================
       CONNECT
       ========================================================= */

    @PostConstruct
    public void connect() {

        logger.info("{} connecting...", serviceName);

        WebSocketStompClient client =
                new WebSocketStompClient(
                        new SockJsClient(List.of(
                                new WebSocketTransport(
                                        new StandardWebSocketClient()
                                )
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

                        startLoop();
                    }
                }
        );
    }


    /* =========================================================
       LOOP
       ========================================================= */

    private void startLoop() {

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {

            if (session == null || !session.isConnected()) {
                logger.warn("WebSocket not connected");
                return;
            }

            try {

                /* ===============================
                   FAULT INJECTION: CRASH
                   =============================== */

                if (random.nextDouble() < crashProbability) {

                    logger.error(
                            " FAULT INJECTION: SERVICE7 CRASH"
                    );

                    System.exit(1);
                }


                List<UserDTO> users = fetchUsers();

                for (UserDTO user : users) {

                    /* ---------- SUBSCRIPTIONS ---------- */

                    List<SubscribedUserDTO> subs =
                            fetchSubscriptions(user.id());

                    List<VideoDTO> videos =
                            fetchVideosOfSubscribedUsers(subs);

                    String subCategory =
                            calculateCategoryFromVideos(videos);


                    /* ---------- LIKES ---------- */

                    List<LikedVideoDTO> likes =
                            fetchLikedVideos(user.id());

                    String likedCategory =
                            calculateCategoryFromLikes(likes);


                    /* ---------- COMBINE ---------- */

                    String combinedCategory;

                    if (subCategory.equals(likedCategory)) {
                        combinedCategory = subCategory;
                    } else {
                        combinedCategory =
                                subCategory + "," + likedCategory;
                    }


                    ServiceMessage message =
                            new ServiceMessage(
                                    serviceName,
                                    new SubscribedCategoryMessage(
                                            user.id(),
                                            combinedCategory
                                    ),
                                    weight
                            );

                    int msgNum =
                            counter.incrementAndGet();

                    logger.info(
                            "Service7 #{} → user {} → {}",
                            msgNum,
                            user.id(),
                            combinedCategory
                    );

                    session.send(
                            "/app/from-service",
                            message
                    );

                    Thread.sleep(250);
                }

            } catch (Exception e) {

                logger.error(
                        "Service7 loop error",
                        e
                );
            }

        }, 15, 40, TimeUnit.SECONDS);
    }


    /* =========================================================
       👤 USERS
       ========================================================= */

    private List<UserDTO> fetchUsers() {

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(USERS_URL))
                            .header(
                                    "X-SERVICE-KEY",
                                    SERVICE_API_KEY
                            )
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
        SUBSCRIPTIONS
       ========================================================= */

    private List<SubscribedUserDTO> fetchSubscriptions(
            int userId
    ) {

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            SUBSCRIPTIONS_URL + userId
                                    )
                            )
                            .header(
                                    "X-SERVICE-KEY",
                                    SERVICE_API_KEY
                            )
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

            logger.warn(
                    "No subscriptions for user {}",
                    userId
            );

            return List.of();
        }
    }


    /* =========================================================
        VIDEOS
       ========================================================= */

    private List<VideoDTO> fetchVideosOfSubscribedUsers(
            List<SubscribedUserDTO> users
    ) {

        List<VideoDTO> result = new ArrayList<>();

        for (SubscribedUserDTO user : users) {

            try {

                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(
                                        URI.create(
                                                USER_VIDEOS_URL + user.id()
                                        )
                                )
                                .header(
                                        "X-SERVICE-KEY",
                                        SERVICE_API_KEY
                                )
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

            } catch (Exception e) {

                logger.warn(
                        "Cannot fetch videos for {}",
                        user.id()
                );
            }
        }

        return result;
    }


    /* =========================================================
        LIKES
       ========================================================= */

    private List<LikedVideoDTO> fetchLikedVideos(
            int userId
    ) {

        try {

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            USER_LIKED_URL + userId + "/liked"
                                    )
                            )
                            .header(
                                    "X-SERVICE-KEY",
                                    SERVICE_API_KEY
                            )
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
                            LikedVideoDTO[].class
                    )
            );

        } catch (Exception e) {

            logger.warn(
                    "No liked videos for user {}",
                    userId
            );

            return List.of();
        }
    }


    /* =========================================================
        CATEGORY LOGIC
       ========================================================= */

    private String calculateCategoryFromVideos(
            List<VideoDTO> videos
    ) {

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

                .orElse("OTHER");
    }


    private String calculateCategoryFromLikes(
            List<LikedVideoDTO> videos
    ) {

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
