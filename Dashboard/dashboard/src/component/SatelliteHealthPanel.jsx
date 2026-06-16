import "./SatelliteHealthPanel.css";

function SatelliteHealthPanel({ health }) {
  if (!health) {
    return (
      <div className="empty-hint">
        Brak danych health monitor.
      </div>
    );
  }

  const services = Object.entries(health.services || {});

  return (
    <div className="sat-health-panel">

      <div className="health-summary">
        <MetricCard
          label="Satelity"
          value={health.totalServices}
          sub="łącznie"
        />

        <MetricCard
          label="DOWN"
          value={health.downCount}
          sub="niedostępne"
          danger={health.downCount > 0}
        />

        <MetricCard
          label="Próg alarmu"
          value={`${health.alertThresholdMs / 1000}s`}
          sub="heartbeat timeout"
        />
      </div>

      <div className="health-grid">
        {services.map(([name, svc]) => (
          <div
            key={name}
            className={`health-card health-card--${svc.status?.toLowerCase()}`}
          >
            <div className="health-card-header">
              <strong>{name}</strong>

              <span
                className={`status-badge ${
                  svc.status === "UP"
                    ? "badge--unanimous"
                    : "badge--noquorum"
                }`}
              >
                {svc.status}
              </span>
            </div>

            <div className="health-info">
              <div>URL: {svc.url}</div>
              <div>Silence: {svc.silenceSec}s</div>
              <div>
                Alert:
                {svc.alertSent ? " TAK" : " NIE"}
              </div>
              <div>
                Last Seen:
                {svc.lastSeen
                  ? ts(svc.lastSeen)
                  : " brak"}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}