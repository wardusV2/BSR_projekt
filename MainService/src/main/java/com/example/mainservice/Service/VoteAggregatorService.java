package com.example.mainservice.Service;

import com.example.mainservice.DTO.RoundState;
import com.example.mainservice.DTO.ServiceMessage;
import com.example.mainservice.DTO.WbftResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agreguje głosy satelit i uruchamia algorytm WBFT.
 *
 * Trzy ścieżki zakończenia rundy:
 * ────────────────────────────────
 * A) Pełna liczba głosów (7/7)  → natychmiastowe WBFT w wątku VoteListener.
 * B) Kworum miękkie (≥5 głosów) → natychmiastowe WBFT (bez czekania na resztę).
 * C) Timeout (>30 s)            → WBFT uruchamiany przez @Scheduled co 5 s.
 *
 * Flaga RoundState.finished (volatile) gwarantuje, że WBFT wykona się
 * dokładnie raz nawet przy współbieżnych wywołaniach.
 */
@Service
public class VoteAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(VoteAggregatorService.class);

    /** Wszystkie znane serwisy satelitarne. */
    private static final List<String> ALL_SERVICES = List.of(
            "Service1","Service2","Service3","Service4","Service5","Service6","Service7"
    );

    /* ── zależności ── */

    private final WbftAlgorithm      wbftAlgorithm;
    private final WbftResultService  wbftResultService;
    private final MonitoringLogService logService;

    /* ── stan aktywnych rund ── */

    /**
     * Mapa: userId → RoundState.
     * ConcurrentHashMap chroni przed race-condition przy równoległych głosach.
     */
    private final Map<Integer, RoundState> activeRounds = new ConcurrentHashMap<>();

    public VoteAggregatorService(WbftAlgorithm wbftAlgorithm,
                                 WbftResultService wbftResultService,
                                 MonitoringLogService logService) {
        this.wbftAlgorithm     = wbftAlgorithm;
        this.wbftResultService = wbftResultService;
        this.logService        = logService;
    }

    /* ══════════════════════════════════════════════════════════════
       PRZYJMOWANIE GŁOSU
       ══════════════════════════════════════════════════════════════ */

    /**
     * Główny punkt wejścia — wywoływany przez VoteListener przy każdej wiadomości z RabbitMQ.
     */
    public void processVote(ServiceMessage message) {
        Object rawUserId = extractUserId(message);
        if (rawUserId == null) {
            log.warn("Głos bez userId od {}", message.getServiceName());
            return;
        }
        int userId = ((Number) rawUserId).intValue();

        // Pobierz istniejącą rundę lub utwórz nową
        RoundState round = activeRounds.computeIfAbsent(userId, RoundState::new);

        if (round.isFinished()) {
            // Runda już zamknięta — nowy głos zaczyna kolejną rundę
            round = new RoundState(userId);
            activeRounds.put(userId, round);
            log.info("Nowa runda dla userId={} (poprzednia zamknięta)", userId);
        }

        round.addVote(message.getServiceName(), message);

        log.info("Głos od {} dla userId={} ({}/{} głosów)",
                message.getServiceName(), userId,
                round.getVoteCount(), RoundState.TOTAL_NODES);

        // Sprawdź czy uruchomić WBFT natychmiast
        if (round.shouldFinish()) {
            runWbft(userId, round);
        }
    }

    /* ══════════════════════════════════════════════════════════════
       SCHEDULER — TIMEOUT
       ══════════════════════════════════════════════════════════════ */

    /**
     * Co 5 sekund sprawdza, czy jakaś aktywna runda przekroczyła timeout.
     * Jeśli tak — uruchamia WBFT na zebranych dotąd głosach.
     *
     * Wymaga @EnableScheduling w klasie konfiguracyjnej lub main.
     */
    @Scheduled(fixedDelay = 5_000)
    public void checkTimeouts() {
        if (activeRounds.isEmpty()) return;

        activeRounds.forEach((userId, round) -> {
            if (round.isFinished() || !round.isTimedOut()) return;

            int count = round.getVoteCount();
            log.warn("TIMEOUT rundy dla userId={} po {} ms — zebrano {}/{} głosów",
                    userId, round.ageMs(), count, RoundState.TOTAL_NODES);

            if (round.hasEnoughVotesForWbft()) {
                // Mamy ≥5 głosów — uruchom WBFT na tym co jest
                log.info("Wystarczająca liczba głosów ({}) — uruchamiam WBFT po timeout", count);
                runWbft(userId, round);
            } else {
                // Za mało głosów żeby cokolwiek sensownego policzyć
                log.warn("Za mało głosów ({}/{}) — runda zakończona jako NO_DATA",
                        count, RoundState.TOTAL_NODES);
                round.markFinished();
                activeRounds.remove(userId, round);
                wbftResultService.addResult(buildNoDataResult(userId, round));
            }
        });
    }

    /* ══════════════════════════════════════════════════════════════
       URUCHOMIENIE WBFT
       ══════════════════════════════════════════════════════════════ */

    /**
     * Uruchamia algorytm WBFT dla danej rundy.
     * Metoda jest idempotentna dzięki RoundState.markFinished().
     */
    private void runWbft(int userId, RoundState round) {
        // Atomowe oznaczenie rundy jako zakończonej
        if (!round.markFinished()) {
            log.debug("WBFT dla userId={} już uruchomiony — pomijam", userId);
            return;
        }

        // Usunięcie z mapy aktywnych rund
        activeRounds.remove(userId, round);

        List<String> missing = round.missingSenders(ALL_SERVICES);
        boolean timedOut = round.isTimedOut() && round.getVoteCount() < RoundState.TOTAL_NODES;

        log.info("Uruchamiam WBFT dla userId={} | głosy={}/{} | timeout={} | brakuje={}",
                userId, round.getVoteCount(), RoundState.TOTAL_NODES, timedOut, missing);

        // Oblicz wynik WBFT
        WbftResult result = wbftAlgorithm.compute(round.getVotes());

        // Wzbogać wynik o metadane rundy
        Map<String, Object> resultMap = buildResultMap(userId, result, round, missing, timedOut);

        // Zapisz wynik
        wbftResultService.addResult(resultMap);

        // Log monitoringu
        logService.log(Map.of(
                "type",      "WBFT",
                "service",   "MainService",
                "timestamp", System.currentTimeMillis(),
                "content",   Map.of(
                        "userId",   userId,
                        "status",   result.getStatus().name(),
                        "category", result.getCategory(),
                        "timedOut", timedOut,
                        "missing",  missing
                )
        ));

        log.info("WBFT zakończony dla userId={} → status={} kategoria={}",
                userId, result.getStatus(), result.getCategory());
    }

    /* ══════════════════════════════════════════════════════════════
       BUDOWANIE ODPOWIEDZI
       ══════════════════════════════════════════════════════════════ */

    /**
     * Buduje mapę wynikową przekazywaną do WbftResultService i frontendu.
     * Zawiera pole nodeVotes potrzebne do wizualizacji węzłów.
     */
    private Map<String, Object> buildResultMap(int userId,
                                               WbftResult result,
                                               RoundState round,
                                               List<String> missing,
                                               boolean timedOut) {
        // Wyznacz kategorię zwycięzcy do porównania z głosami
        String winnerCategory = result.getCategory();

        // Zbuduj listę głosów per węzeł (dla frontendu)
        List<Map<String, Object>> nodeVotes = new ArrayList<>();
        round.getVotes().forEach((svc, msg) -> {
            String cat = extractCategory(msg);
            boolean isByzantine = result.getByzantineSuspects().contains(svc);
            nodeVotes.add(Map.of(
                    "service",   svc,
                    "category",  cat != null ? cat : "?",
                    "weight",    msg.getWeight(),
                    "state",     isByzantine ? "byzantine" : "ok",
                    "timedOut",  false
            ));
        });

        // Dodaj węzły, które nie odpowiedziały
        missing.forEach(svc -> nodeVotes.add(Map.of(
                "service",  svc,
                "category", "",
                "weight",   0.0,
                "state",    "missing",
                "timedOut", timedOut
        )));

        // Sortuj: Service1…Service7
        nodeVotes.sort(Comparator.comparing(m -> (String) m.get("service")));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId",            userId);
        map.put("category",          result.getCategory());
        map.put("status",            result.getStatus().name());
        map.put("winnerRatio",       result.getWinnerRatio());
        map.put("totalWeight",       result.getTotalWeight());
        map.put("weightSums",        result.getWeightSums());
        map.put("byzantineSuspects", result.getByzantineSuspects());
        map.put("voterCount",        round.getVoteCount());
        map.put("confident",         result.getStatus() != WbftResult.Status.NO_QUORUM);
        map.put("timedOut",          timedOut);
        map.put("missingSenders",    missing);
        map.put("nodeVotes",         nodeVotes);
        map.put("timestamp",         System.currentTimeMillis());
        return map;
    }

    /* ══════════════════════════════════════════════════════════════
       EKSTRAKCJA DANYCH
       ══════════════════════════════════════════════════════════════ */

    private Object extractUserId(ServiceMessage msg) {
        if (msg.getContent() instanceof Map<?, ?> m) return m.get("userId");
        return null;
    }

    private String extractCategory(ServiceMessage msg) {
        if (msg.getContent() instanceof Map<?, ?> m) {
            Object cat = m.get("category");
            return cat instanceof String s ? s : null;
        }
        return null;
    }

    private Map<String, Object> buildNoDataResult(int userId, RoundState round) {
        List<String> missing = round.missingSenders(ALL_SERVICES);
        List<Map<String, Object>> nodeVotes = new ArrayList<>();
        round.getVotes().forEach((svc, msg) -> nodeVotes.add(Map.of(
                "service",  svc,
                "category", extractCategory(msg) != null ? extractCategory(msg) : "?",
                "weight",   msg.getWeight(),
                "state",    "ok",
                "timedOut", false
        )));
        missing.forEach(svc -> nodeVotes.add(Map.of(
                "service",  svc,
                "category", "",
                "weight",   0.0,
                "state",    "missing",
                "timedOut", true
        )));
        nodeVotes.sort(Comparator.comparing(m -> (String) m.get("service")));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId",            userId);
        map.put("category",          "OTHER");
        map.put("status",            "NO_DATA");
        map.put("winnerRatio",       0.0);
        map.put("totalWeight",       0.0);
        map.put("weightSums",        Map.of());
        map.put("byzantineSuspects", List.of());
        map.put("voterCount",        round.getVoteCount());
        map.put("confident",         false);
        map.put("timedOut",          true);
        map.put("missingSenders",    missing);
        map.put("nodeVotes",         nodeVotes);
        map.put("timestamp",         System.currentTimeMillis());

        logService.log(Map.of(
                "type",      "WBFT",
                "service",   "MainService",
                "timestamp", System.currentTimeMillis(),
                "content",   Map.of("userId", userId, "status", "NO_DATA",
                        "category", "OTHER", "timedOut", true, "missing", missing)
        ));
        return map;
    }

    /* ══════════════════════════════════════════════════════════════
       DIAGNOSTYKA
       ══════════════════════════════════════════════════════════════ */

    /**
     * Stan aktywnych rund — używany przez MonitoringController (/monitor/state).
     */
    public Map<String, Object> getDebugState() {
        Map<String, Object> state = new LinkedHashMap<>();

        Map<String, Object> users = new LinkedHashMap<>();
        activeRounds.forEach((userId, round) -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("voteCount",   round.getVoteCount());
            info.put("totalNodes",  RoundState.TOTAL_NODES);
            info.put("ageMs",       round.ageMs());
            info.put("timedOut",    round.isTimedOut());
            info.put("missing",     round.missingSenders(ALL_SERVICES));
            info.put("senders",     new ArrayList<>(round.getVotes().keySet()));
            users.put(String.valueOf(userId), info);
        });

        state.put("activeUsers",   users);
        state.put("totalRounds",   users.size());
        state.put("timeoutMs",     RoundState.ROUND_TIMEOUT_MS);
        state.put("minVotes",      RoundState.MIN_VOTES_FOR_EARLY_WBFT);
        state.put("serverTimeMs",  System.currentTimeMillis());
        return state;
    }
}