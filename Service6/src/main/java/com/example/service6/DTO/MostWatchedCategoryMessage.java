package com.example.service6.DTO;

public class MostWatchedCategoryMessage {

    private Integer userId;
    private String category;

    public MostWatchedCategoryMessage(Integer userId, String category) {
        this.userId = userId;
        this.category = category;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
