package com.example.service6.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryDTOTest {

    @Test
    void shouldCreateHistoryDto() {

        HistoryDTO dto =
                new HistoryDTO(
                        1,
                        "Video title",
                        "SPORT"
                );

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.title()).isEqualTo("Video title");
        assertThat(dto.category()).isEqualTo("SPORT");
    }
}