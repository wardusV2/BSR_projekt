package com.example.service1.Controller;

import com.example.service1.Fault.FaultState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FaultControllerTest {

    private FaultState faultState;
    private FaultController controller;

    @BeforeEach
    void setUp() {
        faultState = mock(FaultState.class);
        controller = new FaultController(faultState);
    }

    @Test
    void inject_validFaultType_shouldReturnOk() {
        FaultController.FaultRequest req = new FaultController.FaultRequest("DELAY", 300, 0, null);
        ResponseEntity<String> response = controller.inject(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK: DELAY");
    }

    @Test
    void inject_unknownFaultType_shouldReturnBadRequest() {
        FaultController.FaultRequest req = new FaultController.FaultRequest("UNKNOWN_TYPE", null, null, null);
        ResponseEntity<String> response = controller.inject(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Nieznany faultType: UNKNOWN_TYPE");
    }

    @Test
    void inject_nullDelayMs_shouldDefaultToZero() {
        FaultController.FaultRequest req = new FaultController.FaultRequest("NONE", null, null, null);
        controller.inject(req);
        verify(faultState).set(argThat(config -> config.delayMs() == 0 && config.dropRate() == 0));
    }

    @Test
    void inject_nullDropRate_shouldDefaultToZero() {
        FaultController.FaultRequest req = new FaultController.FaultRequest("NONE", 100, null, null);
        controller.inject(req);
        verify(faultState).set(argThat(config -> config.dropRate() == 0));
    }

    @Test
    void inject_withByzantineCategory_shouldPassCategory() {
        FaultController.FaultRequest req = new FaultController.FaultRequest("BYZANTINE", 0, 0, "Sports");
        controller.inject(req);
        verify(faultState).set(argThat(config ->
                config.faultType() == FaultState.FaultType.BYZANTINE &&
                        "Sports".equals(config.byzantineCategory())
        ));
    }

    @Test
    void inject_offlineType_shouldCallSet() {
        FaultController.FaultRequest req = new FaultController.FaultRequest("OFFLINE", 0, 0, null);
        ResponseEntity<String> response = controller.inject(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(faultState).set(any(FaultState.FaultConfig.class));
    }

    @Test
    void status_shouldReturnCurrentFaultConfig() {
        FaultState.FaultConfig config = new FaultState.FaultConfig(FaultState.FaultType.DROP, 0, 50, null);
        when(faultState.get()).thenReturn(config);
        ResponseEntity<FaultState.FaultConfig> response = controller.status();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(config);
    }

    @Test
    void faultRequest_record_shouldStoreValues() {
        FaultController.FaultRequest req = new FaultController.FaultRequest("DROP", 200, 60, "Action");
        assertThat(req.faultType()).isEqualTo("DROP");
        assertThat(req.delayMs()).isEqualTo(200);
        assertThat(req.dropRate()).isEqualTo(60);
        assertThat(req.byzantineCategory()).isEqualTo("Action");
    }
}