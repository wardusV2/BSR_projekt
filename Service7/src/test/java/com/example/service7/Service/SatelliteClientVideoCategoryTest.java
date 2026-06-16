package com.example.service7.Service;

import com.example.service7.DTO.VideoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SatelliteClientVideoCategoryTest {

    @Test
    void shouldReturnMostCommonVideoCategory() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateCategoryFromVideos",
                        List.of(
                                new VideoDTO(1,"a","u",1,"x","SPORT"),
                                new VideoDTO(2,"b","u",1,"x","SPORT"),
                                new VideoDTO(3,"c","u",1,"x","MUSIC")
                        )
                );

        assertThat(result).isEqualTo("SPORT");
    }

    @Test
    void shouldReturnOtherWhenVideoListEmpty() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateCategoryFromVideos",
                        List.of()
                );

        assertThat(result).isEqualTo("OTHER");
    }
}