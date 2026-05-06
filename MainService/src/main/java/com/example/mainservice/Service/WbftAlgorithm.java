package com.example.mainservice.Service;

import com.example.mainservice.DTO.ServiceMessage;
import com.example.mainservice.DTO.WbftResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Base64;

/**
 * Secure Weighted Byzantine Fault Tolerance (WBFT).
 *
 * Warstwa bezpieczeństwa (nowa względem oryginału):
 * ──────────────────────────────────────────────────
 * 1. ECDSA (SHA256withECDSA / secp256r1)
 *    Każdy głos musi być podpisany kluczem prywatnym satelity.
 *    Weryfikacja odbywa się kluczem publicznym z TrustStore.
 *    Nieważny podpis = kryptograficzny dowód złośliwości → czarna lista.
 *
 * 2. TrustStore
 *    Rejestr kluczy publicznych satelit. Tylko węzły zarejestrowane
 *    mogą uczestniczyć w głosowaniu. Klucze ładowane przy starcie
 *    przez NodeKeyRegistry (@PostConstruct) z pliku konfiguracyjnego.
 *
 * 3. Ochrona przed Replay Attack
 *    - timestamp: głos odrzucany jeśli starszy niż MAX_VOTE_AGE_MS (30 s).
 *    - para (roundId, serviceName): zapamiętywana jako "zużyta".
 *      Ponowne użycie → odrzucenie.
 *
 * 4. Wykrywanie Equivocation
 *    Jeśli satelita wyśle dwa różne głosy w tej samej rundzie
 *    → PROVEN_BYZANTINE → czarna lista.
 *
 * 5. Statystyczna detekcja anomalii (z oryginału)
 *    Pozostaje jako drugorzędny mechanizm dla węzłów "podejrzanych"
 *    (brak dowodu kryptograficznego, tylko odchylenie statystyczne).
 *
 * Interfejs publiczny:
 * ────────────────────
 * compute(roundId, votes) – przyjmuje roundId generowane przez
 * VoteAggregatorService (UUID per użytkownik per runda).
 *
 * Kompatybilność wsteczna:
 * ────────────────────────
 * compute(votes) – stara sygnatura, generuje roundId wewnętrznie.
 * Nie zapewnia pełnej ochrony przed Replay Attack między rundami.
 */
