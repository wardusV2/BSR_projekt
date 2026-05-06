import { useState, useEffect, useCallback } from "react";
import {
  BarChart, Bar, XAxis, YAxis, Tooltip,
  ResponsiveContainer, Cell,
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

// Wyniki WBFT z backendu (/monitor/wbft) mają strukturę:
// { userId, category, status, winnerRatio, totalWeight,
//   weightSums:{cat->weight}, byzantineSuspects:[svc,...],
//   voterCount, confident, timestamp }

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
    const w   = parseFloat((1 + Math.random() * 3).toFixed(1));
    logs.push({
      type: "RABBIT_IN",
      service: svc,
      routingKey: "vote." + svc,
      weight: w,
      content: { userId: 10 + (i % 5), category: cat },
      timestamp: now - (41 - i) * 8000,
    });
  }
  return logs;
}

function generateMockWbft() {
  const cats     = ["ACTION","COMEDY","DRAMA","THRILLER","DOCUMENTARY"];
  const statuses = ["UNANIMOUS","CONSENSUS","NO_QUORUM"];
  const results  = [];
  const now      = Date.now();
  for (let i = 0; i < 12; i++) {
    const cat   = cats[Math.floor(Math.random() * cats.length)];
    const st    = statuses[Math.floor(Math.random() * statuses.length)];
    const total = parseFloat((10 + Math.random() * 8).toFixed(1));
    const winW  = parseFloat((total * (0.55 + Math.random() * 0.4)).toFixed(1));
    const alt   = cats.find((c) => c !== cat) || "OTHER";
    const weightSums = { [cat]: winW };
    if (st !== "UNANIMOUS") weightSums[alt] = parseFloat((total - winW).toFixed(1));
    results.push({
      userId:            10 + (i % 5),
      category:          st === "NO_QUORUM" ? "OTHER" : cat,
      status:            st,
      winnerRatio:       parseFloat((winW / total).toFixed(3)),
      totalWeight:       total,
      weightSums,
      byzantineSuspects: st === "NO_QUORUM"
        ? [SVC_NAMES[Math.floor(Math.random() * SVC_NAMES.length)]]
        : [],
      voterCount: 7,
      confident:  st !== "NO_QUORUM",
      timestamp:  now - (11 - i) * 35000,
    });
  }
  return results;
}

function MetricCard({ label, value, sub, danger }) {
  return (
    <div className="metric-card">
      <div className="metric-label">{label}</div>
      <div className={`metric-value${danger ? " metric-value--danger" : ""}`}>{value}</div>
      <div className="metric-sub">{sub}</div>
    </div>
  );
}

function ServicePill({ name, status, lastSeen }) {
  const short = name.replace("Service", "S");
  return (
    <div
      className={`svc-pill svc-pill--${status}`}
      title={`${name}${lastSeen ? " · " + ts(lastSeen) : " · nie widziano"}`}
    >
      {short}
    </div>
  );
}

