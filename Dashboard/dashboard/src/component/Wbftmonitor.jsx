import { useState, useEffect, useRef, useCallback } from "react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from "recharts";
import "./Wbftmonitor.css";

const BASE = "http://localhost:8081";
const SVC_NAMES = [
  "Service1","Service2","Service3",
  "Service4","Service5","Service6","Service7",
];
const CAT_COLORS = [
  "#3266ad","#1D9E75","#D4537E",
  "#BA7517","#534AB7","#D85A30","#888780",
];

function ts(ms) {
  return new Date(ms).toTimeString().slice(0, 8);
}

function generateMockLogs() {
  const cats = ["ACTION","COMEDY","DRAMA","THRILLER","DOCUMENTARY"];
  const logs = [];
  const now = Date.now();
  for (let i = 0; i < 42; i++) {
    const svc = SVC_NAMES[i % SVC_NAMES.length];
    const cat = cats[Math.floor(Math.random() * cats.length)];
    const w = parseFloat((1 + Math.random() * 3).toFixed(1));
    logs.push({
      type: "RABBIT_IN",
      service: svc,
      routingKey: "vote." + svc,
      weight: w,
      content: { userId: 10 + (i % 5), category: cat },
      timestamp: now - (41 - i) * 8000,
    });
    if (i % 7 === 6) {
      const statuses = ["UNANIMOUS","CONSENSUS","NO_QUORUM"];
      const st = statuses[Math.floor(Math.random() * statuses.length)];
      logs.push({
        type: "WBFT",
        service: "MainService",
        content: {
          status: st,
          category: cat,
          userId: 10 + (i % 5),
          winnerRatio: (0.6 + Math.random() * 0.4).toFixed(2),
        },
        timestamp: now - (41 - i) * 8000 + 300,
      });
    }
  }
  return logs;
}

function MetricCard({ label, value, sub, danger }) {
  return (
    <div className="metric-card">
      <div className="metric-label">{label}</div>
      <div className={`metric-value${danger ? " metric-value--danger" : ""}`}>
        {value}
      </div>
      <div className="metric-sub">{sub}</div>
    </div>
  );
}

function ServicePill({ name, status, lastSeen }) {
  const short = name.replace("Service", "S");
  return (
    <div className={`svc-pill svc-pill--${status}`} title={`${name}${lastSeen ? " · " + ts(lastSeen) : " · nie widziano"}`}>
      {short}
    </div>
  );
}

function VoteBars({ logs }) {
  const rabbitLogs = logs.filter(
    (l) => l.type === "RABBIT_IN" && l.content?.category
  );
  if (!rabbitLogs.length) return (
    <div className="vote-bars-empty">Brak danych – oczekiwanie na głosy...</div>
  );

  const catCounts = {};
  rabbitLogs.slice(-35).forEach((l) => {
    const cat = l.content.category;
    catCounts[cat] = (catCounts[cat] || 0) + 1;
  });
  const total = Object.values(catCounts).reduce((a, b) => a + b, 0);
  const sorted = Object.entries(catCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6);

  return (
    <div className="vote-bars">
      {sorted.map(([cat, cnt], i) => {
        const pct = Math.round((cnt / total) * 100);
        return (
          <div className="vote-row" key={cat}>
            <div className="vote-row-label" title={cat}>{cat}</div>
            <div className="vote-bar-bg">
              <div
                className="vote-bar-fill"
                style={{ width: `${pct}%`, background: CAT_COLORS[i % CAT_COLORS.length] }}
              />
            </div>
            <div className="vote-pct">{pct}%</div>
          </div>
        );
      })}
    </div>
  );
}

function LastVerdict({ logs }) {
  const wbftLogs = logs.filter((l) => l.type === "WBFT" && l.content?.status);
  const last = [...wbftLogs].reverse()[0];
  if (!last) return <div className="last-verdict-empty">Oczekiwanie na dane...</div>;

  const { status, category, userId, winnerRatio } = last.content;
  const ratio = winnerRatio
    ? (parseFloat(winnerRatio) * 100).toFixed(1) + "%"
    : null;
  const cls =
    status === "UNANIMOUS"
      ? "badge--unanimous"
      : status === "CONSENSUS"
      ? "badge--consensus"
      : "badge--no-quorum";

  return (
    <div className="last-verdict">
      <span className="last-verdict-label">Ostatni wynik:</span>
      <span className={`consensus-badge ${cls}`}>{status}</span>
      <div className="last-verdict-detail">
        user={userId} → <strong>{category}</strong>
        {ratio ? ` · ${ratio} wagi` : ""} · {ts(last.timestamp)}
      </div>
    </div>
  );
}

