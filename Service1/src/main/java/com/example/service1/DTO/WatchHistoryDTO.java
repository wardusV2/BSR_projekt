package com.example.service1.DTO;

import java.time.LocalDateTime;

public class WatchHistoryDTO {

    private Integer videoId;
    private String title;
    private String url;
    private String ownerNick;
    private String category;          // ✅ NOWE
    private LocalDateTime watchedAt;
    private Long lastPositionSeconds;

    public WatchHistoryDTO() {}

    // gettery / settery


    public Integer getVideoId() {
        return videoId;
    }

    public void setVideoId(Integer videoId) {
        this.videoId = videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOwnerNick() {
        return ownerNick;
    }

    public void setOwnerNick(String ownerNick) {
        this.ownerNick = ownerNick;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getWatchedAt() {
        return watchedAt;
    }

    public void setWatchedAt(LocalDateTime watchedAt) {
        this.watchedAt = watchedAt;
    }

    public Long getLastPositionSeconds() {
        return lastPositionSeconds;
    }

    public void setLastPositionSeconds(Long lastPositionSeconds) {
        this.lastPositionSeconds = lastPositionSeconds;
    }
}