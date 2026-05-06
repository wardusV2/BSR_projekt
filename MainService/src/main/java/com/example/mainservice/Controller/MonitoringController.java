package com.example.mainservice.Controller;


import com.example.mainservice.Service.MonitoringLogService;
import com.example.mainservice.Service.VoteAggregatorService;
import com.example.mainservice.Service.WbftResultService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitor")
public class MonitoringController {

    private final VoteAggregatorService aggregatorService;
    private final MonitoringLogService  logService;
    private final WbftResultService wbftResultService;

    public MonitoringController(VoteAggregatorService aggregatorService,
                                MonitoringLogService logService,
                                WbftResultService wbftResultService) {
        this.aggregatorService = aggregatorService;
        this.logService        = logService;
        this.wbftResultService = wbftResultService;
    }

    /** Stan aktywnych rund (użytkownicy, oczekujące głosy). */
    @GetMapping("/state")
    public Map<String, Object> getState() {
        return aggregatorService.getDebugState();
    }

    /** Ogólne logi komunikacji (RABBIT_IN, ERROR, …). */
    @GetMapping("/logs")
    public List<Map<String, Object>> getLogs() {
        return logService.getLogs();
    }

    /**
     * Historia wyników rund WBFT.
     * Każdy element zawiera pola:
     *   userId, category, status, winnerRatio, totalWeight,
     *   weightSums, byzantineSuspects, voterCount, confident, timestamp
     */
    @GetMapping("/wbft")
    public List<Map<String, Object>> getWbftResults() {
        return wbftResultService.getResults();
    }
}