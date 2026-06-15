package com.example.mainservice.Controller;

import com.example.mainservice.Service.SatelliteHealthMonitor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint diagnostyczny dla stanu satelit.
 *
 * GET  /monitor/satellite-health        → snapshot wszystkich satelit
 * POST /monitor/satellite-health/check  → ręczne wyzwolenie sprawdzenia
 */
@RestController
@RequestMapping("/monitor")
public class SatelliteHealthController {

    private final SatelliteHealthMonitor healthMonitor;

    public SatelliteHealthController(SatelliteHealthMonitor healthMonitor) {
        this.healthMonitor = healthMonitor;
    }

    /**
     * Aktualny stan zdrowia wszystkich satelit.
     * GET http://localhost:8081/monitor/satellite-health
     *
     * Przykładowa odpowiedź:
     * {
     *   "services": {
     *     "Service1": { "status": "UP", "url": "http://localhost:8091", "silenceSec": 3, ... },
     *     "Service2": { "status": "DOWN", "silenceSec": 67, "alertSent": true, ... },
     *     ...
     *   },
     *   "totalServices": 7,
     *   "downCount": 1,
     *   "alertThresholdMs": 45000,
     *   "checkIntervalMs": 15000,
     *   "serverTimeMs": 1234567890
     * }
     */
    @GetMapping("/satellite-health")
    public Map<String, Object> getHealth() {
        return healthMonitor.getHealthSnapshot();
    }

    /**
     * Ręczne wyzwolenie sprawdzenia zdrowia satelit.
     * POST http://localhost:8081/monitor/satellite-health/check
     *
     * Przydatne do testowania lub wymuszenia natychmiastowego sprawdzenia
     * bez czekania na kolejny interwał schedulera.
     */
    @PostMapping("/satellite-health/check")
    public Map<String, Object> triggerCheck() {
        healthMonitor.triggerCheck();
        return healthMonitor.getHealthSnapshot();
    }
}