package com.example.service3.Controller;

import com.example.service3.Fault.FaultState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaultControllerTest {

    @Mock
    private FaultState faultState;

    @InjectMocks
    private FaultController faultController;

    // ── inject ────────────────────────────────────────────────────────────────

    @Test
    void inject_noneType_shouldSetFaultConfigAndReturnOk() {
        FaultController.FaultRequest request =
                new FaultController.FaultRequest("NONE", null, null, null);

        ResponseEntity<String> response = faultController.inject(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK: NONE");
    }

    @Test
    void inject_delayType_shouldSetDelayConfigCorrectly() {
        FaultController.FaultRequest request =
                new FaultController.FaultRequest("DELAY", 500, null, null);

        faultController.inject(request);

        ArgumentCaptor<FaultState.FaultConfig> captor =
                ArgumentCaptor.forClass(FaultState.FaultConfig.class);
        verify(faultState).set(captor.capture());

        FaultState.FaultConfig captured = captor.getValue();
        assertThat(captured.faultType()).isEqualTo(FaultState.FaultType.DELAY);
        assertThat(captured.delayMs()).isEqualTo(500);
    }

    @Test
    void inject_dropType_shouldSetDropRateCorrectly() {
        FaultController.FaultRequest request =
                new FaultController.FaultRequest("DROP", null, 75, null);

        faultController.inject(request);

        ArgumentCaptor<FaultState.FaultConfig> captor =
                ArgumentCaptor.forClass(FaultState.FaultConfig.class);
        verify(faultState).set(captor.capture());

        FaultState.FaultConfig captured = captor.getValue();
        assertThat(captured.faultType()).isEqualTo(FaultState.FaultType.DROP);
        assertThat(captured.dropRate()).isEqualTo(75);
    }

    @Test
    void inject_byzantineType_shouldSetByzantineCategoryCorrectly() {
        FaultController.FaultRequest request =
                new FaultController.FaultRequest("BYZANTINE", null, null, "RANDOM");

        faultController.inject(request);

        ArgumentCaptor<FaultState.FaultConfig> captor =
                ArgumentCaptor.forClass(FaultState.FaultConfig.class);
        verify(faultState).set(captor.capture());

        FaultState.FaultConfig captured = captor.getValue();
        assertThat(captured.faultType()).isEqualTo(FaultState.FaultType.BYZANTINE);
        assertThat(captured.byzantineCategory()).isEqualTo("RANDOM");
    }

    @Test
    void inject_nullDelayAndDropRate_shouldDefaultToZero() {
        FaultController.FaultRequest request =
                new FaultController.FaultRequest("NONE", null, null, null);

        faultController.inject(request);

        ArgumentCaptor<FaultState.FaultConfig> captor =
                ArgumentCaptor.forClass(FaultState.FaultConfig.class);
        verify(faultState).set(captor.capture());

        FaultState.FaultConfig captured = captor.getValue();
        assertThat(captured.delayMs()).isEqualTo(0);
        assertThat(captured.dropRate()).isEqualTo(0);
    }

    @Test
    void inject_unknownFaultType_shouldReturnBadRequest() {
        FaultController.FaultRequest request =
                new FaultController.FaultRequest("UNKNOWN_TYPE", null, null, null);

        ResponseEntity<String> response = faultController.inject(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("UNKNOWN_TYPE");
        verify(faultState, never()).set(any());
    }

    @Test
    void inject_offlineType_shouldReturnOk() {
        FaultController.FaultRequest request =
                new FaultController.FaultRequest("OFFLINE", null, null, null);

        ResponseEntity<String> response = faultController.inject(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK: OFFLINE");
    }

    // ── status ────────────────────────────────────────────────────────────────

    @Test
    void status_shouldReturnCurrentFaultConfig() {
        FaultState.FaultConfig expectedConfig =
                new FaultState.FaultConfig(FaultState.FaultType.DELAY, 300, 0, null);
        when(faultState.get()).thenReturn(expectedConfig);

        ResponseEntity<FaultState.FaultConfig> response = faultController.status();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedConfig);
    }

    @Test
    void status_shouldReturnNoneConfigByDefault() {
        FaultState.FaultConfig noneConfig = FaultState.FaultConfig.none();
        when(faultState.get()).thenReturn(noneConfig);

        ResponseEntity<FaultState.FaultConfig> response = faultController.status();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().faultType()).isEqualTo(FaultState.FaultType.NONE);
    }

    // ── FaultRequest record ────────────────────────────────────────────────────

    @Test
    void faultRequest_shouldStoreAllFields() {
        FaultController.FaultRequest request =
                new FaultController.FaultRequest("DROP", 100, 50, "RANDOM");

        assertThat(request.faultType()).isEqualTo("DROP");
        assertThat(request.delayMs()).isEqualTo(100);
        assertThat(request.dropRate()).isEqualTo(50);
        assertThat(request.byzantineCategory()).isEqualTo("RANDOM");
    }
}
