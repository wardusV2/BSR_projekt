package com.example.mainservice.Controller;

import com.example.mainservice.DTO.MainResponse;
import com.example.mainservice.DTO.ServiceMessage;
import com.example.mainservice.DTO.UserCategoryPayload;
import com.example.mainservice.Service.RecommendationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
public class MainServiceController {

    private static final Logger logger =
            LoggerFactory.getLogger(MainServiceController.class);

    private static final double EPSILON = 0.15;

    private static final Set<String> EXPECTED_SERVICES = Set.of(
            "Service1",
            "Service2",
            "Service3",
            "Service4",
            "Service5",
            "Service6",
            "Service7"
    );

    private final RecommendationClient recommendationClient;

    public MainServiceController(
            RecommendationClient recommendationClient
    ) {
        this.recommendationClient = recommendationClient;
    }

    /* ============================================================
       ====================== STORAGE =============================
       ============================================================ */

    // userId -> (serviceName -> message)
    private final ConcurrentHashMap<Integer,
            ConcurrentHashMap<String, ServiceMessage>>
            lastMessagesPerUser = new ConcurrentHashMap<>();

    // userId -> set(serviceName)
    private final ConcurrentHashMap<Integer, Set<String>>
            receivedServicesPerUser = new ConcurrentHashMap<>();

    /* ============================================================
       ================== WEBSOCKET ENDPOINT ======================
       ============================================================ */

    @MessageMapping("/from-service")
    @SendTo("/topic/main-broadcast")
    public MainResponse receiveMessage(ServiceMessage message) {

        extractPayload(message).ifPresent(payload -> {

            Integer userId = payload.userId();
            String serviceName = message.getServiceName();

            lastMessagesPerUser
                    .computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
                    .put(serviceName, message);

            receivedServicesPerUser
                    .computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet())
                    .add(serviceName);

            logger.info(
                    "User {} → received from {} ({}/{})",
                    userId,
                    serviceName,
                    receivedServicesPerUser.get(userId).size(),
                    EXPECTED_SERVICES.size()
            );

            if (receivedServicesPerUser.get(userId)
                    .containsAll(EXPECTED_SERVICES)) {

                handleSynchronizedUser(userId);
            }
        });

        return new MainResponse("ACK from MainService");
    }

    /* ============================================================
       ================= SYNCHRONIZED USER ========================
       ============================================================ */

    private void handleSynchronizedUser(Integer userId) {

        String finalCategory =
                computeApproximateVoteForUser(userId)
                        .orElse("OTHER");

        if ("OTHER".equals(finalCategory)) {
            logger.warn(
                    "NO CONFIDENT VERDICT → user={}, saving OTHER",
                    userId
            );
        } else {
            logger.info(
                    "FINAL VERDICT → user={}, category={}",
                    userId,
                    finalCategory
            );
        }

        recommendationClient
                .saveRecommendation(userId, finalCategory);

        // reset po zakończeniu rundy
        lastMessagesPerUser.remove(userId);
        receivedServicesPerUser.remove(userId);
    }


    /* ============================================================
       ================= PAYLOAD EXTRACTION =======================
       ============================================================ */

    @SuppressWarnings("unchecked")
    private Optional<UserCategoryPayload> extractPayload(
            ServiceMessage msg
    ) {
        if (!(msg.getContent() instanceof Map<?, ?> map)) {
            return Optional.empty();
        }

        try {
            Integer userId = (Integer) map.get("userId");
            String category = (String) map.get("category");

            if (userId == null || category == null) {
                return Optional.empty();
            }

            return Optional.of(
                    new UserCategoryPayload(userId, category)
            );

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /* ============================================================
       ================= APPROXIMATE VOTING =======================
       ============================================================ */

    public Optional<String> computeApproximateVoteForUser(
            Integer userId
    ) {

        Map<String, ServiceMessage> userMessages =
                lastMessagesPerUser.get(userId);

        if (userMessages == null || userMessages.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Double> weightSums = new HashMap<>();

        userMessages.values().forEach(msg ->
                extractPayload(msg).ifPresent(payload ->
                        weightSums.merge(
                                payload.category(),
                                msg.getWeight(),
                                Double::sum
                        )
                )
        );

        if (weightSums.isEmpty()) {
            return Optional.empty();
        }

        if (weightSums.size() == 1) {
            return Optional.of(
                    weightSums.keySet().iterator().next()
            );
        }

        double totalWeight = weightSums.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        List<Map.Entry<String, Double>> sorted =
                weightSums.entrySet()
                        .stream()
                        .sorted(
                                Map.Entry.<String, Double>
                                                comparingByValue()
                                        .reversed()
                        )
                        .toList();

        double top = sorted.get(0).getValue();
        double second = sorted.get(1).getValue();

        double relativeDiff = (top - second) / totalWeight;

        if (relativeDiff < EPSILON) {
            return Optional.empty();
        }

        return Optional.of(sorted.get(0).getKey());
    }

    /* ============================================================
       ================= DEBUG REST ENDPOINT ======================
       ============================================================ */

    @GetMapping("/vote/result")
    public Map<String, Object> getVoteResult() {

        Map<String, Object> result = new HashMap<>();

        result.put("users", lastMessagesPerUser.keySet());

        result.put(
                "receivedServices",
                receivedServicesPerUser
        );

        return result;
    }
}
