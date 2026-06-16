package com.example.service2.Controller;

import com.example.service2.Fault.FaultState;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FaultControllerTest {

    private final FaultState faultState = mock(FaultState.class);
    private final FaultController controller =
            new FaultController(faultState);

    @Test
    void shouldInjectDelayFault() {

        FaultController.FaultRequest request =
                new FaultController.FaultRequest(
                        "DELAY",
                        500,
                        null,
                        null
                );

        ResponseEntity<String> response =
                controller.inject(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        verify(faultState).set(any(FaultState.FaultConfig.class));
    }

    @Test
    void shouldReturnBadRequestForUnknownFault() {

        FaultController.FaultRequest request =
                new FaultController.FaultRequest(
                        "UNKNOWN",
                        null,
                        null,
                        null
                );

        ResponseEntity<String> response =
                controller.inject(request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void shouldReturnStatus() {

        FaultState.FaultConfig config =
                FaultState.FaultConfig.none();

        when(faultState.get()).thenReturn(config);

        ResponseEntity<FaultState.FaultConfig> response =
                controller.status();

        assertThat(response.getBody()).isEqualTo(config);
    }
}