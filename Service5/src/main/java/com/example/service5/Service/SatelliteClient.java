package com.example.service5.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.service5.DTO.MostWatchedCategoryMessage;
import com.example.service5.DTO.UserDTO;
import com.example.service5.DTO.VideoDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class SatelliteClient {

    private static final Logger logger = LoggerFactory.getLogger(SatelliteClient.class);

    private static final String SERVICE_API_KEY = "SUPER_SECRET_SERVICE_KEY_123";
    private static final String USERS_URL       = "http://localhost:8080/api/users/all";
    private static final String VIDEOS_URL      = "http://localhost:8080/AllVideos";
    private static final String SIG_ALGORITHM   = "SHA256withECDSA";

    @Value("${satellite.name:Service5}")
    private String serviceName;

    @Value("${satellite.weight:1.0}")
    private double weight;

    private final RabbitTemplate rabbitTemplate;
    private final AtomicInteger  messageCounter = new AtomicInteger(0);
    private final HttpClient     httpClient     = HttpClient.newHttpClient();
    private final ObjectMapper   mapper         = new ObjectMapper();

    private PrivateKey privateKey;

    public SatelliteClient(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // ── Inicjalizacja ─────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        generateKeyPair();
        startSendingLoop();
    }

    private void generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(256);
            KeyPair keyPair = kpg.generateKeyPair();
            this.privateKey = keyPair.getPrivate();

            String publicKeyB64 = Base64.getEncoder()
                    .encodeToString(keyPair.getPublic().getEncoded());

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
                List<UserDTO> users  = fetchUsers();
                List<VideoDTO> videos = fetchVideos();
                logger.info("{} → users={}, videos={}", serviceName, users.size(), videos.size());

                String bestCategory = calculateMostPopularCategory(videos);

                for (UserDTO user : users) {
                    ServiceMessage message = buildSignedVote(user.id(), bestCategory);

                    String routingKey = "vote." + serviceName;
                    rabbitTemplate.convertAndSend("votes.topic", routingKey, message);

                    logger.info("#{} → [{}] user={} category={}",
                            messageCounter.incrementAndGet(), routingKey, user.id(), bestCategory);

                    Thread.sleep(300);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("{} loop przerwany", serviceName);
            } catch (Exception e) {
                logger.error("{} błąd w pętli", serviceName, e);
            }
        }, 5, 32, TimeUnit.SECONDS);
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

    private List<VideoDTO> fetchVideos() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(VIDEOS_URL))
                    .header("X-SERVICE-KEY", SERVICE_API_KEY).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return Arrays.asList(mapper.readValue(res.body(), VideoDTO[].class));
        } catch (Exception e) {
            logger.error("Cannot fetch videos", e);
            return List.of();
        }
    }

    // ── Logika kategorii ──────────────────────────────────────────────────────

    private String calculateMostPopularCategory(List<VideoDTO> videos) {
        return videos.stream()
                .filter(v -> v.getCategory() != null)
                .collect(Collectors.groupingBy(VideoDTO::getCategory, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("NONE");
    }
}