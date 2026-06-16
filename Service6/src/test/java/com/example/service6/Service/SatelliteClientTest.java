package com.example.service6.Service;

import com.example.service6.DTO.WatchHistoryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SatelliteClientTest {

    @Test
    void shouldReturnMostWatchedCategory() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        WatchHistoryDTO h1 = new WatchHistoryDTO();
        h1.setCategory("SPORT");

        WatchHistoryDTO h2 = new WatchHistoryDTO();
        h2.setCategory("SPORT");

        WatchHistoryDTO h3 = new WatchHistoryDTO();
        h3.setCategory("MUSIC");

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateMostWatchedCategory",
                        List.of(h1, h2, h3)
                );

        assertThat(result).isEqualTo("SPORT");
    }

    @Test
    void shouldReturnNoneForEmptyHistory() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateMostWatchedCategory",
                        List.of()
                );

        assertThat(result).isEqualTo("NONE");
    }

    @Test
    void shouldIgnoreNullCategories() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        WatchHistoryDTO h1 = new WatchHistoryDTO();
        h1.setCategory(null);

        WatchHistoryDTO h2 = new WatchHistoryDTO();
        h2.setCategory("NEWS");

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateMostWatchedCategory",
                        List.of(h1, h2)
                );

        assertThat(result).isEqualTo("NEWS");
    }
}