package com.example.service3.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDTOTest {

    @Test
    void userDTO_shouldStoreId() {
        UserDTO user = new UserDTO(1, "jankowalski");

        assertThat(user.id()).isEqualTo(1);
    }

    @Test
    void userDTO_shouldStoreNick() {
        UserDTO user = new UserDTO(1, "jankowalski");

        assertThat(user.nick()).isEqualTo("jankowalski");
    }

    @Test
    void userDTO_equalObjects_shouldBeEqual() {
        UserDTO a = new UserDTO(5, "alice");
        UserDTO b = new UserDTO(5, "alice");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void userDTO_differentId_shouldNotBeEqual() {
        UserDTO a = new UserDTO(1, "alice");
        UserDTO b = new UserDTO(2, "alice");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void userDTO_differentNick_shouldNotBeEqual() {
        UserDTO a = new UserDTO(1, "alice");
        UserDTO b = new UserDTO(1, "bob");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void userDTO_toString_shouldContainIdAndNick() {
        UserDTO user = new UserDTO(99, "testuser");

        assertThat(user.toString()).contains("99", "testuser");
    }
}
