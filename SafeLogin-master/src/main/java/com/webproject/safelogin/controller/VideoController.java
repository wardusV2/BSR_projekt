package com.webproject.safelogin.controller;


import com.webproject.safelogin.model.*;
import com.webproject.safelogin.repository.UserRepository;
import com.webproject.safelogin.repository.VideoRepository;
import com.webproject.safelogin.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class VideoController {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SubscriptionService subscriptionService;

    @PostMapping("/addVideo")
    public ResponseEntity<String> addVideo(@RequestBody VideoDTO videoDTO) {
        User owner = userRepository.findById(videoDTO.getOwnerId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Video video = new Video();
        video.setTitle(videoDTO.getTitle());
        video.setUrl(videoDTO.getUrl());
        video.setOwner(owner);

        // ⬇️ NOWE
        if (videoDTO.getCategory() != null) {
            video.setCategory(
                    Category.valueOf(videoDTO.getCategory())
            );
        }

        videoRepository.save(video);
        return ResponseEntity.ok("Video saved");
    }


    @GetMapping("/getVideo/{id}")
    public ResponseEntity<VideoResponseDTO> getVideo(@PathVariable Integer id) {
        return videoRepository.findById(id)
                .map(video -> {
                    VideoResponseDTO dto = new VideoResponseDTO(
                            video.getId(),
                            video.getTitle(),
                            video.getUrl(),
                            video.getOwner() != null ? video.getOwner().getId() : null,
                            video.getOwner() != null ? video.getOwner().getNick() : null,
                            video.getCategory().name()
                    );
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/AllVideos")
    public List<VideoResponseDTO> getAllVideos() {
        return videoRepository.findAll().stream()
                .map(video -> new VideoResponseDTO(
                        video.getId(),
                        video.getTitle(),
                        video.getUrl(),
                        video.getOwner().getId(),
                        video.getOwner().getNick(),
                        video.getCategory() != null
                                ? video.getCategory().name()
                                : null
                ))
                .collect(Collectors.toList());
    }
    @GetMapping("/videosByUser/{userId}")
    public List<VideoResponseDTO> getVideosByUser(@PathVariable Integer userId) {
        return videoRepository.findAll().stream()
                .filter(video -> video.getOwner() != null && video.getOwner().getId() == userId)
                .map(video -> new VideoResponseDTO(
                        video.getId(),
                        video.getTitle(),
                        video.getUrl(),
                        video.getOwner().getId(),
                        video.getOwner().getNick(),
                        video.getCategory() != null
                                ? video.getCategory().name()
                                : null
                ))
                .collect(Collectors.toList());
    }
    @GetMapping("/subscribedVideos/{userId}")
    public List<VideoResponseDTO> getSubscribedVideos(@PathVariable Integer userId) {
        Set<User> subscriptions = subscriptionService.getSubscriptions(userId);

        return videoRepository.findAll().stream()
                .filter(video -> video.getOwner() != null && subscriptions.contains(video.getOwner()))
                .map(video -> new VideoResponseDTO(
                        video.getId(),
                        video.getTitle(),
                        video.getUrl(),
                        video.getOwner().getId(),
                        video.getOwner().getNick(),
                        video.getCategory() != null
                                ? video.getCategory().name()
                                : null
                ))
                .collect(Collectors.toList());
    }
    @GetMapping("/videos/category/{category}")
    public List<VideoResponseDTO> getVideosByCategory(@PathVariable String category) {
        Category cat;
        try {
            cat = Category.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return List.of(); // jeśli niepoprawna kategoria, zwróć pustą listę
        }

        return videoRepository.findAll().stream()
                .filter(video -> video.getCategory() != null && video.getCategory() == cat)
                .map(video -> new VideoResponseDTO(
                        video.getId(),
                        video.getTitle(),
                        video.getUrl(),
                        video.getOwner().getId(),
                        video.getOwner().getNick(),
                        video.getCategory() != null
                                ? video.getCategory().name()
                                : null
                ))
                .collect(Collectors.toList());
    }

}