function VoteBars({ logs }) {
  const rabbitLogs = logs.filter((l) => l.type === "RABBIT_IN" && l.content?.category);
  if (!rabbitLogs.length)
    return <div className="vote-bars-empty">Brak danych – oczekiwanie na głosy...</div>;

  const catCounts = {};
  rabbitLogs.slice(-35).forEach((l) => {
    catCounts[l.content.category] = (catCounts[l.content.category] || 0) + 1;
  });
  const total  = Object.values(catCounts).reduce((a, b) => a + b, 0);
  const sorted = Object.entries(catCounts).sort((a, b) => b[1] - a[1]).slice(0, 6);

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

function WbftChart({ wbftResults }) {
  const counts = { UNANIMOUS: 0, CONSENSUS: 0, NO_QUORUM: 0, NO_DATA: 0 };
  wbftResults.forEach((r) => {
    if (counts[r.status] !== undefined) counts[r.status]++;
  });
  const data = [
    { name: "UNANIMOUS", value: counts.UNANIMOUS, color: "#1D9E75" },
    { name: "CONSENSUS",  value: counts.CONSENSUS,  color: "#3266ad" },
    { name: "NO_QUORUM",  value: counts.NO_QUORUM,  color: "#E24B4A" },
    { name: "NO_DATA",    value: counts.NO_DATA,    color: "#888780" },
  ];

  return (
    <ResponsiveContainer width="100%" height={155}>
      <BarChart data={data} margin={{ top: 4, right: 4, left: -20, bottom: 0 }}>
        <XAxis dataKey="name" tick={{ fontSize: 10 }} />
        <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
        <Tooltip
          formatter={(v) => [v, "wyniki"]}
          contentStyle={{ fontSize: 12, borderRadius: 6 }}
        />
        <Bar dataKey="value" radius={[4, 4, 0, 0]}>
          {data.map((d) => <Cell key={d.name} fill={d.color} />)}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

function LastVerdict({ wbftResults }) {
  const last = [...wbftResults].sort((a, b) => b.timestamp - a.timestamp)[0];
  if (!last) return <div className="last-verdict-empty">Oczekiwanie na wynik WBFT...</div>;

  const { status, category, userId, winnerRatio, totalWeight,
          weightSums, byzantineSuspects, confident } = last;

  const cls =
    status === "UNANIMOUS" ? "badge--unanimous"
    : status === "CONSENSUS" ? "badge--consensus"
    : "badge--no-quorum";

  const pct = winnerRatio != null ? (winnerRatio * 100).toFixed(1) + "%" : null;

  return (
    <div className="last-verdict">
      <div className="last-verdict-row">
        <span className="last-verdict-label">Ostatni wynik:</span>
        <span className={`consensus-badge ${cls}`}>{status}</span>
        {confident === false && (
          <span className="badge badge--warn" style={{ fontSize: 10 }}>niepewny</span>
        )}
      </div>

      <div className="last-verdict-detail">
        user=<strong>{userId}</strong> &rarr; <strong>{category}</strong>
        {pct ? ` · ${pct} wagi` : ""}
        {totalWeight != null ? ` · Σ=${totalWeight.toFixed(1)}` : ""}
        {" · "}{ts(last.timestamp)}
      </div>

      {weightSums && Object.keys(weightSums).length > 0 && (
        <div className="verdict-weight-sums">
          {Object.entries(weightSums)
            .sort((a, b) => b[1] - a[1])
            .map(([cat, w], i) => {
              const sum  = Object.values(weightSums).reduce((s, v) => s + v, 0);
              const pctW = sum > 0 ? Math.round((w / sum) * 100) : 0;
              return (
                <div className="verdict-weight-row" key={cat}>
                  <span
                    className="verdict-weight-dot"
                    style={{ background: CAT_COLORS[i % CAT_COLORS.length] }}
                  />
                  <span className="verdict-weight-cat">{cat}</span>
                  <div className="vote-bar-bg" style={{ flex: 1, height: 10 }}>
                    <div
                      className="vote-bar-fill"
                      style={{
                        width: `${pctW}%`,
                        background: CAT_COLORS[i % CAT_COLORS.length],
                        height: "100%",
                      }}
                    />
                  </div>
                  <span className="verdict-weight-val">{w.toFixed(1)} ({pctW}%)</span>
                </div>
              );
            })}
        </div>
      )}

      {byzantineSuspects && byzantineSuspects.length > 0 && (
        <div className="byzantine-row">
          <span className="byzantine-label">Byzantine suspects:</span>
          {byzantineSuspects.map((s) => (
            <span className="byzantine-pill" key={s}>{s}</span>
          ))}
        </div>
      )}
    </div>
  );
}

function WbftHistory({ wbftResults }) {
  const sorted = [...wbftResults].sort((a, b) => b.timestamp - a.timestamp).slice(0, 20);
  if (!sorted.length) return <div className="log-empty">Brak wyników WBFT.</div>;

  return (
    <div className="wbft-history">
      {sorted.map((r, i) => {
        const cls =
          r.status === "UNANIMOUS" ? "badge--unanimous"
          : r.status === "CONSENSUS" ? "badge--consensus"
          : "badge--no-quorum";
        const pct      = r.winnerRatio != null ? (r.winnerRatio * 100).toFixed(1) + "%" : "—";
        const suspects = r.byzantineSuspects?.length ?? 0;
        return (
          <div className="wbft-history-row" key={i}>
            <span className="log-time">{ts(r.timestamp)}</span>
            <span className={`consensus-badge ${cls}`} style={{ fontSize: 10, padding: "2px 7px" }}>
              {r.status}
            </span>
            <span className="wbft-history-user">u={r.userId}</span>
            <span className="wbft-history-cat">{r.category}</span>
            <span className="wbft-history-ratio">{pct}</span>
            {suspects > 0 && (
              <span className="byzantine-pill" style={{ fontSize: 10 }}>
                {suspects} suspect{suspects > 1 ? "s" : ""}
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}

function LogRow({ log }) {
  const typeClass =
    log.type === "RABBIT_IN" ? "log-type--rabbit"
    : log.type === "WBFT"    ? "log-type--wbft"
    : log.type === "ERROR"   ? "log-type--err"
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
  const [logs,        setLogs]        = useState([]);
  const [wbftResults, setWbftResults] = useState([]);
  const [activeUsers, setActiveUsers] = useState([]);
  const [connStatus,  setConnStatus]  = useState("connecting");
  const [filterType,  setFilterType]  = useState("");
  const [filterSvc,   setFilterSvc]   = useState("");
  const [activeTab,   setActiveTab]   = useState("last");
  const [svcStatus,   setSvcStatus]   = useState(
    Object.fromEntries(SVC_NAMES.map((s) => [s, { status: "idle", lastSeen: null }]))
  );

  const updateSvcStatus = useCallback((newLogs) => {
    const rabbitLogs = newLogs.filter((l) => l.type === "RABBIT_IN");
    const now  = Date.now();
    const next = {};
    SVC_NAMES.forEach((svc) => {
      const last  = [...rabbitLogs].reverse().find((l) => l.service === svc);
      const fresh = last && (now - last.timestamp < 40000);
      next[svc]   = { status: last ? (fresh ? "ok" : "warn") : "idle", lastSeen: last?.timestamp ?? null };
    });
    setSvcStatus(next);
  }, []);

  const fetchAll = useCallback(async () => {
    try {
      const [stateRes, logsRes, wbftRes] = await Promise.all([
        fetch(BASE + "/monitor/state", { signal: AbortSignal.timeout(3000) }),
        fetch(BASE + "/monitor/logs",  { signal: AbortSignal.timeout(3000) }),
        fetch(BASE + "/monitor/wbft",  { signal: AbortSignal.timeout(3000) }),
      ]);
      if (!stateRes.ok || !logsRes.ok) throw new Error("bad status");

      const [stateData, newLogs, newWbft] = await Promise.all([
        stateRes.json(),
        logsRes.json(),
        wbftRes.ok ? wbftRes.json() : Promise.resolve([]),
      ]);

      setLogs(newLogs);
      setWbftResults(Array.isArray(newWbft) ? newWbft : []);
      setActiveUsers(Object.keys(stateData.activeUsers || {}));
      updateSvcStatus(newLogs);
      setConnStatus("online");
    } catch {
      setLogs((prev) => {
        if (prev.length === 0) {
          const mockLogs = generateMockLogs();
          const mockWbft = generateMockWbft();
          updateSvcStatus(mockLogs);
          setWbftResults(mockWbft);
          setConnStatus("demo");
          return mockLogs;
        }
        setConnStatus("offline");
        return prev;
      });
    }
  }, [updateSvcStatus]);

  useEffect(() => {
    fetchAll();
    const id = setInterval(fetchAll, 5000);
    return () => clearInterval(id);
  }, [fetchAll]);

  const now    = Date.now();
  const total  = logs.filter((l) => l.type === "RABBIT_IN").length;
  const rate   = logs.filter((l) => l.type === "RABBIT_IN" && now - l.timestamp < 60000).length;
  const dlx    = logs.filter((l) => l.type === "DLX" || l.type === "ERROR").length;

  const filteredLogs = logs
    .filter((l) => {
      if (filterType && l.type !== filterType) return false;
      if (filterSvc && !(l.service || "").toLowerCase().includes(filterSvc.toLowerCase())) return false;
      return true;
    })
    .slice(-60)
    .reverse();

  const connLabel =
    connStatus === "online"   ? "Połączony"
    : connStatus === "demo"   ? "Demo (offline)"
    : connStatus === "offline" ? "Offline"
    : "Łączenie...";

  const connCls =
    connStatus === "online"   ? "badge badge--live"
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
          {activeUsers.length > 0 && (
            <span className="badge badge--warn">
              {activeUsers.length} aktywna runda{activeUsers.length > 1 ? "y" : ""}
            </span>
          )}
          <span className={connCls}>{connLabel}</span>
          <button onClick={fetchAll} className="btn">↺ Odśwież</button>
        </div>
      </div>

      <div className="metrics-grid">
        <MetricCard label="Wiadomości łącznie" value={total || "—"} sub="od startu monitorowania" />
        <MetricCard label="Aktywne rundy"       value={activeUsers.length || "0"} sub="oczekujące głosy" />
        <MetricCard label="Głosy / min"         value={rate}              sub="ostatnie 60 s" />
        <MetricCard label="Rund WBFT"           value={wbftResults.length} sub="zakończonych" />
        <MetricCard label="Dead letter"         value={dlx} sub="odrzucone wiad." danger={dlx > 0} />
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
            <div className="section-title">Rozkład głosów (ostatnie 35)</div>
            <VoteBars logs={logs} />
          </div>
        </div>

        <div className="card">
          <div className="card-tabs">
            <button
              className={`tab-btn${activeTab === "last" ? " tab-btn--active" : ""}`}
              onClick={() => setActiveTab("last")}
            >
              Ostatni wynik
            </button>
            <button
              className={`tab-btn${activeTab === "history" ? " tab-btn--active" : ""}`}
              onClick={() => setActiveTab("history")}
            >
              Historia ({wbftResults.length})
            </button>
          </div>

          {activeTab === "last" ? (
            <>
              <WbftChart wbftResults={wbftResults} />
              <LastVerdict wbftResults={wbftResults} />
            </>
          ) : (
            <WbftHistory wbftResults={wbftResults} />
          )}
        </div>
      </div>

      <div className="card">
        <div className="log-header">
          <div className="section-title" style={{ marginBottom: 0 }}>Logi komunikacji</div>
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
              onClick={() => { setLogs([]); setWbftResults([]); setConnStatus("connecting"); }}
              className="btn"
            >
              Wyczyść
            </button>
          </div>
        </div>
        <div className="log-list">
          {filteredLogs.length === 0
            ? <div className="log-empty">Brak pasujących logów.</div>
            : filteredLogs.map((log, i) => <LogRow key={i} log={log} />)
          }
        </div>
      </div>

      <div className="footer-hint">
        Dane z <code>localhost:8081/monitor/logs</code> · <code>/monitor/state</code> · <code>/monitor/wbft</code> · odświeżanie co 5 s
      </div>
    </div>
  );
}