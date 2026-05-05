package com.webproject.safelogin.controller;

import com.webproject.safelogin.model.RecommendationDTO;
import com.webproject.safelogin.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<RecommendationDTO>> getUserRecommendations(
            @PathVariable int userId) {

        return ResponseEntity.ok(
                recommendationService.getUserRecommendations(userId)
        );
    }

    @PostMapping("/user/{userId}")
    public ResponseEntity<Void> addRecommendation(
            @PathVariable int userId,
            @RequestParam String category) {

        recommendationService.addRecommendation(userId, category);
        return ResponseEntity.ok().build();
    }
    @GetMapping("/user/{userId}/latest")
    public ResponseEntity<RecommendationDTO> getLatestUserRecommendation(
            @PathVariable int userId) {

        RecommendationDTO dto = recommendationService.getLatestRecommendation(userId);
        if (dto == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(dto);
    }
}
