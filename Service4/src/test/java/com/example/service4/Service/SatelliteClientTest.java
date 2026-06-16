package com.example.service4.Service;

import com.example.service4.DTO.WatchHistoryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SatelliteClientTest {

    @Test
    void shouldReturnRarestCategory() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        WatchHistoryDTO a = new WatchHistoryDTO();
        a.setCategory("SPORT");

        WatchHistoryDTO b = new WatchHistoryDTO();
        b.setCategory("SPORT");

        WatchHistoryDTO c = new WatchHistoryDTO();
        c.setCategory("MUSIC");

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateRarestCategory",
                        List.of(a, b, c)
                );

        assertThat(result).isEqualTo("MUSIC");
    }

    @Test
    void shouldReturnOtherForEmptyHistory() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateRarestCategory",
                        List.of()
                );

        assertThat(result).isEqualTo("OTHER");
    }

    @Test
    void shouldIgnoreNullCategories() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        WatchHistoryDTO a = new WatchHistoryDTO();
        a.setCategory(null);

        WatchHistoryDTO b = new WatchHistoryDTO();
        b.setCategory("NEWS");

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateRarestCategory",
                        List.of(a, b)
                );

        assertThat(result).isEqualTo("NEWS");
    }
}