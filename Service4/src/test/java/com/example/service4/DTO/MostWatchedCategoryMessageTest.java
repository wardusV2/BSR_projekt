package com.example.service4.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MostWatchedCategoryMessageTest {

    @Test
    void shouldCreateMessage() {

        MostWatchedCategoryMessage message =
                new MostWatchedCategoryMessage(
                        5,
                        "SPORT"
                );

        assertThat(message.getUserId()).isEqualTo(5);
        assertThat(message.getCategory()).isEqualTo("SPORT");
    }

    @Test
    void shouldUpdateValues() {

        MostWatchedCategoryMessage message =
                new MostWatchedCategoryMessage(1, "MUSIC");

        message.setUserId(10);
        message.setCategory("NEWS");

        assertThat(message.getUserId()).isEqualTo(10);
        assertThat(message.getCategory()).isEqualTo("NEWS");
    }
}