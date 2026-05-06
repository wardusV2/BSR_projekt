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
 * Zmiany względem oryginału (warstwa security):
 * ──────────────────────────────────────────────
 * 1. Każda runda ma unikalny roundId (format: "u{userId}-{UUID}").
 *    roundId jest przekazywany do WbftAlgorithm.compute() i służy do:
 *    - śledzenia zużytych głosów (anty-replay)
 *    - wykrywania equivocation (dwa głosy tej samej satelity w jednej rundzie)
 *
 * 2. Po zakończeniu rundy wywołuje wbftAlgorithm.clearRoundState(roundId)
 *    aby zwolnić pamięć zajmowaną przez dane anty-replay.
 *
 * Pozostała logika (ścieżki A/B/C, timeout, @Scheduled) bez zmian.
 */
@Service
public class VoteAggregatorService {

    private static final Logger log = LoggerFactory.getLogger(VoteAggregatorService.class);

    private static final List<String> ALL_SERVICES = List.of(
            "Service1","Service2","Service3","Service4","Service5","Service6","Service7"
    );

    private final WbftAlgorithm      wbftAlgorithm;
    private final WbftResultService  wbftResultService;
    private final MonitoringLogService logService;

    /** userId → RoundState (zawiera roundId). */
    private final Map<Integer, RoundState> activeRounds = new ConcurrentHashMap<>();

    public VoteAggregatorService(WbftAlgorithm wbftAlgorithm,
                                 WbftResultService wbftResultService,
                                 MonitoringLogService logService) {
        this.wbftAlgorithm    = wbftAlgorithm;
        this.wbftResultService = wbftResultService;
        this.logService        = logService;
    }

    // ── Przyjmowanie głosu ────────────────────────────────────────────────────

    public void processVote(ServiceMessage message) {
        Object rawUserId = extractUserId(message);
        if (rawUserId == null) {
            log.warn("Głos bez userId od {}", message.getServiceName());
            return;
        }
        int userId = ((Number) rawUserId).intValue();

        RoundState round = activeRounds.computeIfAbsent(userId, RoundState::new);

        if (round.isFinished()) {
            round = new RoundState(userId);
            activeRounds.put(userId, round);
            log.info("Nowa runda {} dla userId={}", round.getRoundId(), userId);
        }

        round.addVote(message.getServiceName(), message);

        log.info("Głos od {} dla userId={} ({}/{}) runda={}",
                message.getServiceName(), userId,
                round.getVoteCount(), RoundState.TOTAL_NODES, round.getRoundId());

        if (round.shouldFinish()) {
            runWbft(userId, round);
        }
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 5_000)
    public void checkTimeouts() {
        if (activeRounds.isEmpty()) return;

        activeRounds.forEach((userId, round) -> {
            if (round.isFinished() || !round.isTimedOut()) return;

            int count = round.getVoteCount();
            log.warn("TIMEOUT rundy {} dla userId={} po {}ms — {}/{} głosów",
                    round.getRoundId(), userId, round.ageMs(), count, RoundState.TOTAL_NODES);

            if (round.hasEnoughVotesForWbft()) {
                runWbft(userId, round);
            } else {
                log.warn("Za mało głosów ({}/{}) — NO_DATA", count, RoundState.TOTAL_NODES);
                round.markFinished();
                activeRounds.remove(userId, round);
                // Wyczyść stan anty-replay dla tej rundy
                wbftAlgorithm.clearRoundState(round.getRoundId());
                wbftResultService.addResult(buildNoDataResult(userId, round));
            }
        });
    }

    // ── Uruchomienie WBFT ─────────────────────────────────────────────────────

