// MostWatchedCategoryMessageTest.java
package com.example.service1.DTO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MostWatchedCategoryMessageTest {
    @Test void constructor_shouldSetFields() {
        MostWatchedCategoryMessage msg = new MostWatchedCategoryMessage(1, "Sports");
        assertThat(msg.getUserId()).isEqualTo(1);
        assertThat(msg.getCategory()).isEqualTo("Sports");
    }
    @Test void setters_shouldUpdateValues() {
        MostWatchedCategoryMessage msg = new MostWatchedCategoryMessage(1, "Music");
        msg.setUserId(99); msg.setCategory("Action");
        assertThat(msg.getUserId()).isEqualTo(99);
        assertThat(msg.getCategory()).isEqualTo("Action");
    }
}