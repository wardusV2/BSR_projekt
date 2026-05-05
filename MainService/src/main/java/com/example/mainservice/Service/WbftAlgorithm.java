package com.example.mainservice.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.mainservice.DTO.UserCategoryPayload;
import com.example.mainservice.DTO.WbftResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementacja algorytmu Weighted Byzantine Fault Tolerance (WBFT).
 *
 * Zasada działania:
 * ─────────────────
 * 1. Każdy węzeł (satelita) wysyła głos: (kategoria, waga).
 * 2. Sumujemy wagi per kategoria.
 * 3. Zwycięzca musi uzyskać > QUORUM_THRESHOLD sumy wszystkich wag.
 *    Domyślnie 2/3 (66.6%) – klasyczny próg BFT tolerujący f < n/3 błędów.
 * 4. Węzły, których głos różni się od kategorii uzyskującej kworum,
 *    są oznaczane jako "byzantine suspects".
 * 5. Jeśli żadna kategoria nie osiągnie progu → wynik NO_QUORUM ("OTHER").
 *
 * Tolerancja błędów:
 * ──────────────────
 * Przy 7 węzłach i równych wagach system toleruje do 2 węzłów byzantyjskich
 * (f < n/3 = 2.33 → f_max = 2).
 * Przy różnych wagach kryterium opiera się na sumie wag, nie liczbie węzłów.
 */

@Component
public class WbftAlgorithm {
    private static final Logger logger = LoggerFactory.getLogger(WbftAlgorithm.class);

    /**
     * Próg kworum: zwycięzca musi mieć > 2/3 sumy wszystkich wag.
     * Klasyczny BFT wymaga 2f+1 z 3f+1 węzłów → ~66.7%.
     */
    private static final double QUORUM_THRESHOLD = 2.0 / 3.0;

    /**
     * Minimalna różnica względna między 1. a 2. kategorią
     * wymagana do uznania wyniku za pewny (dodatkowe zabezpieczenie).
     */
    private static final double MIN_RELATIVE_DIFF = 0.10;

    /* ================================================================
       GŁÓWNA METODA
       ================================================================ */

    /**
     * Uruchamia algorytm WBFT dla zebranych głosów jednego użytkownika.
     *
     * @param votes mapa: serviceName → ServiceMessage (głos satelity)
     * @return wynik rundy WBFT
     */
    public WbftResult compute(Map<String, ServiceMessage> votes) {

        if (votes == null || votes.isEmpty()) {
            logger.warn("WBFT: brak głosów");
            return noData();
        }

        // ── 1. Ekstrakcja głosów ────────────────────────────────────
        Map<String, String> serviceToCategory = extractCategories(votes);
        Map<String, Double> serviceToWeight   = extractWeights(votes);

        if (serviceToCategory.isEmpty()) {
            logger.warn("WBFT: nie udało się wyekstrahować żadnej kategorii");
            return noData();
        }

        // ── 2. Sumowanie wag per kategoria ──────────────────────────
        Map<String, Double> weightSums = new HashMap<>();
        serviceToCategory.forEach((service, category) ->
                weightSums.merge(category, serviceToWeight.getOrDefault(service, 1.0), Double::sum)
        );

        double totalWeight = weightSums.values().stream()
                .mapToDouble(Double::doubleValue).sum();

        logWeightSums(weightSums, totalWeight);

        // ── 3. Przypadek: wszyscy zgodni ────────────────────────────
        if (weightSums.size() == 1) {
            String winner = weightSums.keySet().iterator().next();
            logger.info("WBFT: UNANIMOUS → kategoria={}", winner);
            return new WbftResult(
                    winner,
                    WbftResult.Status.UNANIMOUS,
                    weightSums,
                    totalWeight,
                    totalWeight,
                    1.0,
                    List.of(),
                    serviceToCategory.size()
            );
        }

        // ── 4. Sortowanie kandydatów malejąco po wadze ──────────────
        List<Map.Entry<String, Double>> ranked = weightSums.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();

        String winner       = ranked.get(0).getKey();
        double winnerWeight = ranked.get(0).getValue();
        double secondWeight = ranked.get(1).getValue();

        double winnerRatio   = winnerWeight / totalWeight;
        double relativeDiff  = (winnerWeight - secondWeight) / totalWeight;

        logger.info("WBFT: kandydat={} waga={:.2f} ratio={:.2f} diff={:.2f}",
                winner, winnerWeight, winnerRatio, relativeDiff);

        // ── 5. Wykrywanie węzłów byzantyjskich ──────────────────────
        List<String> byzantineSuspects = detectByzantineNodes(
                serviceToCategory, winner, serviceToWeight, totalWeight
        );

        if (!byzantineSuspects.isEmpty()) {
            logger.warn("WBFT: podejrzane węzły byzantyjskie → {}", byzantineSuspects);
        }

        // ── 6. Sprawdzenie kworum ────────────────────────────────────
        boolean hasQuorum = winnerRatio > QUORUM_THRESHOLD
                && relativeDiff >= MIN_RELATIVE_DIFF;

        if (hasQuorum) {
            logger.info("WBFT: CONSENSUS → kategoria={} ratio={:.1f}%",
                    winner, winnerRatio * 100);
            return new WbftResult(
                    winner,
                    WbftResult.Status.CONSENSUS,
                    weightSums,
                    totalWeight,
                    winnerWeight,
                    winnerRatio,
                    byzantineSuspects,
                    serviceToCategory.size()
            );
        }

        // ── 7. Brak kworum ───────────────────────────────────────────
        logger.warn("WBFT: NO_QUORUM → najlepszy kandydat={} ratio={:.1f}% (próg={:.1f}%)",
                winner, winnerRatio * 100, QUORUM_THRESHOLD * 100);
        return new WbftResult(
                "OTHER",
                WbftResult.Status.NO_QUORUM,
                weightSums,
                totalWeight,
                winnerWeight,
                winnerRatio,
                byzantineSuspects,
                serviceToCategory.size()
        );
    }