@Component
public class WbftAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger(WbftAlgorithm.class);

    private static final double QUORUM_THRESHOLD  = 2.0 / 3.0;
    private static final double MIN_RELATIVE_DIFF = 0.10;
    private static final long   MAX_VOTE_AGE_MS   = 30_000L;
    private static final String SIG_ALGORITHM     = "SHA256withECDSA";

    // ── TrustStore ────────────────────────────────────────────────────────────

    /** serviceName → PublicKey. Ładowany przez NodeKeyRegistry przy starcie. */
    private final Map<String, PublicKey> trustStore = new ConcurrentHashMap<>();

    /** Satelity z udowodnioną złośliwością – trwale wykluczone. */
    private final Set<String> blacklist = ConcurrentHashMap.newKeySet();

    /** roundId → Set<serviceName>: zużyte pary (anty-replay). */
    private final Map<String, Set<String>> usedVotes = new ConcurrentHashMap<>();

    /** roundId → Map<serviceName, category>: pierwszy głos per satelita per runda. */
    private final Map<String, Map<String, String>> firstVotes = new ConcurrentHashMap<>();

    // ── Rejestracja kluczy publicznych ───────────────────────────────────────

    /**
     * Rejestruje klucz publiczny satelity w TrustStore.
     * Wywoływane przez NodeKeyRegistry przy starcie aplikacji.
     *
     * @param serviceName  np. "Service1"
     * @param publicKeyB64 klucz publiczny ECDSA zakodowany w Base64 (X.509 DER)
     */
    public void registerNode(String serviceName, String publicKeyB64)
            throws GeneralSecurityException {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyB64);
        KeyFactory kf = KeyFactory.getInstance("EC");
        PublicKey pk = kf.generatePublic(new X509EncodedKeySpec(keyBytes));
        trustStore.put(serviceName, pk);
        logger.info("TrustStore: zarejestrowano satelitę '{}'", serviceName);
    }

    /** Bezpośrednia rejestracja obiektu PublicKey (używana w testach). */
    public void registerNode(String serviceName, PublicKey publicKey) {
        trustStore.put(serviceName, publicKey);
        logger.info("TrustStore: zarejestrowano satelitę '{}' (obiekt)", serviceName);
    }

    // ── Publiczny interfejs ───────────────────────────────────────────────────

    /**
     * Główna metoda – wywoływana przez VoteAggregatorService.
     *
     * @param roundId unikalny identyfikator rundy (np. "userId-42-" + UUID)
     * @param votes   mapa: serviceName → ServiceMessage
     */
    public WbftResult compute(String roundId, Map<String, ServiceMessage> votes) {

        if (votes == null || votes.isEmpty()) {
            logger.warn("WBFT [{}]: brak głosów", roundId);
            return noData();
        }

        // ── 1. Walidacja kryptograficzna każdego głosu ───────────────────────
        Map<String, String> verifiedCategories = new HashMap<>();
        Map<String, Double> verifiedWeights    = new HashMap<>();
        List<String>        cryptoByzantine    = new ArrayList<>();

        for (Map.Entry<String, ServiceMessage> entry : votes.entrySet()) {
            String serviceName = entry.getKey();
            ServiceMessage msg = entry.getValue();

            ValidationResult vr = validateVote(roundId, serviceName, msg);

            switch (vr) {
                case BLACKLISTED -> logger.warn(
                        "WBFT [{}]: {} na czarnej liście – odrzucono", roundId, serviceName);

                case INVALID_SIGNATURE, REPLAY_DETECTED, EQUIVOCATION, STALE_VOTE -> {
                    logger.error("WBFT [{}]: {} → {} → czarna lista",
                            roundId, serviceName, vr);
                    blacklist.add(serviceName);
                    cryptoByzantine.add(serviceName);
                }
                case UNKNOWN_NODE -> logger.warn(
                        "WBFT [{}]: {} nieznany (brak klucza publicznego) – odrzucono",
                        roundId, serviceName);

                case VALID -> {
                    String cat = extractCategory(msg);
                    if (cat != null) {
                        verifiedCategories.put(serviceName, cat);
                        verifiedWeights.put(serviceName, msg.getWeight());
                    }
                }
            }
        }

        if (verifiedCategories.isEmpty()) {
            logger.warn("WBFT [{}]: brak zweryfikowanych głosów", roundId);
            return noData();
        }

        // ── 2. Sumowanie wag per kategoria ───────────────────────────────────
        Map<String, Double> weightSums = new HashMap<>();
        verifiedCategories.forEach((svc, cat) ->
                weightSums.merge(cat, verifiedWeights.getOrDefault(svc, 1.0), Double::sum));

        double totalWeight = weightSums.values().stream().mapToDouble(Double::doubleValue).sum();
        logWeightSums(roundId, weightSums, totalWeight);

        // ── 3. Wszyscy zgodni ────────────────────────────────────────────────
        if (weightSums.size() == 1) {
            String winner = weightSums.keySet().iterator().next();
            logger.info("WBFT [{}]: UNANIMOUS → {}", roundId, winner);
            return new WbftResult(winner, WbftResult.Status.UNANIMOUS,
                    weightSums, totalWeight, totalWeight, 1.0,
                    cryptoByzantine, verifiedCategories.size());
        }

        // ── 4. Ranking ───────────────────────────────────────────────────────
        List<Map.Entry<String, Double>> ranked = weightSums.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();

        String winner       = ranked.get(0).getKey();
        double winnerWeight = ranked.get(0).getValue();
        double secondWeight = ranked.get(1).getValue();
        double winnerRatio  = winnerWeight / totalWeight;
        double relativeDiff = (winnerWeight - secondWeight) / totalWeight;

        // ── 5. Statystyczna detekcja podejrzanych ────────────────────────────
        List<String> suspects = detectStatisticalSuspects(
                verifiedCategories, winner, verifiedWeights, totalWeight);

        // Połącz: kryptograficznie udowodnieni + statystyczni
        List<String> allSuspects = new ArrayList<>(cryptoByzantine);
        suspects.stream().filter(s -> !allSuspects.contains(s)).forEach(allSuspects::add);

        // ── 6. Kworum ────────────────────────────────────────────────────────
        boolean hasQuorum = winnerRatio > QUORUM_THRESHOLD
                && relativeDiff >= MIN_RELATIVE_DIFF;

        if (hasQuorum) {
            logger.info("WBFT [{}]: CONSENSUS → {} ({:.1f}%)",
                    roundId, winner, winnerRatio * 100);
            return new WbftResult(winner, WbftResult.Status.CONSENSUS,
                    weightSums, totalWeight, winnerWeight, winnerRatio,
                    allSuspects, verifiedCategories.size());
        }

        logger.warn("WBFT [{}]: NO_QUORUM → {} ({:.1f}% < {:.1f}%)",
                roundId, winner, winnerRatio * 100, QUORUM_THRESHOLD * 100);
        return new WbftResult("OTHER", WbftResult.Status.NO_QUORUM,
                weightSums, totalWeight, winnerWeight, winnerRatio,
                allSuspects, verifiedCategories.size());
    }

    /**
     * Kompatybilność wsteczna z VoteAggregatorService (stara sygnatura).
     * Generuje roundId wewnętrznie – nie zapewnia pełnej ochrony między rundami.
     */
    public WbftResult compute(Map<String, ServiceMessage> votes) {
        return compute("round-" + UUID.randomUUID(), votes);
    }

    // ── Walidacja kryptograficzna ─────────────────────────────────────────────

    private enum ValidationResult {
        VALID,
        UNKNOWN_NODE,       // brak klucza w TrustStore
        BLACKLISTED,        // węzeł na czarnej liście
        INVALID_SIGNATURE,  // podpis ECDSA nieważny → dowód złośliwości
        REPLAY_DETECTED,    // para (roundId, serviceName) już zużyta
        EQUIVOCATION,       // dwa różne głosy w tej samej rundzie
        STALE_VOTE          // głos zbyt stary
    }

    private ValidationResult validateVote(String roundId, String serviceName, ServiceMessage msg) {

        // 1. Czarna lista
        if (blacklist.contains(serviceName))
            return ValidationResult.BLACKLISTED;

        // 2. TrustStore
        PublicKey publicKey = trustStore.get(serviceName);
        if (publicKey == null)
            return ValidationResult.UNKNOWN_NODE;

        // 3. Świeżość timestampu
        long age = Math.abs(Instant.now().toEpochMilli() - msg.getTimestamp());
        if (age > MAX_VOTE_AGE_MS) {
            logger.warn("WBFT: stale vote od {} (wiek={}ms)", serviceName, age);
            return ValidationResult.STALE_VOTE;
        }

        // 4. Replay: para (roundId, serviceName) już użyta?
        Set<String> used = usedVotes.computeIfAbsent(roundId,
                k -> ConcurrentHashMap.newKeySet());
        if (!used.add(serviceName))
            return ValidationResult.REPLAY_DETECTED;

        // 5. Weryfikacja podpisu ECDSA
        String payload = buildPayload(serviceName, msg);
        if (!verifySignature(payload, msg.getSignature(), publicKey))
            return ValidationResult.INVALID_SIGNATURE;

        // 6. Equivocation: inny głos w tej samej rundzie?
        String category = extractCategory(msg);
        Map<String, String> roundFirst = firstVotes.computeIfAbsent(roundId,
                k -> new ConcurrentHashMap<>());
        String prev = roundFirst.putIfAbsent(serviceName, category != null ? category : "");
        if (prev != null && !prev.equals(category)) {
            logger.error("WBFT: EQUIVOCATION {} w rundzie {}: '{}' vs '{}'",
                    serviceName, roundId, prev, category);
            return ValidationResult.EQUIVOCATION;
        }

        return ValidationResult.VALID;
    }

    /**
     * Buduje payload do podpisu.
     * Musi być identyczny po stronie satelity i MainService.
     * Format: serviceName|category|weight|timestamp
     */
    private String buildPayload(String serviceName, ServiceMessage msg) {
        String category = extractCategory(msg);
        return serviceName + "|" + category + "|" + msg.getWeight() + "|" + msg.getTimestamp();
    }

    private boolean verifySignature(String payload, String signature, PublicKey publicKey) {
        if (signature == null || signature.isBlank()) {
            logger.warn("WBFT: brak podpisu w głosie");
            return false;
        }
        try {
            Signature sig = Signature.getInstance(SIG_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(payload.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            logger.error("WBFT: błąd weryfikacji podpisu: {}", e.getMessage());
            return false;
        }
    }

    // ── Statystyczna detekcja ─────────────────────────────────────────────────

    private List<String> detectStatisticalSuspects(
            Map<String, String> verifiedCategories,
            String winnerCategory,
            Map<String, Double> weights,
            double totalWeight
    ) {
        double threshold = MIN_RELATIVE_DIFF * totalWeight;
        return verifiedCategories.entrySet().stream()
                .filter(e -> !winnerCategory.equals(e.getValue()))
                .filter(e -> weights.getOrDefault(e.getKey(), 1.0) >= threshold)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toList());
    }

    // ── Czyszczenie stanu rundy ───────────────────────────────────────────────

    /**
     * Wywołaj po compute() aby zapobiec wyciekowi pamięci.
     * VoteAggregatorService powinien wywoływać tę metodę w runWbft().
     */
    public void clearRoundState(String roundId) {
        usedVotes.remove(roundId);
        firstVotes.remove(roundId);
        logger.debug("WBFT: wyczyszczono stan rundy {}", roundId);
    }

    /** Niemodyfikowalny widok czarnej listy (do monitoringu). */
    public Set<String> getBlacklist() {
        return Collections.unmodifiableSet(blacklist);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractCategory(ServiceMessage msg) {
        if (msg.getContent() instanceof Map<?, ?> map) {
            Object cat = map.get("category");
            return cat instanceof String s && !s.isBlank() ? s : null;
        }
        return null;
    }

    private void logWeightSums(String roundId, Map<String, Double> ws, double total) {
        logger.info("WBFT [{}]: rozkład głosów (łącznie={:.2f}):", roundId, total);
        ws.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> logger.info("  {} → {:.2f} ({:.1f}%)",
                        e.getKey(), e.getValue(), e.getValue() / total * 100));
    }

    private WbftResult noData() {
        return new WbftResult("OTHER", WbftResult.Status.NO_DATA,
                Map.of(), 0.0, 0.0, 0.0, List.of(), 0);
    }
}