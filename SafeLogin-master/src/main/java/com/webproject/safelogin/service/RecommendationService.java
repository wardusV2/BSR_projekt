package com.webproject.safelogin.service;

import com.webproject.safelogin.model.Category;
import com.webproject.safelogin.model.Recommendation;
import com.webproject.safelogin.model.RecommendationDTO;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.repository.RecommendationRepository;
import com.webproject.safelogin.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;

    public RecommendationService(RecommendationRepository recommendationRepository,
                                 UserRepository userRepository) {
        this.recommendationRepository = recommendationRepository;
        this.userRepository = userRepository;
    }


    public List<RecommendationDTO> getUserRecommendations(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return recommendationRepository.findByUser(user)
                .stream()
                .map(r -> new RecommendationDTO(
                        r.getCategory().name(),
                        r.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    public void addRecommendation(int userId, String category) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Category categoryEnum = Category.valueOf(category.toUpperCase());

        Recommendation recommendation = new Recommendation(user, categoryEnum);
        recommendationRepository.save(recommendation);
    }

    public RecommendationDTO getLatestRecommendation(int userId) {
        return recommendationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(r -> new RecommendationDTO(
                        r.getCategory().name(),
                        r.getCreatedAt()
                ))
                .orElse(null);


    }
}
