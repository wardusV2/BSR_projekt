package com.example.service2.Service;

import com.example.service2.DTO.VideoDTO;
import com.example.service2.Fault.FaultState;
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
                        mock(RabbitTemplate.class),
                        new FaultState()
                );

        List<VideoDTO> videos = List.of(
                new VideoDTO(1,"A","url",1,"x","MUSIC"),
                new VideoDTO(2,"B","url",1,"x","MUSIC"),
                new VideoDTO(3,"C","url",1,"x","SPORT")
        );

        String category =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateCategory",
                        videos
                );

        assertThat(category).isEqualTo("MUSIC");
    }

    @Test
    void shouldReturnNoneForEmptyList() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class),
                        new FaultState()
                );

        String category =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateCategory",
                        List.of()
                );

        assertThat(category).isEqualTo("NONE");
    }

    @Test
    void shouldIgnoreNullCategories() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class),
                        new FaultState()
                );

        List<VideoDTO> videos = List.of(
                new VideoDTO(1,"A","url",1,"x",null),
                new VideoDTO(2,"B","url",1,"x","SPORT")
        );

        String category =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateCategory",
                        videos
                );

        assertThat(category).isEqualTo("SPORT");
    }
}