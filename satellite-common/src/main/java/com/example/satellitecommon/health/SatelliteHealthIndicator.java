package com.example.satellitecommon.health;

import com.example.satellitecommon.model.HealthStatus;
import com.example.satellitecommon.provider.HealthStatusProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class SatelliteHealthIndicator implements HealthIndicator {

    private final HealthStatusProvider provider;

    public SatelliteHealthIndicator(
            HealthStatusProvider provider
    ) {
        this.provider = provider;
    }

    @Override
    public Health health() {

        HealthStatus status =
                provider.getHealthStatus();

        if (status.loopRunning()) {

            return Health.up()
                    .withDetail("name",
                            status.serviceName())
                    .withDetail("messagesSent",
                            status.messagesSent())
                    .withDetail("lastLoopOk",
                            true)
                    .build();
        }

        return Health.down()
                .withDetail("name",
                        status.serviceName())
                .withDetail("messagesSent",
                        status.messagesSent())
                .withDetail("lastLoopOk",
                        false)
                .withDetail("reason",
                        status.failureReason())
                .build();
    }
}