package com.example.service3.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WatchHistoryDTOTest {

    // Odpowiada LikedVideoDTO – nazwa zachowana zgodnie ze strukturą Service1

    @Test
    void likedVideoDTO_shouldStoreId() {
        LikedVideoDTO dto = new LikedVideoDTO(10, "Spring Tutorial", "EDUCATION");

        assertThat(dto.id()).isEqualTo(10);
    }

    @Test
    void likedVideoDTO_shouldStoreTitle() {
        LikedVideoDTO dto = new LikedVideoDTO(10, "Spring Tutorial", "EDUCATION");

        assertThat(dto.title()).isEqualTo("Spring Tutorial");
    }

    @Test
    void likedVideoDTO_shouldStoreCategory() {
        LikedVideoDTO dto = new LikedVideoDTO(10, "Spring Tutorial", "EDUCATION");

        assertThat(dto.category()).isEqualTo("EDUCATION");
    }

    @Test
    void likedVideoDTO_nullCategory_shouldBeAllowed() {
        LikedVideoDTO dto = new LikedVideoDTO(1, "No Category Video", null);

        assertThat(dto.category()).isNull();
    }

    @Test
    void likedVideoDTO_equalObjects_shouldBeEqual() {
        LikedVideoDTO a = new LikedVideoDTO(3, "Title", "SPORT");
        LikedVideoDTO b = new LikedVideoDTO(3, "Title", "SPORT");

        assertThat(a).isEqualTo(b);
    }

    @Test
    void likedVideoDTO_differentObjects_shouldNotBeEqual() {
        LikedVideoDTO a = new LikedVideoDTO(1, "Video A", "SPORT");
        LikedVideoDTO b = new LikedVideoDTO(2, "Video B", "MUSIC");

        assertThat(a).isNotEqualTo(b);
    }
}
