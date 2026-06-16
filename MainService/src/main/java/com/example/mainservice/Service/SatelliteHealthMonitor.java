package com.example.mainservice.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitoruje dostępność satelit (Service1–7) przez HTTP health-check.
 *
 * Działanie:
 * ──────────
 * Co CHECK_INTERVAL_MS (15 s) odpytuje GET {baseUrl}/actuator/health każdej satelity.
 * Jeśli satelita nie odpowiada przez ALERT_THRESHOLD_MS (45 s) wysyła alert:
 *   - log w MonitoringLogService
 *   - push WebSocket → /topic/satellite-health
 *
 * Konfiguracja portów (application.properties):
 * ─────────────────────────────────────────────
 * satellite.health.urls=Service1=http://localhost:8091,Service2=http://localhost:8092,...
 * satellite.health.check-interval-ms=15000   # opcjonalnie
 * satellite.health.alert-threshold-ms=45000  # opcjonalnie
 */
@Component
public class SatelliteHealthMonitor {

    private static final Logger log = LoggerFactory.getLogger(SatelliteHealthMonitor.class);

    // ── Konfiguracja (nadpisywalna w application.properties) ─────────────────

    /** Interwał sprawdzania zdrowia satelit [ms]. */
    @Value("${satellite.health.check-interval-ms:15000}")
    private long checkIntervalMs;

    /** Czas bez odpowiedzi po którym wysyłany jest alert [ms]. */
    @Value("${satellite.health.alert-threshold-ms:45000}")
    private long alertThresholdMs;

    /**
     * Adresy bazowe satelit w formacie: "Service1=http://localhost:8091,Service2=http://localhost:8092"
     * Endpointem health-checka jest {baseUrl}/actuator/health
     */
    @Value("${satellite.health.urls:Service1=http://localhost:8082,Service2=http://localhost:8083," +
            "Service3=http://localhost:8084,Service4=http://localhost:8085," +
            "Service5=http://localhost:8086,Service6=http://localhost:8087," +
            "Service7=http://localhost:8087}")
    private String satelliteUrlsRaw;

    // ── Zależności ────────────────────────────────────────────────────────────

    private final MonitoringLogService     logService;
    private final SimpMessagingTemplate    messagingTemplate;

    // ── Stan wewnętrzny ───────────────────────────────────────────────────────

    /** serviceName → ostatni udany health-check */
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    /** serviceName → czy alert był już wysłany (unikamy spamu) */
    private final Map<String, Boolean> alertSent = new ConcurrentHashMap<>();

    /** serviceName → aktualny status */
    private final Map<String, SatelliteStatus> statuses = new ConcurrentHashMap<>();

