package com.example.mainservice.DTO;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Przechowuje stan jednej rundy głosowania WBFT dla konkretnego użytkownika.
 *
 * Żywotność obiektu:
 * ─────────────────
 * Tworzony gdy nadejdzie pierwszy głos dla userId.
 * Niszczony (usuwany z mapy w VoteAggregatorService) gdy:
 *   a) zebrano głosy od wszystkich 7 serwisów → WBFT uruchamiany natychmiast,
 *   b) minął ROUND_TIMEOUT_MS od startu → WBFT uruchamiany przez scheduler,
 *   c) zebrano MIN_VOTES_FOR_EARLY_WBFT głosów → WBFT uruchamiany wcześniej.
 */
public class RoundState {

    /** Maksymalny czas oczekiwania na wszystkie głosy (30 sekund). */
    public static final long ROUND_TIMEOUT_MS = 30_000;

    /**
     * Liczba głosów wystarczająca do wcześniejszego uruchomienia WBFT
     * (bez czekania na wszystkich 7). Wartość 5 = ponad 2/3 węzłów.
     */
    public static final int MIN_VOTES_FOR_EARLY_WBFT = 5;

    /** Łączna liczba węzłów satelitarnych (Service1…Service7). */
    public static final int TOTAL_NODES = 7;

    /* ── pola ── */

    private final int  userId;
    private final long startTime;

    /**
     * Zebrane głosy: serviceName → ServiceMessage.
     * ConcurrentHashMap — VoteListener i scheduler mogą pisać/czytać z różnych wątków.
     */
    private final Map<String, ServiceMessage> votes = new ConcurrentHashMap<>();

    /** Czy runda została już zakończona (przez WBFT lub timeout). */
    private volatile boolean finished = false;

    /* ── konstruktor ── */

    public RoundState(int userId) {
        this.userId    = userId;
        this.startTime = System.currentTimeMillis();
    }

    /* ── mutacje ── */

    /**
     * Rejestruje głos serwisu. Istniejący głos dla danego serwisu jest nadpisywany
     * (zabezpieczenie przed duplikatami z RabbitMQ).
     *
     * @param serviceName identyfikator serwisu (np. "Service3")
     * @param message     odebrany głos
     */
    public void addVote(String serviceName, ServiceMessage message) {
        votes.put(serviceName, message);
    }

    /** Oznacza rundę jako zakończoną (idempotent). */
    public boolean markFinished() {
        if (finished) return false;
        finished = true;
        return true;
    }

    /* ── zapytania ── */

    /**
     * Czy runda powinna zostać zakończona teraz?
     *
     * Warunki (sprawdzane w kolejności):
     * 1. Zebrano głosy od wszystkich TOTAL_NODES węzłów → natychmiastowe WBFT.
     * 2. Minął ROUND_TIMEOUT_MS od startu → WBFT na zebranych głosach
     *    (o ile mamy MIN_VOTES_FOR_EARLY_WBFT, inaczej NO_DATA).
     *
     * Celowo NIE uruchamiamy WBFT po samym fakcie zebrania MIN_VOTES_FOR_EARLY_WBFT —
     * brakujące serwisy mogą być po prostu wolniejsze, nie wadliwe.
     * Miękkie kworum stosujemy tylko jako fallback po upływie timeoutu.
     */
    public boolean shouldFinish() {
        if (finished) return false;
        if (votes.size() >= TOTAL_NODES) return true;
        return isTimedOut();
    }

    /**
     * Czy po timeoucie mamy wystarczająco głosów żeby uruchomić WBFT?
     * Jeśli nie — scheduler zwróci NO_DATA zamiast uruchamiać algorytm.
     */
    public boolean hasEnoughVotesForWbft() {
        return votes.size() >= MIN_VOTES_FOR_EARLY_WBFT;
    }

    /** Czy runda przekroczyła maksymalny czas oczekiwania? */
    public boolean isTimedOut() {
        return System.currentTimeMillis() - startTime >= ROUND_TIMEOUT_MS;
    }

    /** Ile milisekund upłynęło od startu rundy. */
    public long ageMs() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Lista serwisów, które NIE oddały głosu.
     * Używana przez monitor i do oznaczania węzłów timedOut w WbftResult.
     */
    public java.util.List<String> missingSenders(java.util.List<String> allServices) {
        return allServices.stream()
                .filter(s -> !votes.containsKey(s))
                .toList();
    }

    /* ── gettery ── */

    public int getUserId()                           { return userId; }
    public long getStartTime()                       { return startTime; }
    public boolean isFinished()                      { return finished; }
    public Map<String, ServiceMessage> getVotes()    { return Collections.unmodifiableMap(votes); }
    public int getVoteCount()                        { return votes.size(); }
}