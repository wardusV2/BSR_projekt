package com.example.service3.Health;

import com.example.service3.Service.SatelliteClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SatelliteHealthIndicatorTest {

    @Mock
    private SatelliteClient satelliteClient;

    @InjectMocks
    private SatelliteHealthIndicator healthIndicator;

    // ── Status UP ─────────────────────────────────────────────────────────────

    @Test
    void health_whenLoopRunning_shouldReturnStatusUp() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service3", 10, true, null)
        );

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_whenLoopRunning_shouldIncludeServiceName() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service3", 5, true, null)
        );

        Health health = healthIndicator.health();

        assertThat(health.getDetails()).containsEntry("name", "Service3");
    }

    @Test
    void health_whenLoopRunning_shouldIncludeMessagesSent() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service3", 42, true, null)
        );

        Health health = healthIndicator.health();

        assertThat(health.getDetails()).containsEntry("messagesSent", 42);
    }

    @Test
    void health_whenLoopRunning_shouldIncludeLastLoopOkTrue() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service3", 1, true, null)
        );

        Health health = healthIndicator.health();

        assertThat(health.getDetails()).containsEntry("lastLoopOk", true);
    }

    // ── Status DOWN ───────────────────────────────────────────────────────────

    @Test
    void health_whenLoopNotRunning_shouldReturnStatusDown() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service3", 0, false, "Tryb OFFLINE")
        );

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void health_whenLoopNotRunning_shouldIncludeLastLoopOkFalse() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service3", 0, false, "Błąd połączenia")
        );

        Health health = healthIndicator.health();

        assertThat(health.getDetails()).containsEntry("lastLoopOk", false);
    }

    @Test
    void health_whenLoopNotRunning_shouldIncludeFailureReason() {
        String reason = "Tryb OFFLINE (fault injection)";
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service3", 0, false, reason)
        );

        Health health = healthIndicator.health();

        assertThat(health.getDetails()).containsEntry("reason", reason);
    }

    @Test
    void health_whenLoopNotRunning_shouldIncludeServiceName() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service3", 0, false, "Fault")
        );

        Health health = healthIndicator.health();

        assertThat(health.getDetails()).containsEntry("name", "Service3");
    }

    @Test
    void health_whenLoopNotRunning_shouldIncludeMessagesSent() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service3", 7, false, "Fault")
        );

        Health health = healthIndicator.health();

        assertThat(health.getDetails()).containsEntry("messagesSent", 7);
    }

    // ── HealthStatus record ────────────────────────────────────────────────────

    @Test
    void healthStatus_shouldStoreAllFields() {
        SatelliteClient.HealthStatus status =
                new SatelliteClient.HealthStatus("Service3", 99, true, null);

        assertThat(status.serviceName()).isEqualTo("Service3");
        assertThat(status.messagesSent()).isEqualTo(99);
        assertThat(status.loopRunning()).isTrue();
        assertThat(status.failureReason()).isNull();
    }
}
