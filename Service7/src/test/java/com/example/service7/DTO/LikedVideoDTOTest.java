package com.example.service7.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikedVideoDTOTest {

    @Test
    void shouldCreateLikedVideoDto() {

        LikedVideoDTO dto =
                new LikedVideoDTO(
                        1,
                        "Video",
                        "SPORT"
                );

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.title()).isEqualTo("Video");
        assertThat(dto.category()).isEqualTo("SPORT");
    }
}