    /* ================================================================
       WYKRYWANIE BŁĘDÓW BYZANTYJSKICH
       ================================================================
       Węzeł jest "podejrzany" gdy:
       - głosuje na kategorię inną niż zwycięzca kworum
       - ORAZ jego waga jest na tyle duża, że mógł celowo zablokować konsensus
         (waga węzła > MIN_RELATIVE_DIFF * totalWeight)
       ================================================================ */

    private List<String> detectByzantineNodes(
            Map<String, String> serviceToCategory,
            String winnerCategory,
            Map<String, Double> serviceToWeight,
            double totalWeight
    ) {
        double byzantineWeightThreshold = MIN_RELATIVE_DIFF * totalWeight;

        return serviceToCategory.entrySet().stream()
                .filter(e -> !winnerCategory.equals(e.getValue()))
                .filter(e -> {
                    double w = serviceToWeight.getOrDefault(e.getKey(), 1.0);
                    // węzeł z dużą wagą głosujący przeciwko – podejrzany
                    return w >= byzantineWeightThreshold;
                })
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    /* ================================================================
       EKSTRAKCJA
       ================================================================ */

    private Map<String, String> extractCategories(Map<String, ServiceMessage> votes) {
        Map<String, String> result = new HashMap<>();
        votes.forEach((service, msg) -> {
            if (msg.getContent() instanceof Map<?, ?> map) {
                Object cat = map.get("category");
                if (cat instanceof String category && !category.isBlank()) {
                    result.put(service, category);
                }
            }
        });
        return result;
    }

    private Map<String, Double> extractWeights(Map<String, ServiceMessage> votes) {
        Map<String, Double> result = new HashMap<>();
        votes.forEach((service, msg) -> result.put(service, msg.getWeight()));
        return result;
    }

    /* ================================================================
       LOGGING
       ================================================================ */

    private void logWeightSums(Map<String, Double> weightSums, double total) {
        logger.info("WBFT: rozkład głosów (łącznie waga={:.2f}):", total);
        weightSums.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> logger.info("  {} → {:.2f} ({:.1f}%)",
                        e.getKey(), e.getValue(), e.getValue() / total * 100));
    }

    /* ================================================================
       FACTORY METHODS
       ================================================================ */

    private WbftResult noData() {
        return new WbftResult(
                "OTHER",
                WbftResult.Status.NO_DATA,
                Map.of(),
                0.0,
                0.0,
                0.0,
                List.of(),
                0
        );
    }
}
