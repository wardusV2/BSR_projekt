package com.webproject.safelogin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "watch_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "video_id"})
)
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    private LocalDateTime watchedAt;

    // opcjonalnie – bardzo polecam
    private Long lastPositionSeconds;

    public WatchHistory() {}

    public WatchHistory(User user, Video video) {
        this.user = user;
        this.video = video;
        this.watchedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Video getVideo() {
        return video;
    }

    public void setVideo(Video video) {
        this.video = video;
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
// gettery/settery
}
