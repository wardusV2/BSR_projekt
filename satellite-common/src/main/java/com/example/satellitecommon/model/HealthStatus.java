package com.example.satellitecommon.model;

public record HealthStatus(
        String serviceName,
        int messagesSent,
        boolean loopRunning,
        String failureReason
) {
}