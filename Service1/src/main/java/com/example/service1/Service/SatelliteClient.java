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
import java.util.Base64;
import java.util.Properties;

/**
 * Satelita Service1 – wysyła podpisane głosy do MainService przez RabbitMQ.
 *
 * Zmiany względem oryginału:
 * ──────────────────────────
 * + śledzenie stanu pętli (loopRunning, lastFailureReason)
 * + metoda getHealthStatus() dla SatelliteHealthIndicator
 * Pozostała logika bez zmian.
 */
@Component
public class SatelliteClient {

    private static final Logger logger = LoggerFactory.getLogger(SatelliteClient.class);

    private static final String SERVICE_API_KEY   = "SUPER_SECRET_SERVICE_KEY_123";
    private static final String USERS_URL          = "http://localhost:8080/api/users/all";
    private static final String WATCH_HISTORY_BASE = "http://localhost:8080/api/history/get/";
    private static final String SIG_ALGORITHM      = "SHA256withECDSA";

    private static final Path MAIN_SERVICE_KEYS_FILE =
            Paths.get("../MainService/src/main/java/com/example/mainservice/Config/satellite-keys.properties");
    private static final Path PRIVATE_KEY_FILE =
            Paths.get("keys", "service1-private.properties");

    @Value("${satellite.name:Service1}")
    private String serviceName;

    @Value("${satellite.weight:2.0}")
    private double weight;

    private final RabbitTemplate   rabbitTemplate;
    private final AtomicInteger    messageCounter = new AtomicInteger(0);
    private final HttpClient       httpClient     = HttpClient.newHttpClient();
    private final ObjectMapper     mapper;

    // ── Stan zdrowia pętli ────────────────────────────────────────────────────
    private final AtomicBoolean         loopRunning     = new AtomicBoolean(false);
    private final AtomicReference<String> failureReason = new AtomicReference<>("");

    // ── Klucze ECDSA ──────────────────────────────────────────────────────────
    private PrivateKey privateKey;
    private PublicKey  publicKey;

    public SatelliteClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    // ── Inicjalizacja ─────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        loadOrCreateKeys();
        startSendingLoop();
    }

    // ── Health status (dla SatelliteHealthIndicator) ──────────────────────────

    /**
     * Zwraca aktualny stan zdrowia satelity.
     * Wywoływane przez SatelliteHealthIndicator → /actuator/health.
     */
    public HealthStatus getHealthStatus() {
        return new HealthStatus(
                serviceName,
                loopRunning.get(),
                messageCounter.get(),
                failureReason.get()
        );
    }

    /**
     * Snapshot stanu zdrowia satelity.
     *
     * @param serviceName   nazwa satelity
     * @param loopRunning   czy pętla wysyłania działa poprawnie
     * @param messagesSent  łączna liczba wysłanych głosów
     * @param failureReason przyczyna awarii (pusta gdy UP)
     */
    public record HealthStatus(
            String  serviceName,
            boolean loopRunning,
            int     messagesSent,
            String  failureReason
    ) {}

    // ── Klucze ───────────────────────────────────────────────────────────────

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
        this.publicKey  = keyPair.getPublic();
        savePrivateKey();
        savePublicKeyToMainService();
    }

    private void savePrivateKey() throws Exception {
        Files.createDirectories(PRIVATE_KEY_FILE.getParent());
        Properties props = new Properties();
        props.setProperty("privateKey",
                Base64.getEncoder().encodeToString(privateKey.getEncoded()));
        props.setProperty("publicKey",
                Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        try (OutputStream out = Files.newOutputStream(PRIVATE_KEY_FILE)) {
            props.store(out, "Satellite private/public key");
        }
    }

    private void loadPrivateKey() throws Exception {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(PRIVATE_KEY_FILE)) {
            props.load(in);
        }
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        this.privateKey = keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(props.getProperty("privateKey"))));
        this.publicKey = keyFactory.generatePublic(
                new X509EncodedKeySpec(
                        Base64.getDecoder().decode(props.getProperty("publicKey"))));
    }

    private void savePublicKeyToMainService() throws Exception {
        Properties props = new Properties();
        if (Files.exists(MAIN_SERVICE_KEYS_FILE)) {
            try (InputStream in = Files.newInputStream(MAIN_SERVICE_KEYS_FILE)) {
                props.load(in);
            }
        }
        props.setProperty(serviceName,
                Base64.getEncoder().encodeToString(publicKey.getEncoded()));
        try (OutputStream out = Files.newOutputStream(MAIN_SERVICE_KEYS_FILE)) {
            props.store(out, "Satellite public keys");
        }
        logger.info("{} -> zapisano klucz publiczny do {}",
                serviceName, MAIN_SERVICE_KEYS_FILE.toAbsolutePath());
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
                // Oznacz pętlę jako działającą na starcie iteracji
                loopRunning.set(true);
                failureReason.set("");

                List<UserDTO> users = fetchUsers();
                logger.info("{} → przetwarzam {} użytkowników", serviceName, users.size());

                for (UserDTO user : users) {
                    List<WatchHistoryDTO> history = fetchWatchHistory(user.id());
                    String bestCategory = calculateMostWatchedCategory(history);

                    ServiceMessage message = buildSignedVote(user.id(), bestCategory);

                    String routingKey = "vote." + serviceName;
                    rabbitTemplate.convertAndSend(
                            RabbitMQSatelliteConfig.VOTES_EXCHANGE,
                            routingKey,
                            message
                    );

                    int msgNum = messageCounter.incrementAndGet();
                    logger.info("#{} → [{}] user={} category={}",
                            msgNum, routingKey, user.id(), bestCategory);

                    Thread.sleep(300);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                loopRunning.set(false);
                failureReason.set("Pętla przerwana (InterruptedException)");
                logger.warn("{} loop przerwany", serviceName);

            } catch (Exception e) {
                // Oznacz pętlę jako niezdrową – actuator zwróci DOWN
                loopRunning.set(false);
                failureReason.set(e.getClass().getSimpleName() + ": " + e.getMessage());
                logger.error("{} błąd w pętli głównej", serviceName, e);
                // Nie rzucamy dalej – scheduler uruchomi kolejną iterację
            }
        }, 5, 25, TimeUnit.SECONDS);

        // Oznacz jako DOWN dopóki pierwsza iteracja się nie wykona
        loopRunning.set(false);
        failureReason.set("Oczekiwanie na pierwszą iterację (5s opóźnienie startu)");
    }

    // ── Budowanie i podpisywanie głosu ────────────────────────────────────────

    private ServiceMessage buildSignedVote(int userId, String category) throws GeneralSecurityException {
        long timestamp = System.currentTimeMillis();
        MostWatchedCategoryMessage payload = new MostWatchedCategoryMessage(userId, category);

        String sigPayload = serviceName + "|" + category + "|" + weight + "|" + timestamp;
        Signature sig = Signature.getInstance(SIG_ALGORITHM);
        sig.initSign(privateKey);
        sig.update(sigPayload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(sig.sign());

        return new ServiceMessage(serviceName, payload, weight, signature, timestamp);
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

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

    // ── Logika kategorii ──────────────────────────────────────────────────────

    private String calculateMostWatchedCategory(List<WatchHistoryDTO> history) {
        return history.stream()
                .filter(h -> h.getCategory() != null)
                .collect(Collectors.groupingBy(
                        WatchHistoryDTO::getCategory, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
    }
}