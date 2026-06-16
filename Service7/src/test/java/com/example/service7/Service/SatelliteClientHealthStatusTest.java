package com.example.service7.Service;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SatelliteClientHealthStatusTest {

    @Test
    void shouldCreateHealthStatus() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        SatelliteClient.HealthStatus status =
                client.getHealthStatus();

        assertThat(status).isNotNull();
    }
}