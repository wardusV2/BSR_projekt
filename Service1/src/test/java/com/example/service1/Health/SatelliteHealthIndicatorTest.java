package com.example.service1.Health;

import com.example.service1.Service.SatelliteClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SatelliteHealthIndicatorTest {

    private SatelliteClient satelliteClient;
    private SatelliteHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        satelliteClient = mock(SatelliteClient.class);
        indicator = new SatelliteHealthIndicator(satelliteClient);
    }

    @Test
    void health_whenLoopRunning_shouldReturnUp() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service1", true, 42, ""));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_whenLoopRunning_shouldContainServiceName() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service1", true, 10, ""));
        assertThat(indicator.health().getDetails()).containsEntry("name", "Service1");
    }

    @Test
    void health_whenLoopRunning_shouldContainMessagesSent() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service1", true, 99, ""));
        assertThat(indicator.health().getDetails()).containsEntry("messagesSent", 99);
    }

    @Test
    void health_whenLoopRunning_shouldContainLastLoopOkTrue() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service1", true, 0, ""));
        assertThat(indicator.health().getDetails()).containsEntry("lastLoopOk", true);
    }

    @Test
    void health_whenLoopNotRunning_shouldReturnDown() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service1", false, 5, "Connection refused"));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void health_whenLoopNotRunning_shouldContainReason() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service1", false, 0, "Connection refused"));
        Health health = indicator.health();
        assertThat(health.getDetails()).containsEntry("reason", "Connection refused");
        assertThat(health.getDetails()).containsEntry("lastLoopOk", false);
    }

    @Test
    void health_whenLoopRunning_shouldNotContainReasonKey() {
        when(satelliteClient.getHealthStatus()).thenReturn(
                new SatelliteClient.HealthStatus("Service1", true, 1, ""));
        assertThat(indicator.health().getDetails()).doesNotContainKey("reason");
    }
}