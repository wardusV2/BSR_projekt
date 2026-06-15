package com.example.service4.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service4.DTO.MostWatchedCategoryMessage;
import com.example.service4.DTO.UserDTO;
import com.example.service4.DTO.WatchHistoryDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class SatelliteClient {

    private static final Logger logger = LoggerFactory.getLogger(SatelliteClient.class);

    private static final String SERVICE_API_KEY      = "SUPER_SECRET_SERVICE_KEY_123";
    private static final String USERS_URL            = "http://localhost:8080/api/users/all";
    private static final String WATCH_HISTORY_BASE   = "http://localhost:8080/api/history/get/";
    private static final String SIG_ALGORITHM        = "SHA256withECDSA";

    private PrivateKey privateKey;
    private PublicKey publicKey;

    private static final Path MAIN_SERVICE_KEYS_FILE =
            Paths.get("../MainService/src/main/java/com/example/mainservice/Config/satellite-keys.properties");
    private static final Path PRIVATE_KEY_FILE =
            Paths.get("keys", "service4-private.properties");

    @Value("${satellite.name:Service4}")
    private String serviceName;

    @Value("${satellite.weight:1.0}")
    private double weight;

    private final RabbitTemplate rabbitTemplate;
    private final AtomicInteger  messageCounter = new AtomicInteger(0);
    private final HttpClient     httpClient     = HttpClient.newHttpClient();
    private final ObjectMapper   mapper         = new ObjectMapper();



    public SatelliteClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper.registerModule(new JavaTimeModule());
    }

    // ── Inicjalizacja ─────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        loadOrCreateKeys();
        startSendingLoop();
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

    private void startSendingLoop() {
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
                    String rarestCategory = calculateRarestCategory(history);

                    ServiceMessage message = buildSignedVote(user.id(), rarestCategory);

                    String routingKey = "vote." + serviceName;
                    rabbitTemplate.convertAndSend("votes.topic", routingKey, message);

                    logger.info("#{} → [{}] user={} category={}",
                            messageCounter.incrementAndGet(), routingKey, user.id(), rarestCategory);

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
                new MostWatchedCategoryMessage(userId, category),
                weight,
                signature,
                timestamp
        );
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private List<UserDTO> fetchUsers() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(USERS_URL))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return Arrays.asList(mapper.readValue(res.body(), UserDTO[].class));
        } catch (Exception e) {
            logger.error("Cannot fetch users", e);
            return List.of();
        }
    }

    private List<WatchHistoryDTO> fetchWatchHistory(int userId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(WATCH_HISTORY_BASE + userId))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return Arrays.asList(mapper.readValue(res.body(), WatchHistoryDTO[].class));
        } catch (Exception e) {
            logger.error("Cannot fetch history for user {}", userId, e);
            return List.of();
        }
    }

    // ── Logika kategorii ──────────────────────────────────────────────────────

    private String calculateRarestCategory(List<WatchHistoryDTO> history) {
        if (history.isEmpty()) return "OTHER";
        return history.stream()
                .filter(h -> h.getCategory() != null)
                .collect(Collectors.groupingBy(WatchHistoryDTO::getCategory, Collectors.counting()))
                .entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("OTHER");
    }
}