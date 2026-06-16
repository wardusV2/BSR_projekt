package com.example.service2.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDTOTest {

    @Test
    void shouldCreateUserDto() {

        UserDTO dto =
                new UserDTO(1, "test@test.pl", "john");

        assertThat(dto.id()).isEqualTo(1);
        assertThat(dto.email()).isEqualTo("test@test.pl");
        assertThat(dto.nick()).isEqualTo("john");
    }
}