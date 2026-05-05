package com.example.mainservice.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.mainservice.DTO.UserCategoryPayload;
import com.example.mainservice.DTO.WbftResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Koordynator WBFT – zbiera głosy satelit z RabbitMQ,
 * uruchamia algorytm gdy wszystkie głosy dotarły,
 * zapisuje wynik i powiadamia frontend przez WebSocket.
 */
@Service
public class VoteAggregatorService {

    private static final Logger logger =
            LoggerFactory.getLogger(VoteAggregatorService.class);

    private static final Set<String> EXPECTED_SERVICES = Set.of(
            "Service1", "Service2", "Service3",
            "Service4", "Service5", "Service6", "Service7"
    );

    // userId -> (serviceName -> message)
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String, ServiceMessage>>
            lastMessagesPerUser = new ConcurrentHashMap<>();

    // userId -> set(serviceName)
    private final ConcurrentHashMap<Integer, Set<String>>
            receivedServicesPerUser = new ConcurrentHashMap<>();

    private final WbftAlgorithm wbftAlgorithm;
    private final RecommendationClient recommendationClient;
    private final SimpMessagingTemplate wsTemplate;

    public VoteAggregatorService(
            WbftAlgorithm wbftAlgorithm,
            RecommendationClient recommendationClient,
            @Lazy SimpMessagingTemplate wsTemplate
    ) {
        this.wbftAlgorithm        = wbftAlgorithm;
        this.recommendationClient = recommendationClient;
        this.wsTemplate           = wsTemplate;
    }

    /* ================================================================
       GŁÓWNA METODA – wywołana przez VoteListener (RabbitMQ)
       ================================================================ */

    public void processVote(ServiceMessage message) {
        extractPayload(message).ifPresentOrElse(
                payload -> handleValidVote(message, payload),
                () -> logger.warn("Odrzucono wiadomość – brak poprawnego payload: {}", message)
        );
    }

    /* ================================================================
       OBSŁUGA GŁOSU
       ================================================================ */

    private void handleValidVote(ServiceMessage message, UserCategoryPayload payload) {

        Integer userId      = payload.userId();
        String  serviceName = message.getServiceName();

        lastMessagesPerUser
                .computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
                .put(serviceName, message);

        receivedServicesPerUser
                .computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet())
                .add(serviceName);

        int received = receivedServicesPerUser.get(userId).size();
        int expected = EXPECTED_SERVICES.size();

        logger.info("[WBFT] user={} głos od {} ({}/{}) serwisy={}",
                userId, serviceName, received, expected,
                receivedServicesPerUser.get(userId));

        if (receivedServicesPerUser.get(userId).containsAll(EXPECTED_SERVICES)) {
            runWbftForUser(userId);
        }
    }

    /* ================================================================
       URUCHOMIENIE WBFT
       ================================================================ */

    private void runWbftForUser(Integer userId) {

        Map<String, ServiceMessage> votes = lastMessagesPerUser.get(userId);

        logger.info("[WBFT] ══ Uruchamiam algorytm dla user={} ({} głosów) ══",
                userId, votes.size());

        WbftResult result = wbftAlgorithm.compute(votes);

        logWbftResult(userId, result);

        recommendationClient.saveRecommendation(userId, result.category());

        notifyFrontend(userId, result);

        // Reset – gotowość na kolejną rundę
        lastMessagesPerUser.remove(userId);
        receivedServicesPerUser.remove(userId);

        logger.info("[WBFT] ══ Runda zakończona dla user={} ══", userId);
    }

    /* ================================================================
       WEBSOCKET → FRONTEND
       ================================================================ */

    private void notifyFrontend(Integer userId, WbftResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId",            userId);
        payload.put("category",          result.category());
        payload.put("status",            result.status().name());
        payload.put("winnerRatio",       Math.round(result.winnerRatio() * 1000) / 10.0);
        payload.put("totalWeight",       result.totalWeight());
        payload.put("weightSums",        result.weightSums());
        payload.put("byzantineSuspects", result.byzantineSuspects());
        payload.put("voterCount",        result.voterCount());
        payload.put("confident",         result.isConfident());

        wsTemplate.convertAndSend("/topic/verdict", (Object) payload);
    }

    /* ================================================================
       PAYLOAD EXTRACTION
       ================================================================ */

    private Optional<UserCategoryPayload> extractPayload(ServiceMessage msg) {
        if (!(msg.getContent() instanceof Map<?, ?> map)) return Optional.empty();
        try {
            Integer userId   = (Integer) map.get("userId");
            String  category = (String)  map.get("category");
            if (userId == null || category == null) return Optional.empty();
            return Optional.of(new UserCategoryPayload(userId, category));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /* ================================================================
       LOGGING WYNIKU
       ================================================================ */

    private void logWbftResult(Integer userId, WbftResult result) {
        switch (result.status()) {
            case UNANIMOUS ->
                    logger.info("[WBFT] UNANIMOUS user={} → {}", userId, result.category());
            case CONSENSUS ->
                    logger.info("[WBFT] CONSENSUS user={} → {} ({:.1f}% wagi, {} byzantine suspects)",
                            userId, result.category(),
                            result.winnerRatio() * 100,
                            result.byzantineSuspects().size());
            case NO_QUORUM ->
                    logger.warn("[WBFT] NO_QUORUM user={} → brak consensusu, zapis OTHER. " +
                                    "Najlepszy kandydat miał {:.1f}% wagi (próg {}%)",
                            userId,
                            result.winnerRatio() * 100,
                            (int)(2.0 / 3.0 * 100));
            case NO_DATA ->
                    logger.error("[WBFT] NO_DATA user={} → brak danych do głosowania", userId);
        }

        if (!result.byzantineSuspects().isEmpty()) {
            logger.warn("[WBFT] ⚠ Byzantine suspects dla user={}: {}",
                    userId, result.byzantineSuspects());
        }
    }

    /* ================================================================
       DEBUG
       ================================================================ */

    public Map<String, Object> getDebugState() {
        return Map.of(
                "activeUsers",      lastMessagesPerUser.keySet(),
                "receivedServices", receivedServicesPerUser,
                "expectedServices", EXPECTED_SERVICES
        );
    }
}