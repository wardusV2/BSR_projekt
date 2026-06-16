import { useState, useEffect, useCallback } from "react";
import "./FaultInjectionpanel.css";


const SERVICES = [
  { name: "Service1", port: 8082 },
  { name: "Service2", port: 8083 },
  { name: "Service3", port: 8084 },
];

const FAULT_TYPES = [
  { id: "NONE",      label: "Brak błędu",       badgeClass: "fi-badge--none",  badgeText: "NONE"   },
  { id: "DELAY",     label: "Opóźnienie",        badgeClass: "fi-badge--delay", badgeText: "DELAY"  },
  { id: "DROP",      label: "Gubienie pakietów", badgeClass: "fi-badge--drop",  badgeText: "DROP"   },
  { id: "BYZANTINE", label: "Byzantine vote",    badgeClass: "fi-badge--byz",   badgeText: "BFAULT" },
  { id: "OFFLINE",   label: "Wyłącz serwis",     badgeClass: "fi-badge--off",   badgeText: "OFF"    },
];

const PRESETS = [
  { label: "Byzantine S1+S2",           faultType: "BYZANTINE", byzantineCategory: "ACTION", services: [0, 1] },
  { label: "Opóźnienie 3s (wszystkie)", faultType: "DELAY",     delayMs: 3000,               services: "all"  },
  { label: "Drop 80% (S3)",             faultType: "DROP",      dropRate: 80,                services: [2]    },
  { label: "Reset wszystkich",          faultType: "NONE",                                   services: "all"  },
];

const CATS = ["ACTION", "COMEDY", "DRAMA", "THRILLER", "DOCUMENTARY"];

function ts() {
  return new Date().toTimeString().slice(0, 8);
}

/* ── Status dot ── */
function StatusDot({ status }) {
  const cls = status === "up" ? "fi-dot fi-dot--up"
    : status === "down"       ? "fi-dot fi-dot--down"
    : "fi-dot fi-dot--unknown";
  return <span className={cls} />;
}

/* ── Fault badge ── */
function FaultBadge({ badgeClass, text }) {
  return <span className={`fi-badge ${badgeClass}`}>{text}</span>;
}

/* ── Service card ── */
function ServiceCard({ svc, index, selected, healthStatus, activeFault, onToggle }) {
  const ft = activeFault
    ? FAULT_TYPES.find(x => x.id === activeFault.faultType)
    : null;

  return (
    <div
      className={`fi-svc-card${selected ? " fi-svc-card--selected" : ""}`}
      onClick={() => onToggle(index)}
    >
      <div className="fi-svc-header">
        <span className="fi-svc-name">{svc.name.replace("Service", "S")}</span>
        <StatusDot status={healthStatus} />
      </div>
      <div className="fi-svc-port">:{svc.port}</div>
      <div className="fi-svc-fault-row">
        {ft
          ? <FaultBadge badgeClass={ft.badgeClass} text={ft.badgeText} />
          : <FaultBadge badgeClass="fi-badge--none"  text="OK" />
        }
        {activeFault?.delayMs  && <span className="fi-svc-fault-extra">{activeFault.delayMs}ms</span>}
        {activeFault?.dropRate && <span className="fi-svc-fault-extra">{activeFault.dropRate}%</span>}
      </div>
    </div>
  );
}

