package com.example.service1.Fault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FaultStateTest {

    private FaultState faultState;

    @BeforeEach
    void setUp() {
        faultState = new FaultState();
    }

    @Test
    void initialState_shouldBeNone() {
        FaultState.FaultConfig config = faultState.get();
        assertThat(config.faultType()).isEqualTo(FaultState.FaultType.NONE);
        assertThat(config.delayMs()).isZero();
        assertThat(config.dropRate()).isZero();
        assertThat(config.byzantineCategory()).isNull();
    }

    @Test
    void set_shouldUpdateCurrentConfig() {
        FaultState.FaultConfig newConfig = new FaultState.FaultConfig(
                FaultState.FaultType.DELAY, 500, 0, null);
        faultState.set(newConfig);
        assertThat(faultState.get().faultType()).isEqualTo(FaultState.FaultType.DELAY);
        assertThat(faultState.get().delayMs()).isEqualTo(500);
    }

    @Test
    void reset_shouldRestoreNoneState() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.OFFLINE, 0, 0, null));
        faultState.reset();
        assertThat(faultState.get().faultType()).isEqualTo(FaultState.FaultType.NONE);
    }

    @Test
    void faultConfigNone_shouldReturnNoneConfig() {
        FaultState.FaultConfig none = FaultState.FaultConfig.none();
        assertThat(none.faultType()).isEqualTo(FaultState.FaultType.NONE);
        assertThat(none.delayMs()).isZero();
        assertThat(none.dropRate()).isZero();
        assertThat(none.byzantineCategory()).isNull();
    }

    @Test
    void set_byzantineConfigWithCategory_shouldBePreserved() {
        FaultState.FaultConfig byzantine = new FaultState.FaultConfig(
                FaultState.FaultType.BYZANTINE, 0, 0, "Music");
        faultState.set(byzantine);
        assertThat(faultState.get().byzantineCategory()).isEqualTo("Music");
        assertThat(faultState.get().faultType()).isEqualTo(FaultState.FaultType.BYZANTINE);
    }

    @Test
    void set_dropConfigWithDropRate_shouldBePreserved() {
        FaultState.FaultConfig drop = new FaultState.FaultConfig(
                FaultState.FaultType.DROP, 0, 75, null);
        faultState.set(drop);
        assertThat(faultState.get().dropRate()).isEqualTo(75);
    }

    @Test
    void faultType_allValuesExist() {
        assertThat(FaultState.FaultType.values())
                .containsExactlyInAnyOrder(
                        FaultState.FaultType.NONE,
                        FaultState.FaultType.DELAY,
                        FaultState.FaultType.DROP,
                        FaultState.FaultType.BYZANTINE,
                        FaultState.FaultType.OFFLINE
                );
    }
}