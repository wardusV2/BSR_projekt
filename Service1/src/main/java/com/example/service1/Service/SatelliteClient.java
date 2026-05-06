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
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.Base64;

/**
 * Satelita Service1 – wysyła podpisane głosy do MainService przez RabbitMQ.
 *
 * Zmiany względem oryginału (warstwa security):
 * ──────────────────────────────────────────────
 * 1. Przy starcie generuje parę kluczy ECDSA (secp256r1).
 *    Klucz publiczny jest logowany w formacie Base64 – należy go
 *    skopiować do pliku config/satellite-keys.properties w MainService.
 *
 * 2. Każdy głos jest podpisywany kluczem prywatnym przed wysłaniem.
 *    Payload podpisu: serviceName|category|weight|timestamp
 *
 * 3. ServiceMessage zawiera teraz pola signature i timestamp.
 *
 * Uwaga produkcyjna:
 * ──────────────────
 * Klucz prywatny powinien być generowany raz i przechowywany
 * w bezpiecznym magazynie (HSM, Vault, Keystore). Tu generowany
 * przy każdym starcie dla uproszczenia – w produkcji zastąp przez
 * SatelliteKeyManager.
 */
@Component
public class SatelliteClient {

    private static final Logger logger = LoggerFactory.getLogger(SatelliteClient.class);

    private static final String SERVICE_API_KEY   = "SUPER_SECRET_SERVICE_KEY_123";
    private static final String USERS_URL          = "http://localhost:8080/api/users/all";
    private static final String WATCH_HISTORY_BASE = "http://localhost:8080/api/history/get/";
    private static final String SIG_ALGORITHM      = "SHA256withECDSA";

    @Value("${satellite.name:Service1}")
    private String serviceName;

    @Value("${satellite.weight:2.0}")
    private double weight;

    private final RabbitTemplate   rabbitTemplate;
    private final AtomicInteger    messageCounter = new AtomicInteger(0);
    private final HttpClient       httpClient     = HttpClient.newHttpClient();
    private final ObjectMapper     mapper;

    // ── Klucze ECDSA ──────────────────────────────────────────────────────────
    private PrivateKey privateKey;
    private PublicKey  publicKey;

    public SatelliteClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    // ── Inicjalizacja: generowanie kluczy + start pętli ──────────────────────

    @PostConstruct
    public void init() {
        generateKeyPair();
        startSendingLoop();
    }

    /**
     * Generuje parę kluczy ECDSA (secp256r1) dla tej satelity.
     * Klucz publiczny loguje w Base64 – należy go zarejestrować w MainService.
     */
    private void generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256); // secp256r1
            KeyPair keyPair = kpg.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey  = keyPair.getPublic();

            String publicKeyB64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());

            logger.info("╔══════════════════════════════════════════════════════════╗");
            logger.info("║  {} – KLUCZ PUBLICZNY (skopiuj do satellite-keys.properties)", serviceName);
            logger.info("║  {}={}", serviceName, publicKeyB64);
            logger.info("╚══════════════════════════════════════════════════════════╝");

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Nie można wygenerować kluczy ECDSA", e);
        }
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
                    String bestCategory = calculateMostWatchedCategory(history);

                    // Zbuduj i podpisz głos
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
                logger.warn("{} loop przerwany", serviceName);
            } catch (Exception e) {
                logger.error("{} błąd w pętli głównej", serviceName, e);
            }
        }, 5, 25, TimeUnit.SECONDS);
    }

    // ── Budowanie i podpisywanie głosu ────────────────────────────────────────

    /**
     * Tworzy ServiceMessage z podpisem ECDSA.
     *
     * Payload do podpisu: serviceName|category|weight|timestamp
     * (musi być identyczny z WbftAlgorithm.buildPayload())
     */
    private ServiceMessage buildSignedVote(int userId, String category) throws GeneralSecurityException {
        long timestamp = System.currentTimeMillis();

        MostWatchedCategoryMessage payload =
                new MostWatchedCategoryMessage(userId, category);

        // Payload podpisywany przez węzeł – identyczny format jak w WbftAlgorithm
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