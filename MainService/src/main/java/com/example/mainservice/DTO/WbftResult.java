package com.example.mainservice.DTO;
import java.util.List;
import java.util.Map;

/**
 * Wynik jednej rundy algorytmu Weighted BFT.
 */
public record WbftResult(

        // Zwycięska kategoria lub "OTHER" gdy brak kworum
        String category,

        // Status rundy
        Status status,

        // Sumy wag per kategoria
        Map<String, Double> weightSums,

        // Całkowita suma wag
        double totalWeight,

        // Suma wag zwycięzcy
        double winnerWeight,

        // Stosunek wag zwycięzcy do sumy (0.0–1.0)
        double winnerRatio,

        // Węzły podejrzane o błąd bizantyjski
        List<String> byzantineSuspects,

        // Liczba głosujących węzłów
        int voterCount
) {
    public enum Status {
        // Wyraźne kworum – pewny wynik
        CONSENSUS,
        // Brak kworum – wynik niepewny
        NO_QUORUM,
        // Pojedyncza kategoria (wszyscy zgodni)
        UNANIMOUS,
        // Brak danych
        NO_DATA
    }

    public boolean isConfident() {
        return status == Status.CONSENSUS || status == Status.UNANIMOUS;
    }
}