package com.example.service3.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service3.DTO.LikedVideoDTO;
import com.example.service3.DTO.SubscribedCategoryMessage;
import com.example.service3.DTO.UserDTO;
import com.example.service3.Fault.FaultState;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public class SatelliteClient {

    private static final Logger logger = LoggerFactory.getLogger(SatelliteClient.class);

    private static final String USERS_URL       = "http://localhost:8080/api/users/all";
    private static final String USER_LIKED_URL   = "http://localhost:8080/users/";
    private static final String SERVICE_API_KEY  = "SUPER_SECRET_SERVICE_KEY_123";
    private static final String SIG_ALGORITHM    = "SHA256withECDSA";

    private PrivateKey privateKey;
    private PublicKey publicKey;

    private static final Path MAIN_SERVICE_KEYS_FILE =
            Paths.get("../MainService/src/main/java/com/example/mainservice/Config/satellite-keys.properties");
    private static final Path PRIVATE_KEY_FILE =
            Paths.get("keys", "service3-private.properties");

    private final AtomicBoolean loopRunning = new AtomicBoolean(false);
    private final AtomicReference<String> failureReason = new AtomicReference<>(null);
    private final Random random = new Random();

    @Value("${satellite.name:Service3}")
    private String serviceName;

    @Value("${satellite.weight:1.0}")
    private double weight;

    private final RabbitTemplate rabbitTemplate;
    private final AtomicInteger  counter    = new AtomicInteger();
    private final HttpClient     httpClient = HttpClient.newHttpClient();
    private final ObjectMapper   mapper     = new ObjectMapper();
    private final FaultState faultState;


    public SatelliteClient(RabbitTemplate rabbitTemplate, FaultState faultState) {
        this.rabbitTemplate = rabbitTemplate;
        this.faultState = faultState;
    }

    // ── Inicjalizacja ─────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        loadOrCreateKeys();
        startLoop();
    }
    private void applyFault() throws Exception {
        FaultState.FaultConfig cfg = faultState.get();

        switch (cfg.faultType()) {

            case NONE:
                return;

            case DELAY:
                logger.warn("FAULT DELAY {} ms", cfg.delayMs());
                Thread.sleep(cfg.delayMs());
                return;

            case DROP:
                if (random.nextInt(100) < cfg.dropRate()) {
                    logger.warn("FAULT DROP");
                    throw new RuntimeException("Message dropped");
                }
                return;

            case BYZANTINE:
                return;
        }
    }

    private boolean shouldDrop() {
        FaultState.FaultConfig cfg = faultState.get();

        return cfg.faultType() == FaultState.FaultType.DROP
                && random.nextInt(100) < cfg.dropRate();
    }

    private String applyByzantine(String category) {

        FaultState.FaultConfig cfg = faultState.get();

        if (cfg.faultType() != FaultState.FaultType.BYZANTINE) {
            return category;
        }

        return switch (cfg.byzantineCategory()) {

            case "RANDOM" -> {
                String[] categories = {
                        "SPORT",
                        "MUSIC",
                        "NEWS",
                        "GAMING",
                        "POLITICS"
                };
                yield categories[random.nextInt(categories.length)];
            }

            case "INVALID" -> "###INVALID###";

            case "NULL" -> null;

            default -> cfg.byzantineCategory();
        };
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

    // ── Główna pętla ──────────────────────────────────────────────────────────

    private void startLoop() {
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, serviceName + "-loop");
                    t.setDaemon(true);
                    return t;
                });

        scheduler.scheduleAtFixedRate(() -> {
            loopRunning.set(true);
            failureReason.set(null);
            try {
                List<UserDTO> users = fetchUsers();
                logger.info("{} → przetwarzam {} użytkowników", serviceName, users.size());

                for (UserDTO user : users) {

                    FaultState.FaultConfig fault = faultState.get();

                    if (fault.faultType() == FaultState.FaultType.OFFLINE) {
                        loopRunning.set(false);
                        failureReason.set("Tryb OFFLINE (fault injection)");

                        logger.warn("{} -> OFFLINE", serviceName);

                        Thread.sleep(5000);
                        return;
                    }

                    List<LikedVideoDTO> likedVideos =
                            fetchLikedVideos(user.id());

                    String bestCategory =
                            calculateCategoryFromLikes(likedVideos);

                    bestCategory =
                            applyByzantine(bestCategory);

                    if (shouldDrop()) {
                        logger.warn(
                                "FAULT DROP -> user {} skipped",
                                user.id()
                        );
                        continue;
                    }

                    applyFault();

                    ServiceMessage message =
                            buildSignedVote(
                                    user.id(),
                                    bestCategory
                            );

                    String routingKey =
                            "vote." + serviceName;

                    rabbitTemplate.convertAndSend(
                            "votes.topic",
                            routingKey,
                            message
                    );

                    logger.info(
                            "#{} → [{}] user={} category={}",
                            counter.incrementAndGet(),
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

    // ── Budowanie i podpisywanie głosu ────────────────────────────────────────

    private ServiceMessage buildSignedVote(int userId, String category)
            throws GeneralSecurityException {
        long timestamp = System.currentTimeMillis();
        String sigPayload = serviceName + "|" + category + "|" + weight + "|" + timestamp;

        Signature sig = Signature.getInstance(SIG_ALGORITHM);
        sig.initSign(privateKey);
        sig.update(sigPayload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(sig.sign());

        return new ServiceMessage(
                serviceName,
                new SubscribedCategoryMessage(userId, category),
                weight,
                signature,
                timestamp
        );
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private List<UserDTO> fetchUsers() {
        try {

            applyFault();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(USERS_URL))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> res =
                    httpClient.send(
                            req,
                            HttpResponse.BodyHandlers.ofString()
                    );

            return Arrays.asList(
                    mapper.readValue(
                            res.body(),
                            UserDTO[].class
                    )
            );

        } catch (Exception e) {
            logger.error("Cannot fetch users", e);
            return List.of();
        }
    }

    private List<LikedVideoDTO> fetchLikedVideos(int userId) {
        try {

            applyFault();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(USER_LIKED_URL + userId + "/liked"))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY)
                    .GET()
                    .build();

            HttpResponse<String> res =
                    httpClient.send(
                            req,
                            HttpResponse.BodyHandlers.ofString()
                    );

            return Arrays.asList(
                    mapper.readValue(
                            res.body(),
                            LikedVideoDTO[].class
                    )
            );

        } catch (Exception e) {
            logger.warn(
                    "No liked videos for user {}",
                    userId,
                    e
            );
            return List.of();
        }
    }

    // ── Logika kategorii ──────────────────────────────────────────────────────

    private String calculateCategoryFromLikes(List<LikedVideoDTO> videos) {
        if (videos.isEmpty()) return "OTHER";
        return videos.stream()
                .map(LikedVideoDTO::category)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("OTHER");
    }

    public record HealthStatus(
            String serviceName,
            int messagesSent,
            boolean loopRunning,
            String failureReason
    ) {}
    public HealthStatus getHealthStatus() {
        return new HealthStatus(
                serviceName,
                counter.get(),
                loopRunning.get(),
                failureReason.get()
        );
    }
}