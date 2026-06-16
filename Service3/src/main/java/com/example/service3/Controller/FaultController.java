package com.example.service3.Controller;

import com.example.service3.Fault.FaultState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fault")
@CrossOrigin   // pozwala frontendowi na localhost:3000 wywoływać endpoint
public class FaultController {

    private final FaultState faultState;

    public FaultController(FaultState faultState) {
        this.faultState = faultState;
    }

    @PostMapping("/inject")
    public ResponseEntity<String> inject(@RequestBody FaultRequest req) {
        FaultState.FaultType type;
        try {
            type = FaultState.FaultType.valueOf(req.faultType());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Nieznany faultType: " + req.faultType());
        }

        faultState.set(new FaultState.FaultConfig(
                type,
                req.delayMs() != null      ? req.delayMs()      : 0,
                req.dropRate() != null     ? req.dropRate()     : 0,
                req.byzantineCategory()
        ));

        return ResponseEntity.ok("OK: " + type);
    }

    @GetMapping("/status")
    public ResponseEntity<FaultState.FaultConfig> status() {
        return ResponseEntity.ok(faultState.get());
    }

    public record FaultRequest(
            String  faultType,
            Integer delayMs,
            Integer dropRate,
            String  byzantineCategory
    ) {}
}