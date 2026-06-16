package com.example.service2.Fault;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaultStateTest {

    @Test
    void shouldHaveNoneAsDefault() {

        FaultState state = new FaultState();

        assertThat(state.get().faultType())
                .isEqualTo(FaultState.FaultType.NONE);
    }

    @Test
    void shouldUpdateFaultState() {

        FaultState state = new FaultState();

        FaultState.FaultConfig config =
                new FaultState.FaultConfig(
                        FaultState.FaultType.DELAY,
                        1000,
                        0,
                        null
                );

        state.set(config);

        assertThat(state.get()).isEqualTo(config);
    }

    @Test
    void shouldResetFaultState() {

        FaultState state = new FaultState();

        state.set(new FaultState.FaultConfig(
                FaultState.FaultType.OFFLINE,
                0,
                0,
                null));

        state.reset();

        assertThat(state.get().faultType())
                .isEqualTo(FaultState.FaultType.NONE);
    }
}