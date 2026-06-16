// WatchHistoryDTOTest.java
package com.example.service1.DTO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class WatchHistoryDTOTest {
    @Test void defaultConstructor_shouldCreateEmptyObject() {
        WatchHistoryDTO dto = new WatchHistoryDTO();
        assertThat(dto.getVideoId()).isNull();
        assertThat(dto.getCategory()).isNull();
    }
    @Test void settersAndGetters_shouldWorkCorrectly() {
        WatchHistoryDTO dto = new WatchHistoryDTO();
        LocalDateTime now = LocalDateTime.now();
        dto.setVideoId(10); dto.setTitle("Test"); dto.setCategory("Comedy");
        dto.setWatchedAt(now); dto.setLastPositionSeconds(120L);
        assertThat(dto.getVideoId()).isEqualTo(10);
        assertThat(dto.getCategory()).isEqualTo("Comedy");
        assertThat(dto.getWatchedAt()).isEqualTo(now);
        assertThat(dto.getLastPositionSeconds()).isEqualTo(120L);
    }
}