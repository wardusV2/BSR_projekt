package com.example.service7.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscribedCategoryMessageTest {

    @Test
    void shouldCreateMessage() {

        SubscribedCategoryMessage dto =
                new SubscribedCategoryMessage(
                        5,
                        "MUSIC"
                );

        assertThat(dto.userId()).isEqualTo(5);
        assertThat(dto.category()).isEqualTo("MUSIC");
    }
}