// UserDTOTest.java
package com.example.service1.DTO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UserDTOTest {
    @Test void record_shouldStoreIdAndNick() {
        UserDTO user = new UserDTO(5, "alice");
        assertThat(user.id()).isEqualTo(5);
        assertThat(user.nick()).isEqualTo("alice");
    }
    @Test void record_equalObjects_shouldBeEqual() {
        assertThat(new UserDTO(1, "bob")).isEqualTo(new UserDTO(1, "bob"));
    }
    @Test void record_differentObjects_shouldNotBeEqual() {
        assertThat(new UserDTO(1, "bob")).isNotEqualTo(new UserDTO(2, "bob"));
    }
}