package com.example.mainservice.Controller;


import com.example.mainservice.Service.MonitoringLogService;
import com.example.mainservice.Service.VoteAggregatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitor")
public class MonitoringController {

    private final VoteAggregatorService aggregatorService;
    private final MonitoringLogService logService;

    public MonitoringController(VoteAggregatorService aggregatorService, MonitoringLogService logService) {
        this.aggregatorService = aggregatorService;
        this.logService = logService;
    }

    @GetMapping("/state")
    public Map<String, Object> getState() {
        return aggregatorService.getDebugState();
    }
    @GetMapping("/logs")
    public List<Map<String, Object>> getLogs() {
        return logService.getLogs();
    }
}