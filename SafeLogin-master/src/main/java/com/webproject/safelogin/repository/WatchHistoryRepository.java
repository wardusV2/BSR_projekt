package com.webproject.safelogin.repository;

import com.webproject.safelogin.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchHistoryRepository extends JpaRepository<WatchHistory, Long> {

    Optional<WatchHistory> findByUserAndVideo(User user, Video video);

    List<WatchHistory> findByUserOrderByWatchedAtDesc(User user);

    // fetch video i owner razem
    @Query("SELECT w FROM WatchHistory w " +
            "JOIN FETCH w.video v " +
            "JOIN FETCH v.owner " +
            "WHERE w.user = :user " +
            "ORDER BY w.watchedAt DESC")
    List<WatchHistory> findByUserWithVideoAndOwner(@Param("user") User user);

}
