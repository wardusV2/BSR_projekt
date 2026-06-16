package com.example.service5.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoDTOTest {

    @Test
    void shouldSetAndGetAllFields() {

        VideoDTO dto = new VideoDTO();

        dto.setId(1);
        dto.setTitle("Video");
        dto.setUrl("url");
        dto.setOwnerId(15);
        dto.setOwnerNick("owner");
        dto.setCategory("SPORT");

        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getTitle()).isEqualTo("Video");
        assertThat(dto.getUrl()).isEqualTo("url");
        assertThat(dto.getOwnerId()).isEqualTo(15);
        assertThat(dto.getOwnerNick()).isEqualTo("owner");
        assertThat(dto.getCategory()).isEqualTo("SPORT");
    }
}