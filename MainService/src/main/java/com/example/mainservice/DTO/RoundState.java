package com.example.mainservice.DTO;

import com.example.mainservice.Service.WbftAlgorithm;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Stan jednej rundy konsensusu dla danego użytkownika.
 *
 * Zmiana względem oryginału (warstwa security):
 * ──────────────────────────────────────────────
 * Dodano pole roundId (UUID) generowane przy tworzeniu rundy.
 * roundId jest przekazywany do WbftAlgorithm.compute() i służy do:
 * - śledzenia zużytych głosów (anty-replay)
 * - wykrywania equivocation
 * - logowania i monitoringu
 */
public class RoundState {

    public static final int  TOTAL_NODES              = 7;
    public static final int  MIN_VOTES_FOR_EARLY_WBFT = 5;
    public static final long ROUND_TIMEOUT_MS         = 30_000L;

    private final int    userId;

    /**
     * Unikalny identyfikator rundy – generowany przy każdym nowym RoundState.
     * Format: "u{userId}-{UUID skrócony do 8 znaków}"
     * Przykład: "u42-3f8a1b2c"
     */
    private final String roundId;

    private final long   startTime = System.currentTimeMillis();

    /** serviceName → głos. ConcurrentHashMap dla bezpieczeństwa wątkowego. */
    private final Map<String, ServiceMessage> votes = new ConcurrentHashMap<>();

    /** Atomowa flaga – runda zakończona dokładnie raz. */
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public RoundState(int userId) {
        this.userId  = userId;
        this.roundId = "u" + userId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ── Mutacje ───────────────────────────────────────────────────────────────

    public void addVote(String serviceName, ServiceMessage message) {
        votes.put(serviceName, message);
    }

    /**
     * Atomowe oznaczenie rundy jako zakończonej.
     * @return true jeśli to wywołanie zamknęło rundę (pierwsze);
     *         false jeśli runda była już zamknięta.
     */
    public boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    // ── Warunki zakończenia ───────────────────────────────────────────────────

    /** Pełna liczba głosów LUB osiągnięto kworum miękkie. */
    public boolean shouldFinish() {
        return votes.size() >= TOTAL_NODES
                || votes.size() >= MIN_VOTES_FOR_EARLY_WBFT;
    }

    /** Czy zebrano wystarczająco głosów do uruchomienia WBFT po timeout. */
    public boolean hasEnoughVotesForWbft() {
        return votes.size() >= MIN_VOTES_FOR_EARLY_WBFT;
    }

    /** Czy runda przekroczyła limit czasu. */
    public boolean isTimedOut() {
        return ageMs() > ROUND_TIMEOUT_MS;
    }

    // ── Odczyty ───────────────────────────────────────────────────────────────

    public boolean isFinished()    { return finished.get(); }
    public int     getUserId()     { return userId; }
    public String  getRoundId()    { return roundId; }
    public int     getVoteCount()  { return votes.size(); }
    public long    ageMs()         { return System.currentTimeMillis() - startTime; }

    public Map<String, ServiceMessage> getVotes() {
        return votes;
    }

    /**
     * Zwraca listę serwisów, od których nie otrzymano głosu.
     */
    public List<String> missingSenders(List<String> allServices) {
        Set<String> received = votes.keySet();
        return allServices.stream()
                .filter(s -> !received.contains(s))
                .sorted()
                .collect(Collectors.toList());
    }
}