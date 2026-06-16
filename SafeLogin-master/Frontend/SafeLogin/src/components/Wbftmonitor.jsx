import { useState, useEffect, useCallback } from "react";
import {
  BarChart, Bar, XAxis, YAxis, Tooltip,
  ResponsiveContainer, Cell,
} from "recharts";
import FaultInjectionPanel from "./FaultInjectionPanel";
import "./Wbftmonitor.css";
import "./FaultInjectionpanel.css";

const BASE = "http://localhost:8081";
const SVC_NAMES = [
  "Service1","Service2","Service3",
  "Service4","Service5","Service6","Service7",
];
const CAT_COLORS = {
  ACTION:      "#3266ad",
  COMEDY:      "#1D9E75",
  DRAMA:       "#D4537E",
  THRILLER:    "#BA7517",
  DOCUMENTARY: "#534AB7",
  OTHER:       "#888780",
};
const CAT_COLOR_LIST = Object.values(CAT_COLORS);

function ts(ms) {
  return new Date(ms).toTimeString().slice(0, 8);
}

/* ─── mock data ───────────────────────────────────────────────────── */
function generateMockLogs() {
  const cats = ["ACTION","COMEDY","DRAMA","THRILLER","DOCUMENTARY"];
  const now = Date.now();
  return Array.from({ length: 42 }, (_, i) => {
    const svc = SVC_NAMES[i % SVC_NAMES.length];
    const cat = cats[Math.floor(Math.random() * cats.length)];
    return {
      type: "RABBIT_IN",
      service: svc,
      routingKey: "vote." + svc,
      weight: parseFloat((1 + Math.random() * 3).toFixed(1)),
      content: { userId: 10 + (i % 5), category: cat },
      timestamp: now - (41 - i) * 8000,
    };
  });
}

function generateMockWbft() {
  const cats     = ["ACTION","COMEDY","DRAMA","THRILLER","DOCUMENTARY"];
  const statuses = ["UNANIMOUS","CONSENSUS","NO_QUORUM"];
  const now      = Date.now();
  return Array.from({ length: 12 }, (_, i) => {
    const cat   = cats[Math.floor(Math.random() * cats.length)];
    const st    = statuses[Math.floor(Math.random() * statuses.length)];
    const total = parseFloat((10 + Math.random() * 8).toFixed(1));
    const winW  = parseFloat((total * (0.55 + Math.random() * 0.4)).toFixed(1));
    const alt   = cats.find((c) => c !== cat) || "OTHER";
    const weightSums = { [cat]: winW };
    if (st !== "UNANIMOUS") weightSums[alt] = parseFloat((total - winW).toFixed(1));
    return {
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
      nodeVotes: SVC_NAMES.map(svc => ({
        service: svc,
        category: st === "NO_QUORUM" && Math.random() > 0.6
          ? cats[Math.floor(Math.random() * cats.length)]
          : cat,
        weight: parseFloat((1 + Math.random() * 3).toFixed(1)),
        state: st === "NO_QUORUM" && Math.random() > 0.6 ? "byzantine" : "ok",
        timedOut: false,
      })),
    };
  });
}

/* ─── sub-components ──────────────────────────────────────────────── */

function MetricCard({ label, value, sub, danger, accent }) {
  return (
    <div className={`metric-card${danger ? " metric-card--danger" : ""}${accent ? " metric-card--accent" : ""}`}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value ?? "—"}</div>
      <div className="metric-sub">{sub}</div>
    </div>
  );
}

function VoteBars({ logs }) {
  const rabbitLogs = logs.filter((l) => l.type === "RABBIT_IN" && l.content?.category);
  if (!rabbitLogs.length)
    return <div className="empty-hint">Oczekiwanie na głosy…</div>;

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
            <div className="vote-row-label">{cat}</div>
            <div className="vote-bar-bg">
              <div
                className="vote-bar-fill"
                style={{ width: `${pct}%`, background: CAT_COLOR_LIST[i % CAT_COLOR_LIST.length] }}
              />
            </div>
            <div className="vote-pct">{pct}%</div>
          </div>
        );
      })}
    </div>
  );
}

