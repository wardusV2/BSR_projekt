package com.webproject.safelogin.repository;

import com.webproject.safelogin.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByUser(User user);
    List<Recommendation> findByUserOrderByCreatedAtDesc(User user);
    Optional<Recommendation> findFirstByUserIdOrderByCreatedAtDesc(int userId);
}
