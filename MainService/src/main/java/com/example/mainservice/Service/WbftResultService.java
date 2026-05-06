package com.example.mainservice.Service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Przechowuje historię wyników rund WBFT.
 * Eksponowany przez MonitoringController pod GET /monitor/wbft.
 *
 * Zmiany względem oryginału:
 * - dodana metoda addResult() wywoływana z VoteAggregatorService
 *   (save() zachowane dla kompatybilności wstecznej)
 */
@Component
public class WbftResultService {

    private final Queue<Map<String, Object>> results =
            new ConcurrentLinkedQueue<>();

    private static final int MAX_RESULTS = 100;

    /**
     * Zapisuje wynik rundy WBFT.
     * Wywoływane z VoteAggregatorService po każdej zakończonej rundzie.
     */
    public void addResult(Map<String, Object> result) {
        results.add(result);
        while (results.size() > MAX_RESULTS) {
            results.poll();
        }
    }

    /** Alias zachowany dla kompatybilności wstecznej. */
    public void save(Map<String, Object> result) {
        addResult(result);
    }

    public List<Map<String, Object>> getResults() {
        return new ArrayList<>(results);
    }
}