package com.webproject.safelogin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class WatchHistoryDTO {

    private Integer videoId;
    private String title;
    private String url;
    private String ownerNick;
    private String category;
    private LocalDateTime watchedAt;
    private Long lastPositionSeconds;

    public WatchHistoryDTO(
            Integer videoId,
            String title,
            String url,
            String ownerNick,
            String category,
            LocalDateTime watchedAt,
            Long lastPositionSeconds
    ) {
        this.videoId = videoId;
        this.title = title;
        this.url = url;
        this.ownerNick = ownerNick;
        this.category = category;
        this.watchedAt = watchedAt;
        this.lastPositionSeconds = lastPositionSeconds;
    }

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
    // gettery
}
