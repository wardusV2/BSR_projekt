package com.webproject.safelogin.controller;

import com.webproject.safelogin.model.User;
import com.webproject.safelogin.model.WatchHistoryDTO;
import com.webproject.safelogin.model.Video;
import com.webproject.safelogin.service.WatchHistoryService;
import com.webproject.safelogin.repository.UserRepository;
import com.webproject.safelogin.repository.VideoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class WatchHistoryController {

    private final WatchHistoryService historyService;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    public WatchHistoryController(
            WatchHistoryService historyService,
            UserRepository userRepository,
            VideoRepository videoRepository
    ) {
        this.historyService = historyService;
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
    }

    // Dodajemy historię dla konkretnego użytkownika i wideo
    @PostMapping("/add")
    public ResponseEntity<?> addHistory(@RequestBody Map<String, Object> request) {
        Integer userId = (Integer) request.get("userId");
        Integer videoId = ((Number) request.get("videoId")).intValue();
        Long position = request.get("position") != null ? ((Number) request.get("position")).longValue() : null;

        // wywołanie serwisu używając ID zamiast całych obiektów
        historyService.saveHistory(userId, videoId, position);

        return ResponseEntity.ok("Historia zapisana");
    }

    // Pobieramy historię użytkownika po jego ID
    @GetMapping("/get/{userId}")
    public ResponseEntity<?> getHistory(@PathVariable Integer userId) {
        System.out.println("=== DEBUG: Getting history for userId: " + userId);

        try {
            // Test: czy użytkownik istnieje?
            boolean userExists = userRepository.existsById(userId);
            System.out.println("User exists: " + userExists);

            // Test: ile rekordów historii?
//            long count = historyRepository.countByUserId(userId);
//            System.out.println("History count: " + count);

            List<WatchHistoryDTO> history = historyService.getUserHistory(userId);
            System.out.println("DTO list size: " + (history != null ? history.size() : "null"));

            return ResponseEntity.ok(history);
        } catch (Exception e) {
            e.printStackTrace(); // Wydrukuj pełny stack trace
            throw e; // Przekaż dalej, żeby zobaczyć w logach
        }
    }

}
