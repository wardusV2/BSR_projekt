package com.example.service7.DTO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscribedUserDTOTest {

    @Test
    void shouldCreateSubscribedUserDto() {

        SubscribedUserDTO dto =
                new SubscribedUserDTO(
                        10,
                        "sub@test.pl",
                        "subscriber"
                );

        assertThat(dto.id()).isEqualTo(10);
        assertThat(dto.email()).isEqualTo("sub@test.pl");
        assertThat(dto.nick()).isEqualTo("subscriber");
    }
}