package com.example.service7.Service;

import com.example.service7.DTO.LikedVideoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SatelliteClientLikedCategoryTest {

    @Test
    void shouldReturnMostCommonLikedCategory() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateCategoryFromLikes",
                        List.of(
                                new LikedVideoDTO(1,"a","NEWS"),
                                new LikedVideoDTO(2,"b","NEWS"),
                                new LikedVideoDTO(3,"c","SPORT")
                        )
                );

        assertThat(result).isEqualTo("NEWS");
    }

    @Test
    void shouldReturnOtherForEmptyLikes() {

        SatelliteClient client =
                new SatelliteClient(
                        mock(RabbitTemplate.class)
                );

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateCategoryFromLikes",
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

        String result =
                (String) ReflectionTestUtils.invokeMethod(
                        client,
                        "calculateCategoryFromLikes",
                        List.of(
                                new LikedVideoDTO(1,"a",null),
                                new LikedVideoDTO(2,"b","MUSIC")
                        )
                );

        assertThat(result).isEqualTo("MUSIC");
    }
}