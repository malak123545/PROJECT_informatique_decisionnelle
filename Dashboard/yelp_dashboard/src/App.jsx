import { useState, useEffect } from "react";

// ══════════════════════════════════════════
//  CONFIG — URL de l'API FastAPI
// ══════════════════════════════════════════
const API_URL = "http://api:8000";
// ══════════════════════════════════════════

const getTier = (nb) => {
  if (nb >= 5) return { label: "Platinum", color: "#60a5fa", bg: "rgba(96,165,250,0.15)",  icon: "💎" };
  if (nb >= 3) return { label: "Gold",     color: "#fbbf24", bg: "rgba(251,191,36,0.15)",   icon: "🥇" };
  if (nb >= 1) return { label: "Silver",   color: "#94a3b8", bg: "rgba(148,163,184,0.15)",  icon: "🥈" };
  return         { label: "Bronze",  color: "#cd7c3a", bg: "rgba(205,124,58,0.15)",   icon: "🥉" };
};

export default function Dashboard() {
  const [data,     setData]     = useState([]);
  const [kpis,     setKpis]     = useState({});
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState(null);
  const [selected, setSelected] = useState(null);
  const [filter,   setFilter]   = useState("Tous");
  const [animIn,   setAnimIn]   = useState(false);

  useEffect(() => {
    Promise.all([
      fetch(`${API_URL}/api/scenario1/top-users`).then(r => r.json()),
      fetch(`${API_URL}/api/scenario1/kpis`).then(r => r.json()),
    ])
      .then(([users, kpisRes]) => {
        setData(users.data || []);
        setKpis(kpisRes.data || {});
        setLoading(false);
        setTimeout(() => setAnimIn(true), 100);
      })
      .catch(e => {
        setError(e.message);
        setLoading(false);
      });
  }, []);

  // ── Dérivés ─────────────────────────────
  const types    = ["Tous", ...new Set(data.map(d => d.type_business))];
  const filtered = filter === "Tous" ? data : data.filter(d => d.type_business === filter);
  const sorted   = [...filtered].sort((a, b) => b.score_pertinence - a.score_pertinence).slice(0, 50);
  const scoreMax = sorted.length ? Number(sorted[0].score_pertinence) : 1;

  const tierCounts = data.reduce((acc, u) => {
    const t = getTier(u.nb_annees_elite || 0).label;
    acc[t] = (acc[t] || 0) + 1;
    return acc;
  }, {});

  const typeStats = Object.entries(
    data.reduce((acc, u) => {
      if (!acc[u.type_business]) acc[u.type_business] = { total: 0, count: 0 };
      acc[u.type_business].total += Number(u.score_pertinence) || 0;
      acc[u.type_business].count += 1;
      return acc;
    }, {})
  ).map(([type, v]) => ({ type, avg: Math.round(v.total / v.count) }))
   .sort((a, b) => b.avg - a.avg).slice(0, 8);

  // ── CSS ─────────────────────────────────
  const css = `
    @import url('https://fonts.googleapis.com/css2?family=Cormorant+Garamond:wght@300;400;600;700&family=JetBrains+Mono:wght@400;600&display=swap');
    * { box-sizing:border-box; margin:0; padding:0; }
    ::-webkit-scrollbar { width:4px; }
    ::-webkit-scrollbar-thumb { background:#fbbf24; border-radius:2px; }
    .fade-up { opacity:0; transform:translateY(20px); transition:opacity .6s ease,transform .6s ease; }
    .fade-up.in { opacity:1; transform:translateY(0); }
    .card { background:rgba(255,255,255,0.03); border:1px solid rgba(255,255,255,0.07); border-radius:12px; transition:border-color .2s,background .2s; }
    .card:hover { border-color:rgba(251,191,36,0.3); background:rgba(255,255,255,0.05); }
    .row-item { display:grid; grid-template-columns:32px 1fr 130px 80px 60px 80px; gap:12px; align-items:center; padding:10px 16px; border-bottom:1px solid rgba(255,255,255,0.05); cursor:pointer; transition:background .15s; }
    .row-item:hover { background:rgba(251,191,36,0.05); }
    .row-item.active { background:rgba(251,191,36,0.08); border-left:2px solid #fbbf24; }
    .pill { display:inline-block; padding:2px 8px; border-radius:20px; font-size:10px; font-family:'JetBrains Mono',monospace; font-weight:600; }
    .bar-fill { height:5px; border-radius:3px; background:linear-gradient(90deg,#fbbf24,#f59e0b); transition:width 1s ease; }
    .filter-btn { padding:5px 14px; border-radius:20px; border:1px solid rgba(255,255,255,0.12); background:transparent; color:#94a3b8; font-family:'JetBrains Mono',monospace; font-size:11px; cursor:pointer; transition:all .2s; }
    .filter-btn.active,.filter-btn:hover { background:rgba(251,191,36,0.15); border-color:#fbbf24; color:#fbbf24; }
    .kpi-num { font-family:'Cormorant Garamond',serif; font-size:36px; font-weight:300; color:#fbbf24; line-height:1; }
    .stat-row { display:flex; justify-content:space-between; align-items:center; padding:7px 0; border-bottom:1px solid rgba(255,255,255,0.05); font-size:12px; }
    .stat-row:last-child { border-bottom:none; }
    @keyframes slideIn { from{opacity:0;transform:translateX(10px)} to{opacity:1;transform:translateX(0)} }
    @keyframes spin { to{transform:rotate(360deg)} }
    .spinner { width:32px; height:32px; border:2px solid rgba(251,191,36,0.2); border-top-color:#fbbf24; border-radius:50%; animation:spin .8s linear infinite; }
  `;

  // ── Loading ──────────────────────────────
  if (loading) return (
    <div style={{ minHeight:"100vh", background:"#080c14", display:"flex", flexDirection:"column", alignItems:"center", justifyContent:"center", gap:16 }}>
      <style>{css}</style>
      <div className="spinner" />
      <div style={{ fontFamily:"'JetBrains Mono',monospace", fontSize:12, color:"#475569", letterSpacing:2 }}>CONNEXION À ORACLE...</div>
    </div>
  );

  // ── Error ────────────────────────────────
  if (error) return (
    <div style={{ minHeight:"100vh", background:"#080c14", display:"flex", flexDirection:"column", alignItems:"center", justifyContent:"center", gap:12 }}>
      <style>{css}</style>
      <div style={{ fontSize:32 }}>⚠️</div>
      <div style={{ fontFamily:"'JetBrains Mono',monospace", fontSize:13, color:"#ef4444" }}>{error}</div>
      <div style={{ fontFamily:"'JetBrains Mono',monospace", fontSize:11, color:"#475569", maxWidth:400, textAlign:"center" }}>
        Vérifiez que l'API FastAPI tourne sur {API_URL}
      </div>
    </div>
  );

  // ── Render ───────────────────────────────
  return (
    <div style={{ minHeight:"100vh", background:"#080c14", fontFamily:"'Georgia','Times New Roman',serif", color:"#e2e8f0" }}>
      <style>{css}</style>

      {/* HEADER */}
      <div style={{ background:"linear-gradient(180deg,rgba(251,191,36,0.08) 0%,transparent 100%)", borderBottom:"1px solid rgba(251,191,36,0.15)", padding:"28px 36px 24px" }}>
        <div style={{ display:"flex", justifyContent:"space-between", alignItems:"flex-end" }}>
          <div>
            <div style={{ fontSize:10, letterSpacing:3, color:"#64748b", fontFamily:"'JetBrains Mono',monospace", marginBottom:8, textTransform:"uppercase" }}>
              Scénario 1 · Informatique Décisionnelle · Yelp Dataset
            </div>
            <h1 style={{ fontFamily:"'Cormorant Garamond',serif", fontSize:36, fontWeight:300, color:"#f8fafc" }}>
              Récompenser les <span style={{ color:"#fbbf24", fontStyle:"italic" }}>Contributeurs</span>
            </h1>
            <p style={{ color:"#475569", fontSize:12, marginTop:6, fontFamily:"'JetBrains Mono',monospace" }}>
              Score de pertinence · Statut élite · Réseau social
            </p>
          </div>
          <div style={{ textAlign:"right", fontSize:11, color:"#334155", fontFamily:"'JetBrains Mono',monospace" }}>
            <div style={{ color:"#22c55e", marginBottom:4 }}>● Live · Oracle DB</div>
            <div style={{ color:"#fbbf24" }}>{data.length} utilisateurs chargés</div>
          </div>
        </div>
      </div>

      <div style={{ padding:"28px 36px", maxWidth:1400, margin:"0 auto" }}>

        {/* KPI ROW */}
        <div className={`fade-up ${animIn?"in":""}`} style={{ display:"grid", gridTemplateColumns:"repeat(4,1fr)", gap:16, marginBottom:28, transitionDelay:"0.1s" }}>
          {[
            { label:"Score Maximum",        value: Number(kpis.score_max||0).toLocaleString(),   sub: sorted[0]?.nom_user || "—" },
            { label:"% Utilisateurs Élite", value: `${kpis.pct_elite||0}%`,                      sub: "Statut élite actif" },
            { label:"Reviews Moyennes",     value: Number(kpis.avg_reviews||0).toLocaleString(), sub: "Par contributeur" },
            { label:"Votes Utiles Totaux",  value: Number(kpis.total_useful||0).toLocaleString(),sub: "Cumul top contributeurs" },
          ].map((kpi, i) => (
            <div key={i} className="card" style={{ padding:"20px 22px" }}>
              <div style={{ fontSize:10, letterSpacing:2, color:"#475569", fontFamily:"'JetBrains Mono',monospace", textTransform:"uppercase", marginBottom:10 }}>{kpi.label}</div>
              <div className="kpi-num">{kpi.value}</div>
              <div style={{ fontSize:11, color:"#334155", marginTop:6, fontFamily:"'JetBrains Mono',monospace" }}>{kpi.sub}</div>
            </div>
          ))}
        </div>

        {/* MAIN GRID */}
        <div style={{ display:"grid", gridTemplateColumns:"1fr 320px", gap:20, marginBottom:20 }}>

          {/* LEADERBOARD */}
          <div className={`card fade-up ${animIn?"in":""}`} style={{ transitionDelay:"0.2s" }}>
            <div style={{ padding:"18px 20px 14px", borderBottom:"1px solid rgba(255,255,255,0.06)", display:"flex", justifyContent:"space-between", alignItems:"center", flexWrap:"wrap", gap:8 }}>
              <div style={{ fontFamily:"'Cormorant Garamond',serif", fontSize:18, fontWeight:600 }}>🏆 Leaderboard</div>
              <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
                {types.slice(0,8).map(t => (
                  <button key={t} className={`filter-btn ${filter===t?"active":""}`} onClick={() => setFilter(t)}>{t}</button>
                ))}
              </div>
            </div>
            <div style={{ display:"grid", gridTemplateColumns:"32px 1fr 130px 80px 60px 80px", gap:12, padding:"8px 16px", fontSize:10, color:"#334155", fontFamily:"'JetBrains Mono',monospace", letterSpacing:1, textTransform:"uppercase", borderBottom:"1px solid rgba(255,255,255,0.05)" }}>
              <span>#</span><span>Utilisateur</span><span>Score</span><span>Reviews</span><span>Amis</span><span>Tier</span>
            </div>
            <div style={{ maxHeight:480, overflowY:"auto" }}>
              {sorted.map((u, i) => {
                const tier = getTier(u.nb_annees_elite || 0);
                const pct  = Math.round((Number(u.score_pertinence) / scoreMax) * 100);
                return (
                  <div key={`${u.user_id}-${i}`} className={`row-item ${selected?.user_id===u.user_id?"active":""}`} onClick={() => setSelected(selected?.user_id===u.user_id ? null : u)}>
                    <span style={{ fontFamily:"'JetBrains Mono',monospace", fontSize:12, color:i===0?"#fbbf24":"#475569" }}>{i===0?"★":i+1}</span>
                    <div>
                      <div style={{ fontSize:13, fontWeight:500, color:"#f1f5f9" }}>{u.nom_user}</div>
                      <div style={{ marginTop:4 }}>
                        <div className="bar-fill" style={{ width:animIn?`${Math.min(pct,100)}%`:"0%", maxWidth:160 }} />
                      </div>
                    </div>
                    <div>
                      <div style={{ fontFamily:"'JetBrains Mono',monospace", fontSize:13, color:"#fbbf24", fontWeight:600 }}>{Number(u.score_pertinence).toLocaleString()}</div>
                      <div style={{ fontSize:10, color:"#334155", fontFamily:"'JetBrains Mono',monospace" }}>{pct}% du max</div>
                    </div>
                    <span style={{ fontFamily:"'JetBrains Mono',monospace", fontSize:12, color:"#94a3b8" }}>{Number(u.review_count).toLocaleString()}</span>
                    <span style={{ fontFamily:"'JetBrains Mono',monospace", fontSize:12, color:"#64748b" }}>{u.nb_amis}</span>
                    <span className="pill" style={{ background:tier.bg, color:tier.color }}>{tier.icon} {tier.label}</span>
                  </div>
                );
              })}
            </div>
          </div>

          {/* RIGHT COLUMN */}
          <div style={{ display:"flex", flexDirection:"column", gap:16 }}>

            {/* Tier Distribution */}
            <div className={`card fade-up ${animIn?"in":""}`} style={{ padding:20, transitionDelay:"0.3s" }}>
              <div style={{ fontFamily:"'Cormorant Garamond',serif", fontSize:16, fontWeight:600, marginBottom:16 }}>Répartition des Tiers</div>
              {[
                { tier:"Platinum", icon:"💎", color:"#60a5fa" },
                { tier:"Gold",     icon:"🥇", color:"#fbbf24" },
                { tier:"Silver",   icon:"🥈", color:"#94a3b8" },
                { tier:"Bronze",   icon:"🥉", color:"#cd7c3a" },
              ].map(({ tier, icon, color }) => {
                const count = tierCounts[tier] || 0;
                const pct   = data.length ? Math.round((count / data.length) * 100) : 0;
                return (
                  <div key={tier} style={{ marginBottom:14 }}>
                    <div style={{ display:"flex", justifyContent:"space-between", marginBottom:5, fontSize:12 }}>
                      <span style={{ color:"#94a3b8" }}>{icon} {tier}</span>
                      <span style={{ fontFamily:"'JetBrains Mono',monospace", color, fontSize:11 }}>{count} · {pct}%</span>
                    </div>
                    <div style={{ height:5, background:"rgba(255,255,255,0.05)", borderRadius:3 }}>
                      <div style={{ height:"100%", width:animIn?`${pct}%`:"0%", background:color, borderRadius:3, transition:"width 1s ease 0.5s" }} />
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Score par secteur */}
            <div className={`card fade-up ${animIn?"in":""}`} style={{ padding:20, transitionDelay:"0.4s" }}>
              <div style={{ fontFamily:"'Cormorant Garamond',serif", fontSize:16, fontWeight:600, marginBottom:16 }}>Score Moyen / Secteur</div>
              {typeStats.map(({ type, avg }, i) => {
                const pct = typeStats[0].avg ? Math.round((avg / typeStats[0].avg) * 100) : 0;
                return (
                  <div key={type} style={{ marginBottom:10 }}>
                    <div style={{ display:"flex", justifyContent:"space-between", marginBottom:4, fontSize:11, fontFamily:"'JetBrains Mono',monospace" }}>
                      <span style={{ color:"#64748b" }}>{type}</span>
                      <span style={{ color:"#fbbf24" }}>{avg.toLocaleString()}</span>
                    </div>
                    <div style={{ height:4, background:"rgba(255,255,255,0.05)", borderRadius:2 }}>
                      <div style={{ height:"100%", width:animIn?`${pct}%`:"0%", background:`hsl(${40-i*5},85%,${58-i*4}%)`, borderRadius:2, transition:`width 1s ease ${0.6+i*0.08}s` }} />
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Detail panel */}
            {selected && (
              <div className="card" style={{ padding:20, borderColor:"rgba(251,191,36,0.25)", background:"rgba(251,191,36,0.04)", animation:"slideIn .3s ease" }}>
                <div style={{ display:"flex", justifyContent:"space-between", alignItems:"center", marginBottom:14 }}>
                  <div style={{ fontFamily:"'Cormorant Garamond',serif", fontSize:16, fontWeight:600 }}>{selected.nom_user}</div>
                  <span className="pill" style={{ background:getTier(selected.nb_annees_elite||0).bg, color:getTier(selected.nb_annees_elite||0).color }}>
                    {getTier(selected.nb_annees_elite||0).icon} {getTier(selected.nb_annees_elite||0).label}
                  </span>
                </div>
                {[
                  ["Score",          Number(selected.score_pertinence).toLocaleString()],
                  ["Secteur",        selected.type_business],
                  ["Reviews",        selected.review_count],
                  ["Votes utiles",   selected.useful],
                  ["Funny / Cool",   `${selected.funny} / ${selected.cool}`],
                  ["Amis",           selected.nb_amis],
                  ["Fans",           selected.fans],
                  ["Années élite",   selected.nb_annees_elite || "—"],
                  ["Note moyenne",   `★ ${Number(selected.average_stars).toFixed(2)}`],
                  ["Top review",     `${selected.best_review_useful} votes · ★${selected.best_review_stars}`],
                  ["Date",           selected.best_review_date || "—"],
                ].map(([label, val]) => (
                  <div className="stat-row" key={label}>
                    <span style={{ color:"#64748b", fontFamily:"'JetBrains Mono',monospace", fontSize:10, letterSpacing:1 }}>{label}</span>
                    <span style={{ color:"#e2e8f0", fontWeight:500, fontSize:12 }}>{val}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        <div style={{ textAlign:"center", padding:"16px 0", fontSize:10, color:"#1e293b", fontFamily:"'JetBrains Mono',monospace", letterSpacing:2 }}>
          YELP · DATA WAREHOUSE · M2 BDIA 2025–2026 · SCÉNARIO 1
        </div>
      </div>
    </div>
  );
}