    /** Mapa: serviceName → baseUrl (parsowana leniwie przy pierwszym użyciu) */
    private volatile Map<String, String> satelliteUrls;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public SatelliteHealthMonitor(MonitoringLogService logService,
                                  SimpMessagingTemplate messagingTemplate) {
        this.logService        = logService;
        this.messagingTemplate = messagingTemplate;
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${satellite.health.check-interval-ms:15000}")
    public void checkAll() {
        Map<String, String> urls = getSatelliteUrls();
        if (urls.isEmpty()) {
            log.warn("HealthMonitor: brak skonfigurowanych URL satelit");
            return;
        }
        urls.forEach(this::checkOne);
    }

    // ── Sprawdzanie jednej satelity ───────────────────────────────────────────

    private void checkOne(String serviceName, String baseUrl) {
        String healthUrl = baseUrl.stripTrailing() + "/actuator/health";
        boolean reachable = ping(healthUrl);

        if (reachable) {
            handleRecovery(serviceName);
        } else {
            handleFailure(serviceName);
        }
    }

    private boolean ping(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.discarding());

            return response.statusCode() >= 200 && response.statusCode() < 400;

        } catch (Exception e) {
//            log.error("HealthMonitor ping failed {}", url, e);
            return false;
        }
    }

    // ── Logika stanów ─────────────────────────────────────────────────────────

    private void handleRecovery(String serviceName) {
        Instant now = Instant.now();
        boolean wasDown = statuses.get(serviceName) == SatelliteStatus.DOWN;

        lastSeen.put(serviceName, now);
        statuses.put(serviceName, SatelliteStatus.UP);

        if (wasDown) {
            // Satelita wróciła do życia po awarii
            alertSent.put(serviceName, false);
            log.info("HealthMonitor: {} ✅ WRÓCIŁA DO ŻYCIA", serviceName);

            Map<String, Object> event = buildEvent(serviceName, "RECOVERED",
                    "Satelita " + serviceName + " wróciła do działania.");
            logService.log(event);
            pushWebSocket(event);
        } else if (!statuses.containsKey(serviceName)) {
            // Pierwsze widzenie
            log.info("HealthMonitor: {} ✅ zarejestrowana ({})", serviceName,
                    getSatelliteUrls().get(serviceName));
        }
    }

    private void handleFailure(String serviceName) {
        // Jeśli nigdy nie widzieliśmy satelity — inicjalizuj stan
        statuses.putIfAbsent(serviceName, SatelliteStatus.UNKNOWN);
        alertSent.putIfAbsent(serviceName, false);

        Instant last = lastSeen.get(serviceName);
        long silenceMs = last != null
                ? Duration.between(last, Instant.now()).toMillis()
                : Long.MAX_VALUE;

        if (silenceMs >= alertThresholdMs) {
            statuses.put(serviceName, SatelliteStatus.DOWN);

            if (!Boolean.TRUE.equals(alertSent.get(serviceName))) {
                // Pierwszy alert dla tej awarii
                alertSent.put(serviceName, true);
                String msg = last != null
                        ? String.format("Satelita %s nie odpowiada od %d s (próg: %d s).",
                        serviceName, silenceMs / 1000, alertThresholdMs / 1000)
                        : String.format("Satelita %s nigdy nie odpowiedziała (próg: %d s).",
                        serviceName, alertThresholdMs / 1000);

                log.error("HealthMonitor: {} ❌ DOWN — {}", serviceName, msg);

                Map<String, Object> event = buildEvent(serviceName, "DOWN", msg);
                logService.log(event);
                pushWebSocket(event);

            } else {
                // Kolejne sprawdzenia — tylko debug, bez spamu
                log.debug("HealthMonitor: {} nadal DOWN ({}s ciszy)",
                        serviceName, silenceMs / 1000);
            }
        } else {
            // Poniżej progu — oznacz jako degraded, bez alertu
            statuses.put(serviceName, SatelliteStatus.DEGRADED);
            log.warn("HealthMonitor: {} ⚠️ DEGRADED — brak odpowiedzi od {}s / próg {}s",
                    serviceName, silenceMs / 1000, alertThresholdMs / 1000);
        }
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────

    private void pushWebSocket(Map<String, Object> event) {
        try {
            messagingTemplate.convertAndSend("/topic/satellite-health", (Object) event);
        } catch (Exception e) {
            log.error("HealthMonitor: błąd wysyłki WebSocket: {}", e.getMessage());
        }
    }

    // ── API publiczne (dla kontrolera) ────────────────────────────────────────

    /**
     * Zwraca aktualny snapshot stanu wszystkich satelit.
     * Używane przez SatelliteHealthController.
     */
    public Map<String, Object> getHealthSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        Map<String, Object> services = new LinkedHashMap<>();

        getSatelliteUrls().forEach((name, url) -> {
            SatelliteStatus status = statuses.getOrDefault(name, SatelliteStatus.UNKNOWN);
            Instant last = lastSeen.get(name);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("status",      status.name());
            info.put("url",         url);
            info.put("lastSeenMs",  last != null ? last.toEpochMilli() : null);
            info.put("silenceSec",  last != null
                    ? Duration.between(last, Instant.now()).toSeconds()
                    : null);
            info.put("alertSent",   Boolean.TRUE.equals(alertSent.get(name)));
            services.put(name, info);
        });

        long downCount = statuses.values().stream()
                .filter(s -> s == SatelliteStatus.DOWN).count();

        snapshot.put("services",         services);
        snapshot.put("totalServices",    getSatelliteUrls().size());
        snapshot.put("downCount",        downCount);
        snapshot.put("alertThresholdMs", alertThresholdMs);
        snapshot.put("checkIntervalMs",  checkIntervalMs);
        snapshot.put("serverTimeMs",     System.currentTimeMillis());
        return snapshot;
    }

    /** Ręczne wyzwolenie sprawdzenia wszystkich satelit (np. z endpointu). */
    public void triggerCheck() {
        log.info("HealthMonitor: ręczne wyzwolenie sprawdzenia satelit");
        checkAll();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildEvent(String serviceName, String type, String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type",        "SATELLITE_HEALTH");
        event.put("service",     "MainService");
        event.put("satellite",   serviceName);
        event.put("event",       type);         // DOWN | RECOVERED
        event.put("message",     message);
        event.put("timestamp",   System.currentTimeMillis());
        event.put("url",         getSatelliteUrls().get(serviceName));
        return event;
    }

    /**
     * Parsuje satelliteUrlsRaw przy pierwszym użyciu.
     * Format: "Service1=http://localhost:8091,Service2=http://localhost:8092"
     */
    private Map<String, String> getSatelliteUrls() {
        if (satelliteUrls == null) {
            synchronized (this) {
                if (satelliteUrls == null) {
                    Map<String, String> map = new LinkedHashMap<>();
                    for (String pair : satelliteUrlsRaw.split(",")) {
                        String[] kv = pair.trim().split("=", 2);
                        if (kv.length == 2) {
                            map.put(kv[0].trim(), kv[1].trim());
                        }
                    }
                    satelliteUrls = Collections.unmodifiableMap(map);
                    log.info("HealthMonitor: załadowano {} satelit: {}",
                            satelliteUrls.size(), satelliteUrls.keySet());
                }
            }
        }
        return satelliteUrls;
    }

    // ── Enum statusów ─────────────────────────────────────────────────────────

    public enum SatelliteStatus {
        UP,        // odpowiada poprawnie
        DEGRADED,  // nie odpowiada, ale poniżej progu alertu
        DOWN,      // nie odpowiada ≥ alertThresholdMs → alert wysłany
        UNKNOWN    // nigdy nie sprawdzana
    }
}