    private void runWbft(int userId, RoundState round) {
        if (!round.markFinished()) {
            log.debug("WBFT dla userId={} już uruchomiony – pomijam", userId);
            return;
        }

        activeRounds.remove(userId, round);

        List<String> missing  = round.missingSenders(ALL_SERVICES);
        boolean timedOut = round.isTimedOut() && round.getVoteCount() < RoundState.TOTAL_NODES;
        String roundId   = round.getRoundId();

        log.info("Uruchamiam WBFT | runda={} userId={} głosy={}/{} timeout={} brakuje={}",
                roundId, userId, round.getVoteCount(), RoundState.TOTAL_NODES, timedOut, missing);

        // Przekaż roundId do WBFT – używany do weryfikacji anty-replay i equivocation
        WbftResult result = wbftAlgorithm.compute(roundId, round.getVotes());

        // Zwolnij pamięć anty-replay po zakończeniu rundy
        wbftAlgorithm.clearRoundState(roundId);

        Map<String, Object> resultMap = buildResultMap(userId, result, round, missing, timedOut);
        wbftResultService.addResult(resultMap);

        logService.log(Map.of(
                "type",      "WBFT",
                "service",   "MainService",
                "timestamp", System.currentTimeMillis(),
                "content",   Map.of(
                        "userId",    userId,
                        "roundId",   roundId,
                        "status",    result.getStatus().name(),
                        "category",  result.getCategory(),
                        "timedOut",  timedOut,
                        "missing",   missing,
                        "blacklist", wbftAlgorithm.getBlacklist()
                )
        ));

        log.info("WBFT zakończony | runda={} userId={} status={} kategoria={}",
                roundId, userId, result.getStatus(), result.getCategory());
    }

    // ── Budowanie odpowiedzi ──────────────────────────────────────────────────

    private Map<String, Object> buildResultMap(int userId, WbftResult result,
                                               RoundState round, List<String> missing,
                                               boolean timedOut) {
        List<Map<String, Object>> nodeVotes = new ArrayList<>();
        round.getVotes().forEach((svc, msg) -> {
            String cat = extractCategory(msg);
            boolean isByzantine = result.getByzantineSuspects().contains(svc);
            nodeVotes.add(Map.of(
                    "service",  svc,
                    "category", cat != null ? cat : "?",
                    "weight",   msg.getWeight(),
                    "state",    isByzantine ? "byzantine" : "ok",
                    "timedOut", false
            ));
        });

        missing.forEach(svc -> nodeVotes.add(Map.of(
                "service",  svc,
                "category", "",
                "weight",   0.0,
                "state",    "missing",
                "timedOut", timedOut
        )));

        nodeVotes.sort(Comparator.comparing(m -> (String) m.get("service")));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId",            userId);
        map.put("roundId",           round.getRoundId());
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

    // ── Ekstrakcja ────────────────────────────────────────────────────────────

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
        map.put("roundId",           round.getRoundId());
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
                "content",   Map.of("userId", userId, "roundId", round.getRoundId(),
                        "status", "NO_DATA", "category", "OTHER",
                        "timedOut", true, "missing", missing)
        ));
        return map;
    }

    // ── Diagnostyka ───────────────────────────────────────────────────────────

    public Map<String, Object> getDebugState() {
        Map<String, Object> state = new LinkedHashMap<>();
        Map<String, Object> users = new LinkedHashMap<>();

        activeRounds.forEach((userId, round) -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("roundId",    round.getRoundId());
            info.put("voteCount",  round.getVoteCount());
            info.put("totalNodes", RoundState.TOTAL_NODES);
            info.put("ageMs",      round.ageMs());
            info.put("timedOut",   round.isTimedOut());
            info.put("missing",    round.missingSenders(ALL_SERVICES));
            info.put("senders",    new ArrayList<>(round.getVotes().keySet()));
            users.put(String.valueOf(userId), info);
        });

        state.put("activeUsers",      users);
        state.put("totalRounds",      users.size());
        state.put("timeoutMs",        RoundState.ROUND_TIMEOUT_MS);
        state.put("minVotes",         RoundState.MIN_VOTES_FOR_EARLY_WBFT);
        state.put("blacklistedNodes", wbftAlgorithm.getBlacklist());
        state.put("serverTimeMs",     System.currentTimeMillis());
        return state;
    }
}