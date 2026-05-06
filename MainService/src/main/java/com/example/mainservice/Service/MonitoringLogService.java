package com.example.mainservice.Service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class MonitoringLogService {

    private final Queue<Map<String, Object>> logs =
            new ConcurrentLinkedQueue<>();

    private static final int MAX_LOGS = 200;

    public void log(Map<String, Object> entry) {
        logs.add(entry);

        while (logs.size() > MAX_LOGS) {
            logs.poll();
        }
    }

    public List<Map<String, Object>> getLogs() {
        return new ArrayList<>(logs);
    }
}