function WbftStatusChart({ wbftResults }) {
  const counts = { UNANIMOUS: 0, CONSENSUS: 0, NO_QUORUM: 0, NO_DATA: 0 };
  wbftResults.forEach((r) => { if (counts[r.status] !== undefined) counts[r.status]++; });
  const data = [
    { name: "UNANIMOUS", value: counts.UNANIMOUS, color: "#1D9E75" },
    { name: "CONSENSUS",  value: counts.CONSENSUS,  color: "#3266ad" },
    { name: "NO_QUORUM",  value: counts.NO_QUORUM,  color: "#E24B4A" },
    { name: "NO_DATA",    value: counts.NO_DATA,    color: "#888780" },
  ];
  return (
    <ResponsiveContainer width="100%" height={140}>
      <BarChart data={data} margin={{ top: 4, right: 4, left: -22, bottom: 0 }}>
        <XAxis dataKey="name" tick={{ fontSize: 9 }} />
        <YAxis allowDecimals={false} tick={{ fontSize: 10 }} />
        <Tooltip
          formatter={(v) => [v, "wyniki"]}
          contentStyle={{ fontSize: 11, borderRadius: 6 }}
        />
        <Bar dataKey="value" radius={[4, 4, 0, 0]}>
          {data.map((d) => <Cell key={d.name} fill={d.color} />)}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

function NodeGrid({ result }) {
  if (!result) return <div className="empty-hint">Brak danych węzłów.</div>;

  const nodeVotes = result.nodeVotes || SVC_NAMES.map(svc => ({
    service: svc,
    category: result.category,
    weight: null,
    state: "ok",
    timedOut: false,
  }));

  return (
    <div className="node-grid">
      {nodeVotes.map((nv) => {
        const stateClass = nv.timedOut ? "timeout"
          : nv.state === "byzantine" ? "byzantine"
          : nv.state === "missing"   ? "missing"
          : "ok";
        const short = nv.service.replace("Service", "S");
        return (
          <div key={nv.service} className={`node-card node-card--${stateClass}`}>
            {nv.state === "byzantine" && <div className="node-badge node-badge--byz">!</div>}
            {nv.timedOut && <div className="node-badge node-badge--timeout">⏱</div>}
            <div className="node-name">{short}</div>
            <div
              className="node-cat"
              style={{ color: nv.category ? (CAT_COLORS[nv.category] || "#888") : undefined }}
            >
              {nv.category || "—"}
            </div>
            <div className="node-weight">
              {nv.weight != null ? `w=${nv.weight.toFixed(1)}` : "brak"}
            </div>
          </div>
        );
      })}
    </div>
  );
}

function ThresholdBar({ label, ratio, threshold, thresholdLabel }) {
  const pct    = Math.min(100, Math.round(ratio * 100));
  const tPct   = Math.round(threshold * 100);
  const passed = ratio >= threshold;
  return (
    <div className="threshold-wrap">
      <div className="threshold-header">
        <span className="threshold-label">{label}</span>
        <span className={`threshold-value ${passed ? "threshold-value--pass" : "threshold-value--fail"}`}>
          {pct}% {passed ? "✓" : "✗"}
        </span>
      </div>
      <div className="threshold-track">
        <div
          className="threshold-fill"
          style={{
            width: `${pct}%`,
            background: passed ? "#1D9E75" : "#E24B4A",
          }}
        />
        <div className="threshold-marker" style={{ left: `${tPct}%` }}>
          <span className="threshold-marker-label">{thresholdLabel}</span>
        </div>
      </div>
    </div>
  );
}

function WbftDetailPanel({ result }) {
  if (!result) return <div className="empty-hint">Oczekiwanie na wynik WBFT…</div>;

  const { status, category, userId, winnerRatio, totalWeight,
          weightSums, byzantineSuspects, confident, nodeVotes, timestamp } = result;

  const voterCount = nodeVotes?.filter(n => !n.timedOut && n.state !== "missing").length ?? result.voterCount ?? 0;
  const timedOut   = nodeVotes?.filter(n => n.timedOut) ?? [];

  const catSorted  = weightSums
    ? Object.entries(weightSums).sort((a, b) => b[1] - a[1])
    : [];
  const totalW = catSorted.reduce((s, [,v]) => s + v, 0);

  const relativeDiff = catSorted.length >= 2
    ? (catSorted[0][1] - catSorted[1][1]) / totalW
    : catSorted.length === 1 ? 1 : 0;

  const statusCls = status === "UNANIMOUS" ? "badge--unanimous"
    : status === "CONSENSUS"               ? "badge--consensus"
    : "badge--noquorum";

  return (
    <>
      <div className="verdict-box">
        <div className="verdict-row">
          <span className={`status-badge ${statusCls}`}>{status}</span>
          <span className="verdict-winner">{category}</span>
          {confident === false && (
            <span className="badge-warn">niepewny</span>
          )}
          {timedOut.length > 0 && (
            <span className="badge-timeout">timeout: {timedOut.map(t => t.service.replace("Service","S")).join(", ")}</span>
          )}
        </div>
        <div className="verdict-meta">
          user=<strong>{userId}</strong> &nbsp;·&nbsp;
          waga zwycięzcy: <strong>{(winnerRatio * 100).toFixed(1)}%</strong> &nbsp;·&nbsp;
          Σ=<strong>{totalWeight?.toFixed(1)}</strong> &nbsp;·&nbsp;
          węzłów: <strong>{voterCount}/7</strong> &nbsp;·&nbsp;
          {ts(timestamp)}
        </div>
        {byzantineSuspects?.length > 0 && (
          <div className="byzantine-row">
            <span className="byzantine-label">Byzantine suspects:</span>
            {byzantineSuspects.map(s => (
              <span key={s} className="byzantine-pill">{s.replace("Service","S")}</span>
            ))}
          </div>
        )}
      </div>

      <div className="section-title" style={{ marginTop: 14 }}>Węzły satelitarne</div>
      <NodeGrid result={result} />

      <div className="section-title" style={{ marginTop: 14 }}>Progi decyzyjne</div>
      <ThresholdBar
        label="Kworum zwycięzcy"
        ratio={winnerRatio ?? 0}
        threshold={2/3}
        thresholdLabel="66.7%"
      />
      <ThresholdBar
        label="Uczestnictwo węzłów"
        ratio={voterCount / 7}
        threshold={4/7}
        thresholdLabel="4/7"
      />
      <ThresholdBar
        label="Margines przewagi"
        ratio={relativeDiff}
        threshold={0.10}
        thresholdLabel="10%"
      />

      {catSorted.length > 0 && (
        <>
          <div className="section-title" style={{ marginTop: 14 }}>Wagi per kategoria</div>
          <div className="weight-bars">
            {catSorted.map(([cat, w], i) => {
              const pct  = totalW > 0 ? Math.round(w / totalW * 100) : 0;
              const color = CAT_COLORS[cat] || CAT_COLOR_LIST[i % CAT_COLOR_LIST.length];
              return (
                <div className="weight-row" key={cat}>
                  <div className="weight-dot" style={{ background: color }} />
                  <div className="weight-cat">{cat}</div>
                  <div className="vote-bar-bg" style={{ flex: 1 }}>
                    <div className="vote-bar-fill" style={{ width: `${pct}%`, background: color }} />
                  </div>
                  <div className="weight-val">{w.toFixed(1)} ({pct}%)</div>
                </div>
              );
            })}
          </div>
        </>
      )}
    </>
  );
}

function WbftHistory({ wbftResults }) {
  const sorted = [...wbftResults].sort((a, b) => b.timestamp - a.timestamp).slice(0, 20);
  if (!sorted.length) return <div className="empty-hint">Brak wyników WBFT.</div>;
  return (
    <div className="wbft-history">
      {sorted.map((r, i) => {
        const cls = r.status === "UNANIMOUS" ? "badge--unanimous"
          : r.status === "CONSENSUS" ? "badge--consensus"
          : "badge--noquorum";
        const pct = r.winnerRatio != null ? (r.winnerRatio * 100).toFixed(1) + "%" : "—";
        const suspects = r.byzantineSuspects?.length ?? 0;
        const timedOut = r.nodeVotes?.filter(n => n.timedOut).length ?? 0;
        return (
          <div className="history-row" key={i}>
            <span className="log-time">{ts(r.timestamp)}</span>
            <span className={`status-badge status-badge--sm ${cls}`}>{r.status}</span>
            <span className="history-user">u={r.userId}</span>
            <span className="history-cat">{r.category}</span>
            <span className="history-ratio">{pct}</span>
            {suspects > 0 && (
              <span className="byzantine-pill" style={{ fontSize: 10 }}>{suspects}B</span>
            )}
            {timedOut > 0 && (
              <span className="badge-timeout" style={{ fontSize: 10 }}>{timedOut}T</span>
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
    msg = `rk=${log.routingKey || ""} userId=${log.content?.userId ?? "?"} cat=${log.content?.category ?? "?"} w=${log.weight ?? "?"}`;
  } else if (log.type === "WBFT") {
    msg = `status=${log.content?.status ?? "?"} cat=${log.content?.category ?? "?"} user=${log.content?.userId ?? "?"}`;
  } else {
    msg = JSON.stringify(log.content || log).slice(0, 80);
  }

  return (
    <div className="log-row">
      <span className="log-time">{ts(log.timestamp || Date.now())}</span>
      <span className={`log-type ${typeClass}`}>{log.type || "?"}</span>
      <span className="log-svc">{(log.service || "?").replace("Service","S")}</span>
      <span className="log-msg">{msg}</span>
    </div>
  );
}

function SatelliteHealthPanel({ health }) {
  if (!health) {
    return <div className="empty-hint">Brak danych health monitor.</div>;
  }
  const services = Object.entries(health.services || {});
  return (
    <div className="sat-health-panel">
      <div className="health-summary">
        <MetricCard label="Satelity"     value={health.totalServices} sub="łącznie" />
        <MetricCard label="DOWN"         value={health.downCount}     sub="niedostępne" danger={health.downCount > 0} />
        <MetricCard label="Próg alarmu"  value={`${health.alertThresholdMs / 1000}s`} sub="heartbeat timeout" />
      </div>
      <div className="health-grid">
        {services.map(([name, svc]) => (
          <div key={name} className={`health-card health-card--${svc.status?.toLowerCase()}`}>
            <div className="health-card-header">
              <strong>{name}</strong>
              <span className={`status-badge ${svc.status === "UP" ? "badge--unanimous" : "badge--noquorum"}`}>
                {svc.status}
              </span>
            </div>
            <div className="health-info">
              <div>URL: {svc.url}</div>
              <div>Silence: {svc.silenceSec}s</div>
              <div>Alert: {svc.alertSent ? "TAK" : "NIE"}</div>
              <div>Last Seen: {svc.lastSeen ? ts(svc.lastSeen) : "brak"}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ─── Main component ──────────────────────────────────────────────── */

const MAIN_TABS = [
  { id: "monitor",        label: "Monitor" },
  { id: "fault-injection", label: "Fault Injection" },
];

export default function WbftMonitor() {
  const [mainTab,         setMainTab]         = useState("monitor");
  const [logs,            setLogs]            = useState([]);
  const [wbftResults,     setWbftResults]     = useState([]);
  const [activeUsers,     setActiveUsers]     = useState([]);
  const [satelliteHealth, setSatelliteHealth] = useState(null);
  const [connStatus,      setConnStatus]      = useState("connecting");
  const [filterType,      setFilterType]      = useState("");
  const [filterSvc,       setFilterSvc]       = useState("");
  const [activeTab,       setActiveTab]       = useState("detail");
  const [selectedIdx,     setSelectedIdx]     = useState(0);
  const [svcStatus,       setSvcStatus]       = useState(
    Object.fromEntries(SVC_NAMES.map((s) => [s, { status: "idle", lastSeen: null }]))
  );

  const updateSvcStatus = useCallback((newLogs) => {
    const now = Date.now();
    const next = {};
    SVC_NAMES.forEach((svc) => {
      const last  = [...newLogs].reverse().find((l) => l.service === svc && l.type === "RABBIT_IN");
      const fresh = last && (now - last.timestamp < 40000);
      next[svc]   = { status: last ? (fresh ? "ok" : "warn") : "idle", lastSeen: last?.timestamp ?? null };
    });
    setSvcStatus(next);
  }, []);

  const fetchAll = useCallback(async () => {
    try {
      const [stateRes, logsRes, wbftRes, healthRes] = await Promise.all([
        fetch(BASE + "/monitor/state",            { signal: AbortSignal.timeout(3000) }),
        fetch(BASE + "/monitor/logs",             { signal: AbortSignal.timeout(3000) }),
        fetch(BASE + "/monitor/wbft",             { signal: AbortSignal.timeout(3000) }),
        fetch(BASE + "/monitor/satellite-health", { signal: AbortSignal.timeout(3000) }),
      ]);
      if (!stateRes.ok || !logsRes.ok) throw new Error("bad status");
      const [stateData, newLogs, newWbft, healthData] = await Promise.all([
        stateRes.json(),
        logsRes.json(),
        wbftRes.ok   ? wbftRes.json()   : Promise.resolve([]),
        healthRes.ok ? healthRes.json() : Promise.resolve(null),
      ]);
      setLogs(newLogs);
      setWbftResults(Array.isArray(newWbft) ? newWbft : []);
      setActiveUsers(Object.keys(stateData.activeUsers || {}));
      setSatelliteHealth(healthData);
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

  const now   = Date.now();
  const total = logs.filter((l) => l.type === "RABBIT_IN").length;
  const rate  = logs.filter((l) => l.type === "RABBIT_IN" && now - l.timestamp < 60000).length;
  const dlx   = logs.filter((l) => l.type === "DLX" || l.type === "ERROR").length;

  const sortedWbft     = [...wbftResults].sort((a, b) => b.timestamp - a.timestamp);
  const selectedResult = sortedWbft[selectedIdx] ?? null;

  const filteredLogs = logs
    .filter((l) => {
      if (filterType && l.type !== filterType) return false;
      if (filterSvc && !(l.service || "").toLowerCase().includes(filterSvc.toLowerCase())) return false;
      return true;
    })
    .slice(-60)
    .reverse();

  const connLabel = connStatus === "online" ? "Połączony"
    : connStatus === "demo"    ? "Demo"
    : connStatus === "offline" ? "Offline"
    : "Łączenie…";
  const connCls = connStatus === "online" ? "badge badge--live"
    : connStatus === "offline"            ? "badge badge--error"
    : "badge badge--warn";

  const byzantineCount = sortedWbft.reduce((acc, r) => acc + (r.byzantineSuspects?.length ?? 0), 0);

  return (
    <div className="monitor">

      {/* ── Top bar ── */}
      <div className="top-bar">
        <div className="top-bar__left">
          <span className="dot-blink" aria-hidden="true" />
          <h1 className="top-bar__title">WBFT Monitor</h1>
        </div>
        <div className="top-bar__actions">
          {activeUsers.length > 0 && (
            <span className="badge badge--warn">{activeUsers.length} aktywna runda</span>
          )}
          <span className={connCls}>{connLabel}</span>
          <button onClick={fetchAll} className="btn">↺</button>
        </div>
      </div>

      {/* ── Main tabs ── */}
      <div className="main-tabs">
        {MAIN_TABS.map(tab => (
          <button
            key={tab.id}
            className={`main-tab-btn${mainTab === tab.id ? " main-tab-btn--active" : ""}`}
            onClick={() => setMainTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* ── Fault Injection tab ── */}
      {mainTab === "fault-injection" && (
        <div className="card">
          <FaultInjectionPanel />
        </div>
      )}

      {/* ── Monitor tab ── */}
      {mainTab === "monitor" && (
        <>
          {/* Metrics */}
          <div className="metrics-grid">
            <MetricCard label="Wiadomości"   value={total || "—"}         sub="łącznie" />
            <MetricCard label="Aktywne rundy" value={activeUsers.length || "0"} sub="oczekują głosów" accent />
            <MetricCard label="Głosy / min"  value={rate}                 sub="ostatnie 60 s" />
            <MetricCard label="Rundy WBFT"   value={wbftResults.length}   sub="zakończone" />
            <MetricCard label="Byzantine"    value={byzantineCount}        sub="wykryte węzły" danger={byzantineCount > 0} />
            <MetricCard label="Dead letter"  value={dlx}                  sub="odrzucone" danger={dlx > 0} />
          </div>

          {/* Satellite Health */}
          <div className="card">
            <div className="section-title">Zdrowie serwisów satelitarnych</div>
            <SatelliteHealthPanel health={satelliteHealth} />
          </div>

          {/* Main grid */}
          <div className="main-grid">

            <div className="card">
              <div className="section-title" style={{ marginTop: 16 }}>Rozkład głosów (ostatnie 35)</div>
              <VoteBars logs={logs} />
            </div>

            <div className="card">
              <div className="card-tabs">
                <button
                  className={`tab-btn${activeTab === "detail" ? " tab-btn--active" : ""}`}
                  onClick={() => setActiveTab("detail")}
                >
                  Szczegóły wyniku
                </button>
                <button
                  className={`tab-btn${activeTab === "history" ? " tab-btn--active" : ""}`}
                  onClick={() => setActiveTab("history")}
                >
                  Historia ({wbftResults.length})
                </button>
                <button
                  className={`tab-btn${activeTab === "chart" ? " tab-btn--active" : ""}`}
                  onClick={() => setActiveTab("chart")}
                >
                  Wykres
                </button>
              </div>

              {activeTab === "detail" && (
                <>
                  {sortedWbft.length > 1 && (
                    <div className="result-selector">
                      <label className="result-selector-label">Runda:</label>
                      <select
                        className="result-selector-select"
                        value={selectedIdx}
                        onChange={e => setSelectedIdx(Number(e.target.value))}
                      >
                        {sortedWbft.map((r, i) => (
                          <option key={i} value={i}>
                            {ts(r.timestamp)} · u={r.userId} · {r.status}
                          </option>
                        ))}
                      </select>
                    </div>
                  )}
                  <WbftDetailPanel result={selectedResult} />
                </>
              )}

              {activeTab === "history" && (
                <WbftHistory wbftResults={wbftResults} />
              )}

              {activeTab === "chart" && (
                <>
                  <WbftStatusChart wbftResults={wbftResults} />
                  <div className="chart-legend">
                    {[
                      { label: "UNANIMOUS", color: "#1D9E75" },
                      { label: "CONSENSUS",  color: "#3266ad" },
                      { label: "NO_QUORUM",  color: "#E24B4A" },
                    ].map(({ label, color }) => (
                      <span key={label} className="chart-legend-item">
                        <span className="chart-legend-dot" style={{ background: color }} />
                        {label}
                      </span>
                    ))}
                  </div>
                </>
              )}
            </div>
          </div>

          {/* Logs */}
          <div className="card">
            <div className="log-header">
              <div className="section-title" style={{ marginBottom: 0 }}>Logi komunikacji</div>
              <div className="log-controls">
                <select
                  value={filterType}
                  onChange={(e) => setFilterType(e.target.value)}
                  className="log-select"
                >
                  <option value="">Wszystkie typy</option>
                  <option value="RABBIT_IN">RABBIT_IN</option>
                  <option value="WBFT">WBFT</option>
                  <option value="ERROR">ERROR</option>
                </select>
                <input
                  type="text"
                  placeholder="Serwis…"
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
                ? <div className="empty-hint">Brak pasujących logów.</div>
                : filteredLogs.map((log, i) => <LogRow key={i} log={log} />)
              }
            </div>
          </div>

          <div className="footer-hint">
            Dane z <code>localhost:8081/monitor</code> · odświeżanie co 5 s
          </div>
        </>
      )}
    </div>
  );
}