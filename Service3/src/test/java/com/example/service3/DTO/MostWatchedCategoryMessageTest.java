package com.example.service3.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MostWatchedCategoryMessageTest {

    // Odpowiada SubscribedCategoryMessage – nazwa zachowana zgodnie ze strukturą Service1

    @Test
    void subscribedCategoryMessage_shouldStoreUserId() {
        SubscribedCategoryMessage msg = new SubscribedCategoryMessage(42, "SPORT");

        assertThat(msg.userId()).isEqualTo(42);
    }

    @Test
    void subscribedCategoryMessage_shouldStoreCategory() {
        SubscribedCategoryMessage msg = new SubscribedCategoryMessage(1, "MUSIC");

        assertThat(msg.category()).isEqualTo("MUSIC");
    }

    @Test
    void subscribedCategoryMessage_equalObjects_shouldBeEqual() {
        SubscribedCategoryMessage a = new SubscribedCategoryMessage(7, "NEWS");
        SubscribedCategoryMessage b = new SubscribedCategoryMessage(7, "NEWS");

        assertThat(a).isEqualTo(b);
    }

    @Test
    void subscribedCategoryMessage_differentObjects_shouldNotBeEqual() {
        SubscribedCategoryMessage a = new SubscribedCategoryMessage(1, "SPORT");
        SubscribedCategoryMessage b = new SubscribedCategoryMessage(2, "MUSIC");

        assertThat(a).isNotEqualTo(b);
    }
}
