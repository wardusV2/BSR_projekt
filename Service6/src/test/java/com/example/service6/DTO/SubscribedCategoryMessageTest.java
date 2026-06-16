package com.example.service6.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscribedCategoryMessageTest {

    @Test
    void shouldCreateMessage() {

        SubscribedCategoryMessage dto =
                new SubscribedCategoryMessage(
                        10,
                        "MUSIC"
                );

        assertThat(dto.userId()).isEqualTo(10);
        assertThat(dto.category()).isEqualTo("MUSIC");
    }
}