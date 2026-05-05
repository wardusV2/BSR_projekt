package com.webproject.safelogin.controller;

import com.webproject.safelogin.model.SubscriptionDTO;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("subscriber/{subscriberId}/subscribeTarget/{targetId}")
    public ResponseEntity<?> subscribe(
            @PathVariable Integer subscriberId,
            @PathVariable Integer targetId) {
        subscriptionService.subscribe(subscriberId, targetId);
        return ResponseEntity.ok("Zasubskrybowano użytkownika " + targetId);
    }

    @PostMapping("subscriber/{subscriberId}/unsubscribe/{targetId}")
    public ResponseEntity<?> unsubscribe(
            @PathVariable Integer subscriberId,
            @PathVariable Integer targetId) {
        subscriptionService.unsubscribe(subscriberId, targetId);
        return ResponseEntity.ok("Odsubskrybowano użytkownika " + targetId);
    }

    @GetMapping("/getSubscriptions/{userId}")
    public ResponseEntity<List<SubscriptionDTO>> getSubscriptions(
            @PathVariable Integer userId
    ) {
        return ResponseEntity.ok(
                subscriptionService.getSubscriptions(userId)
                        .stream()
                        .map(u -> new SubscriptionDTO(
                                u.getId(),
                                u.getNick(),
                                u.getEmail()
                        ))
                        .toList()
        );
    }

}
