package com.example.service5.DTO;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WatchHistoryDTOTest {

    @Test
    void shouldSetAndGetAllFields() {

        LocalDateTime now = LocalDateTime.now();

        WatchHistoryDTO dto = new WatchHistoryDTO();

        dto.setVideoId(1);
        dto.setTitle("Video");
        dto.setUrl("url");
        dto.setOwnerNick("owner");
        dto.setCategory("MUSIC");
        dto.setWatchedAt(now);
        dto.setLastPositionSeconds(250L);

        assertThat(dto.getVideoId()).isEqualTo(1);
        assertThat(dto.getTitle()).isEqualTo("Video");
        assertThat(dto.getCategory()).isEqualTo("MUSIC");
        assertThat(dto.getWatchedAt()).isEqualTo(now);
        assertThat(dto.getLastPositionSeconds()).isEqualTo(250L);
    }
}