/* ── Params area ── */
function ParamsArea({ faultType, delayMs, dropRate, byzantineCategory, onChange }) {
  if (faultType === "DELAY") return (
    <div>
      <div className="fi-param-row">
        <span className="fi-param-label">Opóźnienie</span>
        <input
          type="range" min={100} max={10000} step={100} value={delayMs}
          onChange={e => onChange({ delayMs: parseInt(e.target.value) })}
        />
        <span className="fi-param-val">{delayMs}ms</span>
      </div>
      <p className="fi-param-hint">Dodane do każdej wiadomości wysyłanej przez satelitę (Thread.sleep w pętli)</p>
    </div>
  );

  if (faultType === "DROP") return (
    <div>
      <div className="fi-param-row">
        <span className="fi-param-label">Prawdopodobieństwo guby</span>
        <input
          type="range" min={0} max={100} step={5} value={dropRate}
          onChange={e => onChange({ dropRate: parseInt(e.target.value) })}
        />
        <span className="fi-param-val">{dropRate}%</span>
      </div>
      <p className="fi-param-hint">Satelita pomija wysyłkę głosu z tym prawdopodobieństwem</p>
    </div>
  );

  if (faultType === "BYZANTINE") return (
    <div>
      <div className="fi-param-row">
        <span className="fi-param-label">Fałszywa kategoria</span>
        <select value={byzantineCategory} onChange={e => onChange({ byzantineCategory: e.target.value })}>
          {CATS.map(c => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>
      <p className="fi-param-hint">Satelita zawsze głosuje na tę kategorię niezależnie od danych</p>
    </div>
  );

  if (faultType === "OFFLINE") return (
    <p className="fi-param-hint">
      Satelita przestanie wysyłać wiadomości (pętla zostanie wstrzymana). Health endpoint zgłosi <strong>DOWN</strong>.
    </p>
  );

  return (
    <p className="fi-param-hint">Usuwa wszystkie aktywne błędy. Serwis wraca do normalnej pracy.</p>
  );
}

/* ── Log list ── */
function LogList({ logs }) {
  return (
    <div className="fi-log-area">
      {logs.length === 0
        ? <span className="fi-log-empty">Brak operacji.</span>
        : logs.map((l, i) => (
            <div key={i} className="fi-log-row">
              <span className="fi-log-time">{l.time}</span>
              <span className={`fi-log--${l.cls}`}>{l.msg}</span>
            </div>
          ))
      }
    </div>
  );
}

/* ── Main component ── */
export default function FaultInjectionPanel() {
  const [selected,          setSelected]          = useState(new Set());
  const [faultType,         setFaultType]         = useState("DELAY");
  const [delayMs,           setDelayMs]           = useState(1000);
  const [dropRate,          setDropRate]          = useState(50);
  const [byzantineCategory, setByzantineCategory] = useState("ACTION");
  const [svcFaults,         setSvcFaults]         = useState({});
  const [healthStatus,      setHealthStatus]      = useState({});
  const [logs,              setLogs]              = useState([]);

  const pingAll = useCallback(() => {
    SERVICES.forEach(svc => {
      fetch(`http://localhost:${svc.port}/actuator/health`, {
        signal: AbortSignal.timeout(1500),
      })
        .then(r => setHealthStatus(prev => ({ ...prev, [svc.name]: r.ok ? "up" : "down" })))
        .catch(()  => setHealthStatus(prev => ({ ...prev, [svc.name]: "down" })));
    });
  }, []);

  useEffect(() => {
    pingAll();
    const id = setInterval(pingAll, 8000);
    return () => clearInterval(id);
  }, [pingAll]);

  const addLog = (msg, cls = "info") => {
    setLogs(prev => [{ time: ts(), msg, cls }, ...prev].slice(0, 40));
  };

  const toggleSvc = (index) => {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(index) ? next.delete(index) : next.add(index);
      return next;
    });
  };

  const toggleAll = () => {
    setSelected(prev =>
      prev.size === SERVICES.length ? new Set() : new Set(SERVICES.map((_, i) => i))
    );
  };

  const sendToOne = async (svc, payload) => {
    try {
      const r = await fetch(`http://localhost:${svc.port}/fault/inject`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(3000),
      });
      if (r.ok) {
        setSvcFaults(prev => ({ ...prev, [svc.name]: payload }));
        addLog(`${svc.name} → ${payload.faultType} OK`, "ok");
      } else {
        addLog(`${svc.name} → HTTP ${r.status}`, "err");
      }
    } catch {
      addLog(`${svc.name} → brak połączenia`, "err");
    }
  };

  const sendAll = async () => {
    const payload = { faultType };
    if (faultType === "DELAY")     payload.delayMs           = delayMs;
    if (faultType === "DROP")      payload.dropRate           = dropRate;
    if (faultType === "BYZANTINE") payload.byzantineCategory  = byzantineCategory;

    const targets = [...selected].map(i => SERVICES[i]);
    addLog(`Wysyłam ${faultType} → ${targets.map(s => s.name.replace("Service", "S")).join(", ")}`);
    await Promise.all(targets.map(svc => sendToOne(svc, payload)));
  };

  const resetAll = async () => {
    addLog("Reset wszystkich serwisów…");
    await Promise.all(SERVICES.map(svc => sendToOne(svc, { faultType: "NONE" })));
    setSvcFaults({});
  };

  const applyPreset = (p) => {
    setFaultType(p.faultType);
    if (p.delayMs           != null) setDelayMs(p.delayMs);
    if (p.dropRate          != null) setDropRate(p.dropRate);
    if (p.byzantineCategory != null) setByzantineCategory(p.byzantineCategory);
    if (p.services === "all") setSelected(new Set(SERVICES.map((_, i) => i)));
    else                      setSelected(new Set(p.services));
  };

  const selLabel = selected.size === 0
    ? "brak wybranych"
    : selected.size === SERVICES.length
    ? "wszystkie serwisy"
    : [...selected].map(i => SERVICES[i].name.replace("Service", "S")).join(", ");

  return (
    <div>
      <p className="fi-desc">
        Wybierz serwisy, skonfiguruj typ błędu i wyślij polecenie na{" "}
        <code>POST /fault/inject</code>.
      </p>

      {/* Service grid */}
      <div className="fi-svc-grid">
        {SERVICES.map((svc, i) => (
          <ServiceCard
            key={svc.name}
            svc={svc}
            index={i}
            selected={selected.has(i)}
            healthStatus={healthStatus[svc.name] || "unknown"}
            activeFault={svcFaults[svc.name] || null}
            onToggle={toggleSvc}
          />
        ))}
      </div>

      {/* Config panel */}
      <div className="fi-config-panel">
        <div className="fi-config-header">
          <span className="fi-section-label">Typ błędu</span>
          <span className="fi-sel-info">{selLabel}</span>
        </div>

        <p className="fi-presets-label">Gotowe scenariusze:</p>
        <div className="fi-presets-row">
          {PRESETS.map((p, i) => (
            <button key={i} className="fi-preset-btn" onClick={() => applyPreset(p)}>
              {p.label}
            </button>
          ))}
        </div>

        <div className="fi-fault-tabs">
          {FAULT_TYPES.map(ft => (
            <button
              key={ft.id}
              className={`fi-fault-tab${faultType === ft.id ? " fi-fault-tab--active" : ""}`}
              onClick={() => setFaultType(ft.id)}
            >
              {ft.label}
            </button>
          ))}
        </div>

        <ParamsArea
          faultType={faultType}
          delayMs={delayMs}
          dropRate={dropRate}
          byzantineCategory={byzantineCategory}
          onChange={({ delayMs: d, dropRate: r, byzantineCategory: b }) => {
            if (d != null) setDelayMs(d);
            if (r != null) setDropRate(r);
            if (b != null) setByzantineCategory(b);
          }}
        />

        <div className="fi-action-bar">
          <button
            className="fi-btn-send"
            onClick={sendAll}
            disabled={selected.size === 0}
          >
            Wyślij błąd
          </button>
          <button className="fi-btn-reset" onClick={resetAll}>
            Resetuj serwisy
          </button>
          <button className="fi-select-all" onClick={toggleAll}>
            {selected.size === SERVICES.length ? "Odznacz wszystkie" : "Zaznacz wszystkie"}
          </button>
        </div>
      </div>

      <p className="fi-log-label">Log operacji</p>
      <LogList logs={logs} />
    </div>
  );
}