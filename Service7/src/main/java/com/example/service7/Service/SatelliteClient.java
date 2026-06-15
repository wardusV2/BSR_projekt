package com.example.service7.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service7.DTO.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * SERVICE7
 *
 * Algorytm:
 *  - subskrypcje
 *  - lajki
 *  - merge kategorii
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

    private static final Path MAIN_SERVICE_KEYS_FILE =
            Paths.get("../MainService/src/main/java/com/example/mainservice/Config/satellite-keys.properties");
    private static final Path PRIVATE_KEY_FILE =
            Paths.get("keys", "service7-private.properties");

    /* ================= CONFIG ================= */

    @Value("${satellite.name:Service7}")
    private String serviceName;

    @Value("${satellite.weight:0.9}")
    private double weight;

    @Value("${fault.injection.crash:0.2}")
    private double crashProbability;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    /* ================= STATE ================= */

    private final RabbitTemplate rabbitTemplate;
    private final AtomicInteger counter = new AtomicInteger();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public SatelliteClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /* =========================================================
       LOOP – start po 15s, co 40s
       ========================================================= */
    @PostConstruct
    public void init() {
        loadOrCreateKeys();
        startLoop();
    }

    private void loadOrCreateKeys() {
        try {

            if (Files.exists(PRIVATE_KEY_FILE)) {
                loadPrivateKey();
                logger.info("{} -> załadowano istniejące klucze", serviceName);
                return;
            }

            generateAndSaveKeys();

            logger.info("{} -> wygenerowano nową parę kluczy", serviceName);

        } catch (Exception e) {
            throw new IllegalStateException("Nie można załadować kluczy", e);
        }
    }

    private void generateAndSaveKeys() throws Exception {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(256);

        KeyPair keyPair = kpg.generateKeyPair();

        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();

        savePrivateKey();
        savePublicKeyToMainService();
    }
    private void savePrivateKey() throws Exception {

        Files.createDirectories(PRIVATE_KEY_FILE.getParent());

        Properties props = new Properties();

        props.setProperty(
                "privateKey",
                Base64.getEncoder().encodeToString(
                        privateKey.getEncoded()
                )
        );

        props.setProperty(
                "publicKey",
                Base64.getEncoder().encodeToString(
                        publicKey.getEncoded()
                )
        );

        try (OutputStream out = Files.newOutputStream(PRIVATE_KEY_FILE)) {
            props.store(out, "Satellite private/public key");
        }
    }

    private void loadPrivateKey() throws Exception {

        Properties props = new Properties();

        try (InputStream in = Files.newInputStream(PRIVATE_KEY_FILE)) {
            props.load(in);
        }

        String privateKeyBase64 = props.getProperty("privateKey");
        String publicKeyBase64 = props.getProperty("publicKey");

        KeyFactory keyFactory = KeyFactory.getInstance("EC");

        this.privateKey = keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(privateKeyBase64)
                )
        );

        this.publicKey = keyFactory.generatePublic(
                new X509EncodedKeySpec(
                        Base64.getDecoder().decode(publicKeyBase64)
                )
        );
    }
    private void savePublicKeyToMainService() throws Exception {

        Properties props = new Properties();

        if (Files.exists(MAIN_SERVICE_KEYS_FILE)) {
            try (InputStream in = Files.newInputStream(MAIN_SERVICE_KEYS_FILE)) {
                props.load(in);
            }
        }

        String publicKeyBase64 =
                Base64.getEncoder().encodeToString(publicKey.getEncoded());

        props.setProperty(serviceName, publicKeyBase64);

        try (OutputStream out = Files.newOutputStream(MAIN_SERVICE_KEYS_FILE)) {
            props.store(out, "Satellite public keys");
        }

        logger.info(
                "{} -> zapisano klucz publiczny do {}",
                serviceName,
                MAIN_SERVICE_KEYS_FILE.toAbsolutePath()
        );
    }


    public void startLoop() {

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, serviceName + "-loop");
                    t.setDaemon(true);
                    return t;
                });

        scheduler.scheduleAtFixedRate(() -> {

            try {

                /* ===============================
                   FAULT INJECTION: CRASH
                   =============================== */

                if (random.nextDouble() < crashProbability) {

                    logger.error("💥 FAULT INJECTION → SERVICE7 CRASH");

                    System.exit(1);
                }

                List<UserDTO> users = fetchUsers();

                logger.info("{} → users={}", serviceName, users.size());

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
                            combinedCategory
                    );

                    Thread.sleep(250);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("{} loop przerwany", serviceName);
            } catch (Exception e) {
                logger.error("Service7 loop error", e);
            }

        }, 5, 40, TimeUnit.SECONDS);
    }

    /* =========================================================
       USERS
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
       SUBSCRIPTIONS
       ========================================================= */

    private List<SubscribedUserDTO> fetchSubscriptions(int userId) {

        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
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

            } catch (Exception e) {
                logger.warn("Cannot fetch videos for {}", user.id());
            }
        }

        return result;
    }

    /* =========================================================
       LIKES
       ========================================================= */

    private List<LikedVideoDTO> fetchLikedVideos(int userId) {

        try {
            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(
                                    USER_LIKED_URL + userId + "/liked"
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
                            LikedVideoDTO[].class
                    )
            );

        } catch (Exception e) {
            logger.warn("No liked videos for user {}", userId);
            return List.of();
        }
    }

    /* =========================================================
       CATEGORY LOGIC
       ========================================================= */

    private String calculateCategoryFromVideos(List<VideoDTO> videos) {

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