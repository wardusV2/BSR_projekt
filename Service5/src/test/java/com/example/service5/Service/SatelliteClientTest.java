package com.example.service5.Service;

import com.example.service5.DTO.VideoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SatelliteClientTest {

    @Test
    void shouldReturnMostPopularCategory() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        VideoDTO v1 = new VideoDTO();
        v1.setCategory("SPORT");

        VideoDTO v2 = new VideoDTO();
        v2.setCategory("SPORT");

        VideoDTO v3 = new VideoDTO();
        v3.setCategory("MUSIC");

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateMostPopularCategory",
                        List.of(v1, v2, v3)
                );

        assertThat(result).isEqualTo("SPORT");
    }

    @Test
    void shouldReturnNoneForEmptyList() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateMostPopularCategory",
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

        VideoDTO v1 = new VideoDTO();
        v1.setCategory(null);

        VideoDTO v2 = new VideoDTO();
        v2.setCategory("NEWS");

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateMostPopularCategory",
                        List.of(v1, v2)
                );

        assertThat(result).isEqualTo("NEWS");
    }
}