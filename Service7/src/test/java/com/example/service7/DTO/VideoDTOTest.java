package com.example.service7.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoDTOTest {

    @Test
    void shouldCreateVideoDto() {

        VideoDTO dto =
                new VideoDTO(
                        1,
                        "Video",
                        "url",
                        10,
                        "owner",
                        "SPORT"
                );

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.title()).isEqualTo("Video");
        assertThat(dto.url()).isEqualTo("url");
        assertThat(dto.ownerId()).isEqualTo(10);
        assertThat(dto.ownerNick()).isEqualTo("owner");
        assertThat(dto.category()).isEqualTo("SPORT");
    }
}