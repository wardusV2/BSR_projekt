package com.example.service1.Fault;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class FaultState {

    public enum FaultType { NONE, DELAY, DROP, BYZANTINE, OFFLINE }

    public record FaultConfig(
            FaultType faultType,
            int       delayMs,
            int       dropRate,          // 0–100
            String    byzantineCategory
    ) {
        public static FaultConfig none() {
            return new FaultConfig(FaultType.NONE, 0, 0, null);
        }
    }

    private final AtomicReference<FaultConfig> current =
            new AtomicReference<>(FaultConfig.none());

    public FaultConfig get()                    { return current.get(); }
    public void        set(FaultConfig config)  { current.set(config); }
    public void        reset()                  { current.set(FaultConfig.none()); }
}