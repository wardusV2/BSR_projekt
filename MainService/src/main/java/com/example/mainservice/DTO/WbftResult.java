package com.example.mainservice.DTO;

import java.util.List;
import java.util.Map;

/**
 * Wynik jednej rundy algorytmu Weighted BFT.
 *
 * Record — gettery generowane automatycznie jako nazwa_pola().
 * Dodano pole confident() dla wygody frontendu.
 */
public record WbftResult(

        String category,
        Status status,
        Map<String, Double> weightSums,
        double totalWeight,
        double winnerWeight,
        double winnerRatio,
        List<String> byzantineSuspects,
        int voterCount

) {
    public enum Status {
        CONSENSUS,
        NO_QUORUM,
        UNANIMOUS,
        NO_DATA
    }

    public boolean isConfident() {
        return status == Status.CONSENSUS || status == Status.UNANIMOUS;
    }

    // ── Aliasy dla VoteAggregatorService ────────────────────────────────────
    // Rekordy generują gettery jako category(), status() itd. (bez "get").
    // Poniższe metody to wygodne aliasy, żeby kod w serwisie był czytelny.

    public String getCategory()                  { return category(); }
    public Status getStatus()                    { return status(); }
    public Map<String, Double> getWeightSums()   { return weightSums(); }
    public double getTotalWeight()               { return totalWeight(); }
    public double getWinnerWeight()              { return winnerWeight(); }
    public double getWinnerRatio()               { return winnerRatio(); }
    public List<String> getByzantineSuspects()   { return byzantineSuspects(); }
    public int getVoterCount()                   { return voterCount(); }
}