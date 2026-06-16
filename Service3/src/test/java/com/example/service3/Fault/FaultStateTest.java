package com.example.service3.Fault;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaultStateTest {

    private FaultState faultState;

    @BeforeEach
    void setUp() {
        faultState = new FaultState();
    }

    // ── Domyślny stan ─────────────────────────────────────────────────────────

    @Test
    void initialState_shouldBeNone() {
        FaultState.FaultConfig config = faultState.get();

        assertThat(config.faultType()).isEqualTo(FaultState.FaultType.NONE);
    }

    @Test
    void initialState_delayMs_shouldBeZero() {
        assertThat(faultState.get().delayMs()).isEqualTo(0);
    }

    @Test
    void initialState_dropRate_shouldBeZero() {
        assertThat(faultState.get().dropRate()).isEqualTo(0);
    }

    @Test
    void initialState_byzantineCategory_shouldBeNull() {
        assertThat(faultState.get().byzantineCategory()).isNull();
    }

    // ── set / get ─────────────────────────────────────────────────────────────

    @Test
    void set_shouldUpdateFaultConfig() {
        FaultState.FaultConfig newConfig =
                new FaultState.FaultConfig(FaultState.FaultType.DELAY, 1000, 0, null);

        faultState.set(newConfig);

        assertThat(faultState.get()).isEqualTo(newConfig);
    }

    @Test
    void set_dropConfig_shouldPersist() {
        FaultState.FaultConfig dropConfig =
                new FaultState.FaultConfig(FaultState.FaultType.DROP, 0, 60, null);

        faultState.set(dropConfig);

        assertThat(faultState.get().faultType()).isEqualTo(FaultState.FaultType.DROP);
        assertThat(faultState.get().dropRate()).isEqualTo(60);
    }

    @Test
    void set_byzantineConfig_shouldPersistCategory() {
        FaultState.FaultConfig byzantineConfig =
                new FaultState.FaultConfig(FaultState.FaultType.BYZANTINE, 0, 0, "RANDOM");

        faultState.set(byzantineConfig);

        assertThat(faultState.get().faultType()).isEqualTo(FaultState.FaultType.BYZANTINE);
        assertThat(faultState.get().byzantineCategory()).isEqualTo("RANDOM");
    }

    @Test
    void set_offlineConfig_shouldPersist() {
        FaultState.FaultConfig offlineConfig =
                new FaultState.FaultConfig(FaultState.FaultType.OFFLINE, 0, 0, null);

        faultState.set(offlineConfig);

        assertThat(faultState.get().faultType()).isEqualTo(FaultState.FaultType.OFFLINE);
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    void reset_afterSettingDelay_shouldRestoreNoneState() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.DELAY, 500, 0, null));

        faultState.reset();

        assertThat(faultState.get().faultType()).isEqualTo(FaultState.FaultType.NONE);
    }

    @Test
    void reset_shouldResetDelayMsToZero() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.DELAY, 999, 0, null));

        faultState.reset();

        assertThat(faultState.get().delayMs()).isEqualTo(0);
    }

    @Test
    void reset_shouldResetDropRateToZero() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.DROP, 0, 80, null));

        faultState.reset();

        assertThat(faultState.get().dropRate()).isEqualTo(0);
    }

    @Test
    void reset_shouldResetByzantineCategoryToNull() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.BYZANTINE, 0, 0, "INVALID"));

        faultState.reset();

        assertThat(faultState.get().byzantineCategory()).isNull();
    }

    // ── FaultConfig.none() ────────────────────────────────────────────────────

    @Test
    void faultConfigNone_shouldHaveNoneType() {
        FaultState.FaultConfig none = FaultState.FaultConfig.none();

        assertThat(none.faultType()).isEqualTo(FaultState.FaultType.NONE);
    }

    @Test
    void faultConfigNone_shouldHaveZeroDelayAndDropRate() {
        FaultState.FaultConfig none = FaultState.FaultConfig.none();

        assertThat(none.delayMs()).isEqualTo(0);
        assertThat(none.dropRate()).isEqualTo(0);
    }

    // ── FaultType enum ────────────────────────────────────────────────────────

    @Test
    void faultType_shouldContainAllExpectedValues() {
        FaultState.FaultType[] types = FaultState.FaultType.values();

        assertThat(types).containsExactlyInAnyOrder(
                FaultState.FaultType.NONE,
                FaultState.FaultType.DELAY,
                FaultState.FaultType.DROP,
                FaultState.FaultType.BYZANTINE,
                FaultState.FaultType.OFFLINE
        );
    }

    // ── thread safety (atomiczność) ───────────────────────────────────────────

    @Test
    void set_concurrentUpdates_shouldNotThrow() throws InterruptedException {
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                faultState.set(new FaultState.FaultConfig(FaultState.FaultType.DELAY, i, 0, null));
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                faultState.reset();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // Po zakończeniu stan musi być spójny (nie rzuca wyjątku)
        assertThat(faultState.get()).isNotNull();
    }
}