function WbftChart({ logs }) {
  const wbftLogs = logs.filter((l) => l.type === "WBFT" && l.content?.status);
  const counts = { UNANIMOUS: 0, CONSENSUS: 0, NO_QUORUM: 0, NO_DATA: 0 };
  wbftLogs.forEach((l) => {
    if (counts[l.content.status] !== undefined) counts[l.content.status]++;
  });
  const data = [
    { name: "UNANIMOUS", value: counts.UNANIMOUS, color: "#1D9E75" },
    { name: "CONSENSUS", value: counts.CONSENSUS, color: "#3266ad" },
    { name: "NO_QUORUM", value: counts.NO_QUORUM, color: "#E24B4A" },
    { name: "NO_DATA",   value: counts.NO_DATA,   color: "#888780" },
  ];

  return (
    <ResponsiveContainer width="100%" height={180}>
      <BarChart data={data} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
        <XAxis dataKey="name" tick={{ fontSize: 10 }} />
        <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
        <Tooltip
          formatter={(v) => [v, "wyniki"]}
          contentStyle={{ fontSize: 12, borderRadius: 6 }}
        />
        <Bar dataKey="value" radius={[4, 4, 0, 0]}>
          {data.map((d) => (
            <Cell key={d.name} fill={d.color} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

function LogRow({ log }) {
  const typeClass =
    log.type === "RABBIT_IN" ? "log-type--rabbit"
    : log.type === "WBFT" ? "log-type--wbft"
    : log.type === "ERROR" ? "log-type--err"
    : "log-type--warn";

  let msg = "";
  if (log.type === "RABBIT_IN") {
    msg = `routingKey=${log.routingKey || ""} userId=${log.content?.userId ?? "?"} cat=${log.content?.category ?? "?"} w=${log.weight ?? "?"}`;
  } else if (log.type === "WBFT") {
    msg = `status=${log.content?.status ?? "?"} cat=${log.content?.category ?? "?"} user=${log.content?.userId ?? "?"}`;
  } else {
    msg = JSON.stringify(log.content || log).slice(0, 80);
  }

  return (
    <div className="log-row">
      <span className="log-time">{ts(log.timestamp || Date.now())}</span>
      <span className={`log-type ${typeClass}`}>{log.type || "?"}</span>
      <span className="log-svc">{log.service || "?"}</span>
      <span className="log-msg">{msg}</span>
    </div>
  );
}

export default function WbftMonitor() {
  const [logs, setLogs] = useState([]);
  const [connStatus, setConnStatus] = useState("connecting");
  const [filterType, setFilterType] = useState("");
  const [filterSvc, setFilterSvc] = useState("");
  const [svcStatus, setSvcStatus] = useState(
    Object.fromEntries(SVC_NAMES.map((s) => [s, { status: "idle", lastSeen: null }]))
  );

  const computeMetrics = useCallback(() => {
    const total = logs.filter((l) => l.type === "RABBIT_IN").length;
    const now = Date.now();
    const rate = logs.filter(
      (l) => l.type === "RABBIT_IN" && now - l.timestamp < 60000
    ).length;
    const dlx = logs.filter(
      (l) => l.type === "DLX" || l.type === "ERROR"
    ).length;
    return { total, rate, dlx };
  }, [logs]);

  const updateSvcStatus = useCallback((newLogs) => {
    const rabbitLogs = newLogs.filter((l) => l.type === "RABBIT_IN");
    const now = Date.now();
    const newStatus = {};
    SVC_NAMES.forEach((svc) => {
      const last = [...rabbitLogs].reverse().find((l) => l.service === svc);
      if (!last) {
        newStatus[svc] = { status: "idle", lastSeen: null };
      } else {
        const fresh = now - last.timestamp < 40000;
        newStatus[svc] = { status: fresh ? "ok" : "warn", lastSeen: last.timestamp };
      }
    });
    setSvcStatus(newStatus);
  }, []);

  const fetchAll = useCallback(async () => {
    try {
      const [stateRes, logsRes] = await Promise.all([
        fetch(BASE + "/monitor/state", { signal: AbortSignal.timeout(3000) }),
        fetch(BASE + "/monitor/logs",  { signal: AbortSignal.timeout(3000) }),
      ]);
      if (!stateRes.ok || !logsRes.ok) throw new Error("bad status");
      const newLogs = await logsRes.json();
      setLogs(newLogs);
      updateSvcStatus(newLogs);
      setConnStatus("online");
    } catch {
      setLogs((prev) => {
        if (prev.length === 0) {
          const mock = generateMockLogs();
          updateSvcStatus(mock);
          setConnStatus("demo");
          return mock;
        }
        setConnStatus("offline");
        return prev;
      });
    }
  }, [updateSvcStatus]);

  useEffect(() => {
    fetchAll();
    const interval = setInterval(fetchAll, 5000);
    return () => clearInterval(interval);
  }, [fetchAll]);

  const metrics = computeMetrics();

  const filteredLogs = logs
    .filter((l) => {
      if (filterType && l.type !== filterType) return false;
      if (filterSvc && !(l.service || "").toLowerCase().includes(filterSvc.toLowerCase())) return false;
      return true;
    })
    .slice(-60)
    .reverse();

  const connLabel =
    connStatus === "online" ? "Połączony"
    : connStatus === "demo" ? "Demo (offline)"
    : connStatus === "offline" ? "Offline"
    : "Łączenie...";

  const connCls =
    connStatus === "online" ? "badge badge--live"
    : connStatus === "offline" ? "badge badge--error"
    : "badge badge--warn";

  return (
    <div className="monitor">
      <div className="top-bar">
        <h1 className="top-bar__title">
          <span className="dot-blink" aria-hidden="true" />
          WBFT System Monitor
        </h1>
        <div className="top-bar__actions">
          <span className={connCls}>{connLabel}</span>
          <button onClick={fetchAll} className="btn">
            ↺ Odśwież
          </button>
        </div>
      </div>

      <div className="metrics-grid">
        <MetricCard label="Wiadomości łącznie" value={metrics.total || "—"} sub="od startu monitorowania" />
        <MetricCard label="Aktywne rundy"       value={"—"}                  sub="oczekujące głosy" />
        <MetricCard label="Głosy / min"         value={metrics.rate}          sub="ostatnie 60 s" />
        <MetricCard label="Dead letter"         value={metrics.dlx}           sub="odrzucone wiad." danger={metrics.dlx > 0} />
      </div>

      <div className="grid2">
        <div className="card">
          <div className="section-title">Serwisy satelitarne</div>
          <div className="services-grid">
            {SVC_NAMES.map((svc) => (
              <ServicePill
                key={svc}
                name={svc}
                status={svcStatus[svc]?.status || "idle"}
                lastSeen={svcStatus[svc]?.lastSeen}
              />
            ))}
          </div>
          <div style={{ marginTop: 14 }}>
            <div className="section-title">Rozkład głosów (ostatnia runda)</div>
            <VoteBars logs={logs} />
          </div>
        </div>

        <div className="card">
          <div className="section-title">Wyniki WBFT</div>
          <WbftChart logs={logs} />
          <LastVerdict logs={logs} />
        </div>
      </div>

      <div className="card">
        <div className="log-header">
          <div className="section-title" style={{ marginBottom: 0 }}>
            Logi komunikacji
          </div>
          <div className="log-controls">
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value)}
              className="log-select"
            >
              <option value="">Wszystkie</option>
              <option value="RABBIT_IN">RABBIT_IN</option>
              <option value="WBFT">WBFT</option>
              <option value="ERROR">ERROR</option>
            </select>
            <input
              type="text"
              placeholder="Serwis..."
              value={filterSvc}
              onChange={(e) => setFilterSvc(e.target.value)}
              className="log-input"
            />
            <button
              onClick={() => {
                setLogs([]);
                setConnStatus("connecting");
              }}
              className="btn"
            >
              Wyczyść
            </button>
          </div>
        </div>

        <div className="log-list">
          {filteredLogs.length === 0 ? (
            <div className="log-empty">Brak pasujących logów.</div>
          ) : (
            filteredLogs.map((log, i) => <LogRow key={i} log={log} />)
          )}
        </div>
      </div>

      <div className="footer-hint">
        Dane z <code>http://localhost:8081/monitor/logs</code> i{" "}
        <code>/monitor/state</code> · odświeżanie co 5 s
      </div>
    </div>
  );
}