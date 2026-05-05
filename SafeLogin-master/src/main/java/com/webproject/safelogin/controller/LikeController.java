package com.webproject.safelogin.controller;

import com.webproject.safelogin.model.LikedVideoDTO;
import com.webproject.safelogin.service.LikeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping("/users/{userId}/like/{videoId}")
    public ResponseEntity<?> like(
            @PathVariable int userId,
            @PathVariable int videoId) {

        likeService.likeVideo(userId, videoId);
        return ResponseEntity.ok("Polubiono video " + videoId);
    }

    @PostMapping("/users/{userId}/unlike/{videoId}")
    public ResponseEntity<?> unlike(
            @PathVariable int userId,
            @PathVariable int videoId) {

        likeService.unlikeVideo(userId, videoId);
        return ResponseEntity.ok("Usunięto like z video " + videoId);
    }

    @GetMapping("/users/{userId}/liked")
    public ResponseEntity<List<LikedVideoDTO>> likedVideos(@PathVariable int userId) {

        return ResponseEntity.ok(
                likeService.getLikedVideos(userId).stream()
                        .map(v -> new LikedVideoDTO(
                                v.getId(),
                                v.getTitle(),
                                v.getCategory() != null ? v.getCategory().name() : null
                        ))
                        .toList()
        );
    }
}

