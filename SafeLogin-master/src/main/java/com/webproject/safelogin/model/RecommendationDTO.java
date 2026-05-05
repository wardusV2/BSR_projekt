package com.webproject.safelogin.model;

import java.time.LocalDateTime;

public class RecommendationDTO {

    private String category;
    private LocalDateTime createdAt;

    public RecommendationDTO(String category, LocalDateTime createdAt) {
        this.category = category;
        this.createdAt = createdAt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
