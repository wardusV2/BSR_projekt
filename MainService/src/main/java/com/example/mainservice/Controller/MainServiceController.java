package com.example.mainservice.Controller;

import com.example.mainservice.Service.VoteAggregatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MainServiceController – uproszczony.
 *
 * Głosy satelit NIE trafiają już tutaj przez WebSocket –
 * odbierane są przez VoteListener z RabbitMQ.
 *
 * Kontroler wystawia tylko endpointy diagnostyczne dla frontendu/dewelopera.
 */
@RestController
@RequestMapping("/api")
public class MainServiceController {

    private final VoteAggregatorService aggregatorService;

    public MainServiceController(VoteAggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    /**
     * Stan aktualnie trwających rund głosowania.
     * GET http://localhost:8081/api/vote/state
     */
    @GetMapping("/vote/state")
    public Map<String, Object> getVoteState() {
        return aggregatorService.getDebugState();
    }

    /**
     * Health check.
     * GET http://localhost:8081/api/health
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "MainService");
    }
}