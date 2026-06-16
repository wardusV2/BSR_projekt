package com.example.service2.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoDTOTest {

    @Test
    void shouldReturnAllFields() {

        VideoDTO dto = new VideoDTO(
                1,
                "Video",
                "url",
                10,
                "owner",
                "MUSIC"
        );

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.category()).isEqualTo("MUSIC");
        assertThat(dto.ownerNick()).isEqualTo("owner");
    }
}