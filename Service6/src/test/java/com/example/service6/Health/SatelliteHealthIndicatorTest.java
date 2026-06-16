package com.example.service6.Health;

import com.example.service6.Service.SatelliteClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SatelliteHealthIndicatorTest {

    @Test
    void shouldReturnUpHealth() {

        SatelliteClient client =
                mock(SatelliteClient.class);

        when(client.getHealthStatus())
                .thenReturn(
                        new SatelliteClient.HealthStatus(
                                "Service6",
                                100,
                                true,
                                null
                        )
                );

        SatelliteHealthIndicator indicator =
                new SatelliteHealthIndicator(client);

        Health health = indicator.health();

        assertThat(health.getStatus().getCode())
                .isEqualTo("UP");
    }

    @Test
    void shouldReturnDownHealth() {

        SatelliteClient client =
                mock(SatelliteClient.class);

        when(client.getHealthStatus())
                .thenReturn(
                        new SatelliteClient.HealthStatus(
                                "Service6",
                                100,
                                false,
                                "Loop failed"
                        )
                );

        SatelliteHealthIndicator indicator =
                new SatelliteHealthIndicator(client);

        Health health = indicator.health();

        assertThat(health.getStatus().getCode())
                .isEqualTo("DOWN");

        assertThat(
                health.getDetails().get("reason")
        ).isEqualTo("Loop failed");
    }
}