package com.example.service6.Health;

import com.example.service6.Service.SatelliteClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;


/**
 * Własny HealthIndicator satelity.
 *
 * GET /actuator/health zwróci:
 * ────────────────────────────
 * {
 *   "status": "UP",
 *   "components": {
 *     "satellite": {
 *       "status": "UP",
 *       "details": {
 *         "name": "Service1",
 *         "messagesSent": 42,
 *         "lastLoopOk": true
 *       }
 *     }
 *   }
 * }
 *
 * Status "DOWN" gdy pętla wysyłania nie działa →
 * MainService wykryje to przez health-check i wyśle alert.
 *
 * Dodaj do application.properties:
 *   management.endpoint.health.show-details=always   ← jeśli chcesz szczegóły
 *   management.endpoint.health.show-details=never    ← domyślne (tylko status)
 */
@Component
public class SatelliteHealthIndicator implements HealthIndicator {

    private final SatelliteClient satelliteClient;

    public SatelliteHealthIndicator(SatelliteClient satelliteClient) {
        this.satelliteClient = satelliteClient;
    }

    @Override
    public Health health() {
        SatelliteClient.HealthStatus status = satelliteClient.getHealthStatus();

        if (status.loopRunning()) {
            return Health.up()
                    .withDetail("name",         status.serviceName())
                    .withDetail("messagesSent", status.messagesSent())
                    .withDetail("lastLoopOk",   true)
                    .build();
        } else {
            return Health.down()
                    .withDetail("name",         status.serviceName())
                    .withDetail("messagesSent", status.messagesSent())
                    .withDetail("lastLoopOk",   false)
                    .withDetail("reason",       status.failureReason())
                    .build();
        }
    }
}