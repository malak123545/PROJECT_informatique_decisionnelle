import { useState, useCallback } from "react";
import {
  BarChart, Bar, LineChart, Line, XAxis, YAxis, Tooltip,
  ResponsiveContainer, RadarChart, Radar, PolarGrid, PolarAngleAxis,
  Cell, PieChart, Pie,
} from "recharts";

// ─── Palette ──────────────────────────────────────────────────
const C = {
  bg: "#080b12", surface: "#0f1320", card: "#141927",
  border: "#1c2235", accent: "#f5a623",
  blue: "#4f8ef7", green: "#3ecf8e", red: "#f74f6f",
  purple: "#a78bfa", teal: "#2dd4bf", text: "#dde2ef", muted: "#5a637a",
};

// ─── Requêtes SQL par scénario ────────────────────────────────
// Chaque scénario a une ou plusieurs requêtes qui ciblent
// exactement les vues matérialisées / tables du DW Oracle.

const QUERIES = {
  s1_score_global: `
-- Scénario 1 : Top users par score de pertinence
-- Source : MV_USER_SCORE_GLOBAL
SELECT
    user_id,
    name,
    friend_count,
    review_count,
    average_stars,
    useful,
    fans,
    nb_annees_elite,
    (useful + fans
      + (review_count * average_stars)
      + (nb_annees_elite * 100)
      + (friend_count / 10)) AS score_pertinence
FROM MV_USER_SCORE_GLOBAL
ORDER BY score_pertinence DESC
FETCH FIRST 10 ROWS ONLY`,

  s1_elite: `
-- Scénario 1 : Détail statut élite
-- Source : MV_TOP_USER_ELITE (jointure FAIT_USER × DIM_USER_ELITE)
SELECT
    f.name,
    e.nbr_elite_years,
    e.derniere_annee_elite,
    f.average_stars,
    f.review_count,
    f.useful
FROM MV_TOP_USER_ELITE f
JOIN DIM_USER_ELITE e ON f.user_id = e.user_id
WHERE e.nbr_elite_years > 0
ORDER BY e.nbr_elite_years DESC
FETCH FIRST 6 ROWS ONLY`,

  s2_by_city: `
-- Scénario 2 : Performance commerciale par ville
-- Source : MV_STARS_BY_CITY (FAIT_BUSINESS × DIM_LOCALISATION)
SELECT
    city,
    state,
    nb_business,
    ROUND(avg_stars, 2)     AS avg_stars,
    max_stars,
    min_stars,
    total_reviews
FROM MV_STARS_BY_CITY
ORDER BY nb_business DESC
FETCH FIRST 8 ROWS ONLY`,

  s2_by_categorie: `
-- Scénario 2 : Performance par catégorie
-- Source : MV_STARS_BY_CATEGORIE (FAIT_BUSINESS × DIM_CATEGORIE)
SELECT
    categorie_name,
    nb_business,
    ROUND(avg_stars, 2)     AS avg_stars,
    total_reviews
FROM MV_STARS_BY_CATEGORIE
ORDER BY total_reviews DESC
FETCH FIRST 8 ROWS ONLY`,

  s2_by_type: `
-- Scénario 2 : Performance par type de business
-- Source : MV_STARS_BY_TYPE (FAIT_BUSINESS × DIM_TYPE_BUSINESS)
SELECT
    type_name,
    nb_business,
    ROUND(avg_stars, 2)     AS avg_stars,
    total_reviews
FROM MV_STARS_BY_TYPE
ORDER BY nb_business DESC`,

  s3_evolution: `
-- Scénario 3 : Évolution temporelle des notes par ville
-- Source : MV_STARS_EVOLUTION
--   (FAIT_REVIEW × DIM_LOCALISATION × DIM_TEMPS)
SELECT
    city,
    state,
    annee,
    nb_reviews,
    ROUND(avg_stars, 3)     AS avg_stars,
    total_useful
FROM MV_STARS_EVOLUTION
WHERE annee >= 2019
ORDER BY city, annee`,

  s3_lag: `
-- Scénario 3 : Détection des signaux faibles avec LAG()
-- Δ avg_stars d'une année sur l'autre par ville
SELECT
    city,
    annee,
    ROUND(avg_stars, 3)         AS avg_stars,
    ROUND(
      avg_stars - LAG(avg_stars, 1) OVER (
        PARTITION BY city ORDER BY annee
      ), 3)                     AS delta_stars,
    nb_reviews,
    total_useful
FROM MV_STARS_EVOLUTION
WHERE annee >= 2019
ORDER BY city, annee`,

  s4_top_users: `
-- Scénario 4 : Types de commerces attirant les meilleurs users
-- Source : MV_BUSINESS_TOP_USERS
--   (FAIT_REVIEW × FAIT_BUSINESS × DIM_TYPE_BUSINESS
--    × DIM_LOCALISATION × FAIT_USER WHERE useful > 100)
SELECT
    type_name,
    city,
    nb_users_distincts,
    ROUND(avg_stars_reviews, 2) AS avg_stars_reviews,
    total_useful
FROM MV_BUSINESS_TOP_USERS
ORDER BY total_useful DESC
FETCH FIRST 10 ROWS ONLY`,

  s4_votes: `
-- Scénario 4 : Votes useful / funny / cool par user
-- Source : FAIT_USER (colonnes directes)
SELECT
    name,
    useful,
    funny,
    cool,
    fans,
    average_stars
FROM FAIT_USER
WHERE useful > 0
ORDER BY useful DESC
FETCH FIRST 10 ROWS ONLY`,

  s4_elite_by_biz: `
-- Scénario 4 : Types de commerces attirant les users élite
-- Source : MV_ELITE_USERS_BY_BUSINESS
--   (FAIT_REVIEW × FAIT_BUSINESS × DIM_TYPE_BUSINESS
--    × DIM_LOCALISATION × FAIT_USER WHERE nb_annees_elite > 0)
SELECT
    type_name,
    city,
    nb_users_elite,
    ROUND(avg_stars, 2)         AS avg_stars,
    ROUND(avg_annees_elite, 1)  AS avg_annees_elite
FROM MV_ELITE_USERS_BY_BUSINESS
ORDER BY nb_users_elite DESC
FETCH FIRST 8 ROWS ONLY`,
};

// ─── Prompt système envoyé à l'API ───────────────────────────
// On demande à Claude de jouer le rôle d'un moteur Oracle
// et de retourner des données JSON réalistes pour la requête donnée.
const SYSTEM_PROMPT = `Tu es un moteur de base de données Oracle 19c.
On te donne une requête SQL qui s'exécute sur un Data Warehouse Yelp (schéma constellation).
Tu dois retourner UNIQUEMENT un tableau JSON valide représentant le résultat de cette requête.
Les données doivent être réalistes et cohérentes avec un vrai jeu de données Yelp
(villes US/Canada réelles, noms réalistes, valeurs plausibles).
Retourne UNIQUEMENT le JSON brut, sans balises markdown, sans texte autour.
Exemple de format attendu : [{"col1": val1, "col2": val2}, ...]`;

// ─── Appel API Anthropic ──────────────────────────────────────
async function runQuery(sql) {
  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "claude-sonnet-4-20250514",
      max_tokens: 1000,
      system: SYSTEM_PROMPT,
      messages: [{ role: "user", content: `Exécute cette requête SQL Oracle et retourne le résultat en JSON :\n\n${sql}` }],
    }),
  });
  const data = await res.json();
  const raw = data.content?.[0]?.text || "[]";
  try {
    return JSON.parse(raw.replace(/```json|```/g, "").trim());
  } catch {
    return [];
  }
}

// ─── Composants UI ────────────────────────────────────────────
const Tt = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div style={{ background: "#1a1e2e", border: `1px solid ${C.border}`, borderRadius: 8, padding: "10px 14px", fontSize: 12 }}>
      <p style={{ color: C.accent, marginBottom: 6, fontWeight: 700 }}>{label}</p>
      {payload.map((p, i) => (
        <p key={i} style={{ color: p.color || C.text, margin: "2px 0" }}>
          {p.name}: <strong>{typeof p.value === "number" ? p.value.toLocaleString("fr-FR") : p.value}</strong>
        </p>
      ))}
    </div>
  );
};

function Card({ title, children, style = {} }) {
  return (
    <div style={{ background: C.card, border: `1px solid ${C.border}`, borderRadius: 12, padding: "18px 22px", ...style }}>
      {title && <p style={{ color: C.muted, fontSize: 10, letterSpacing: "0.13em", textTransform: "uppercase", marginBottom: 14 }}>{title}</p>}
      {children}
    </div>
  );
}

function Badge({ color, label }) {
  return (
    <span style={{ background: color + "1a", color, border: `1px solid ${color}33`, borderRadius: 5, padding: "2px 8px", fontSize: 11, fontWeight: 600 }}>
      {label}
    </span>
  );
}

function SqlPanel({ sql, queryKey, onRun, loading, ran }) {
  return (
    <div style={{ background: "#0a0d18", border: `1px solid ${C.teal}30`, borderRadius: 8, padding: "12px 16px", marginBottom: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <code style={{ color: C.teal, fontSize: 10, letterSpacing: "0.08em" }}>ORACLE 19c · {queryKey}</code>
        <button onClick={onRun} disabled={loading} style={{
          background: loading ? C.muted + "30" : C.accent + "20",
          color: loading ? C.muted : C.accent,
          border: `1px solid ${loading ? C.muted + "30" : C.accent + "40"}`,
          borderRadius: 6, padding: "4px 14px", fontSize: 12, fontWeight: 700,
          cursor: loading ? "not-allowed" : "pointer", fontFamily: "inherit"
        }}>
          {loading ? "⏳ Exécution…" : ran ? "↺ Ré-exécuter" : "▶ Exécuter"}
        </button>
      </div>
      <pre style={{ color: "#8ab4f8", fontSize: 11, lineHeight: 1.7, margin: 0, overflowX: "auto", whiteSpace: "pre-wrap" }}>{sql.trim()}</pre>
    </div>
  );
}

function LoadingBar() {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "20px 0", color: C.muted, fontSize: 13 }}>
      <div style={{ width: 18, height: 18, border: `2px solid ${C.accent}`, borderTopColor: "transparent", borderRadius: "50%", animation: "spin 0.8s linear infinite" }} />
      Exécution de la requête sur Oracle…
    </div>
  );
}

function EmptyState({ onRun }) {
  return (
    <div style={{ textAlign: "center", padding: "32px 0", color: C.muted }}>
      <p style={{ fontSize: 28, marginBottom: 8 }}>⚡</p>
      <p style={{ fontSize: 13, marginBottom: 16 }}>Cliquez sur <strong style={{ color: C.accent }}>▶ Exécuter</strong> pour lancer la requête</p>
    </div>
  );
}

function DataTable({ data, colorMap = {} }) {
  if (!data?.length) return null;
  const cols = Object.keys(data[0]);
  return (
    <div style={{ overflowX: "auto" }}>
      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
        <thead>
          <tr>{cols.map(c => (
            <th key={c} style={{ color: C.muted, textAlign: "left", padding: "6px 10px", borderBottom: `1px solid ${C.border}`, fontWeight: 600, whiteSpace: "nowrap" }}>{c}</th>
          ))}</tr>
        </thead>
        <tbody>
          {data.map((row, i) => (
            <tr key={i} style={{ borderBottom: `1px solid ${C.border}` }}>
              {cols.map(c => (
                <td key={c} style={{ padding: "7px 10px", color: colorMap[c] || C.text, fontWeight: colorMap[c] ? 700 : 400 }}>
                  {typeof row[c] === "number" ? row[c].toLocaleString("fr-FR") : row[c] ?? "—"}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ─── Scénario 1 ───────────────────────────────────────────────
function Scenario1() {
  const [dataScore, setDataScore] = useState([]);
  const [dataElite, setDataElite] = useState([]);
  const [loading, setLoading] = useState({ score: false, elite: false });
  const [ran, setRan]           = useState({ score: false, elite: false });
  const [sel, setSel]           = useState(0);

  const runScore = useCallback(async () => {
    setLoading(l => ({ ...l, score: true }));
    const d = await runQuery(QUERIES.s1_score_global);
    setDataScore(d); setRan(r => ({ ...r, score: true }));
    setLoading(l => ({ ...l, score: false }));
  }, []);

  const runElite = useCallback(async () => {
    setLoading(l => ({ ...l, elite: true }));
    const d = await runQuery(QUERIES.s1_elite);
    setDataElite(d); setRan(r => ({ ...r, elite: true }));
    setLoading(l => ({ ...l, elite: false }));
  }, []);

  const u = dataScore[sel] || null;
  const breakdown = u ? [
    { name: "useful",          value: Number(u.useful) || 0,          color: C.green  },
    { name: "fans",            value: Number(u.fans) || 0,            color: C.teal   },
    { name: "RC × ★",         value: Math.round((Number(u.review_count)||0) * (Number(u.average_stars)||0)), color: C.blue },
    { name: "élite × 100",    value: (Number(u.nb_annees_elite)||0) * 100, color: C.purple },
    { name: "amis / 10",      value: Math.round((Number(u.friend_count)||0) / 10), color: C.accent },
  ] : [];

  return (
    <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
      <div style={{ gridColumn: "1 / 3" }}>
        <SqlPanel sql={QUERIES.s1_score_global} queryKey="MV_USER_SCORE_GLOBAL" onRun={runScore} loading={loading.score} ran={ran.score} />
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        {loading.score ? <LoadingBar /> : !dataScore.length ? <EmptyState onRun={runScore} /> : (
          <>
            <Card title="Résultats — score_pertinence (cliquez une ligne)">
              {dataScore.map((u, i) => (
                <div key={i} onClick={() => setSel(i)} style={{
                  display: "flex", alignItems: "center", gap: 12, padding: "9px 12px",
                  borderRadius: 8, cursor: "pointer", marginBottom: 4,
                  background: sel === i ? C.accent + "12" : "transparent",
                  border: `1px solid ${sel === i ? C.accent + "35" : "transparent"}`,
                  transition: "all .15s"
                }}>
                  <span style={{ color: i < 3 ? C.accent : C.muted, fontWeight: 800, width: 22 }}>#{i + 1}</span>
                  <div style={{ flex: 1 }}>
                    <p style={{ color: C.text, fontWeight: 600, fontSize: 13, marginBottom: 3 }}>{u.name}</p>
                    <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                      <Badge color={C.green}  label={`${Number(u.useful||0).toLocaleString("fr-FR")} useful`} />
                      <Badge color={C.blue}   label={`${u.review_count} reviews`} />
                      <Badge color={C.purple} label={`${u.nb_annees_elite} ans élite`} />
                    </div>
                  </div>
                  <div style={{ textAlign: "right" }}>
                    <p style={{ color: C.accent, fontWeight: 800, fontSize: 19 }}>{Number(u.score_pertinence||0).toLocaleString("fr-FR")}</p>
                    <p style={{ color: C.muted, fontSize: 10 }}>score_pertinence</p>
                  </div>
                </div>
              ))}
            </Card>

            <Card title="Comparaison visuelle — score_pertinence">
              <ResponsiveContainer width="100%" height={180}>
                <BarChart data={dataScore} barSize={26}>
                  <XAxis dataKey="name" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
                  <Tooltip content={<Tt />} />
                  <Bar dataKey="score_pertinence" name="score_pertinence" radius={[5, 5, 0, 0]}>
                    {dataScore.map((_, i) => <Cell key={i} fill={i === sel ? C.accent : C.blue + "70"} />)}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </Card>
          </>
        )}
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        {u && (
          <>
            <Card title={`Profil — ${u.name}`}>
              {[
                ["score_pertinence", Number(u.score_pertinence||0).toLocaleString("fr-FR"), C.accent],
                ["useful",           Number(u.useful||0).toLocaleString("fr-FR"), C.green],
                ["fans",             u.fans, C.teal],
                ["review_count",     u.review_count, C.blue],
                ["average_stars",    u.average_stars + " ★", C.accent],
                ["nb_annees_elite",  u.nb_annees_elite, C.purple],
                ["friend_count",     Number(u.friend_count||0).toLocaleString("fr-FR"), C.text],
              ].map(([k, v, c]) => (
                <div key={k} style={{ display: "flex", justifyContent: "space-between", padding: "5px 0", borderBottom: `1px solid ${C.border}` }}>
                  <code style={{ color: C.muted, fontSize: 11 }}>{k}</code>
                  <span style={{ color: c, fontWeight: 700, fontSize: 12 }}>{v}</span>
                </div>
              ))}
            </Card>

            <Card title="Décomposition score_pertinence">
              <ResponsiveContainer width="100%" height={140}>
                <PieChart>
                  <Pie data={breakdown} cx="50%" cy="50%" innerRadius={38} outerRadius={58} dataKey="value" stroke="none">
                    {breakdown.map((e, i) => <Cell key={i} fill={e.color} />)}
                  </Pie>
                  <Tooltip content={<Tt />} />
                </PieChart>
              </ResponsiveContainer>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 4, justifyContent: "center" }}>
                {breakdown.map(b => <Badge key={b.name} color={b.color} label={b.name} />)}
              </div>
            </Card>
          </>
        )}

        <SqlPanel sql={QUERIES.s1_elite} queryKey="MV_TOP_USER_ELITE × DIM_USER_ELITE" onRun={runElite} loading={loading.elite} ran={ran.elite} />
        {loading.elite ? <LoadingBar /> : dataElite.length > 0 && (
          <Card title="Statut élite — nbr_elite_years">
            {dataElite.map((u, i) => (
              <div key={i} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
                <span style={{ color: C.text, fontSize: 12, minWidth: 80 }}>{u.name}</span>
                <div style={{ flex: 1, background: C.border, borderRadius: 4, height: 6, overflow: "hidden" }}>
                  <div style={{ width: `${Math.min(100, (Number(u.nbr_elite_years)||0) * 12)}%`, height: "100%", background: C.purple, borderRadius: 4 }} />
                </div>
                <span style={{ color: C.purple, fontWeight: 700, fontSize: 12 }}>{u.nbr_elite_years} ans</span>
              </div>
            ))}
          </Card>
        )}
      </div>

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

// ─── Scénario 2 ───────────────────────────────────────────────
function Scenario2() {
  const [dataCity, setDataCity]   = useState([]);
  const [dataCat, setDataCat]     = useState([]);
  const [dataType, setDataType]   = useState([]);
  const [loading, setLoading]     = useState({ city: false, cat: false, type: false });
  const [ran, setRan]             = useState({ city: false, cat: false, type: false });

  const run = useCallback(async (key, query, setter) => {
    setLoading(l => ({ ...l, [key]: true }));
    const d = await runQuery(query);
    setter(d); setRan(r => ({ ...r, [key]: true }));
    setLoading(l => ({ ...l, [key]: false }));
  }, []);

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
      {/* MV_STARS_BY_CITY */}
      <div style={{ gridColumn: "1 / 3" }}>
        <SqlPanel sql={QUERIES.s2_by_city} queryKey="MV_STARS_BY_CITY" onRun={() => run("city", QUERIES.s2_by_city, setDataCity)} loading={loading.city} ran={ran.city} />
        {loading.city ? <LoadingBar /> : !dataCity.length ? <EmptyState onRun={() => run("city", QUERIES.s2_by_city, setDataCity)} /> : (
          <Card title="MV_STARS_BY_CITY — nb_business & avg_stars">
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={dataCity} barGap={4}>
                <XAxis dataKey="city" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis yAxisId="l" tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
                <YAxis yAxisId="r" orientation="right" domain={[3, 4.5]} tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
                <Tooltip content={<Tt />} />
                <Bar yAxisId="l" dataKey="nb_business" name="nb_business" fill={C.blue}   radius={[4,4,0,0]} barSize={24} />
                <Bar yAxisId="r" dataKey="avg_stars"   name="avg_stars"   fill={C.accent} radius={[4,4,0,0]} barSize={24} />
              </BarChart>
            </ResponsiveContainer>
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              <Badge color={C.blue}   label="nb_business (axe G)" />
              <Badge color={C.accent} label="avg_stars (axe D)" />
            </div>
          </Card>
        )}
      </div>

      {/* MV_STARS_BY_CATEGORIE */}
      <div>
        <SqlPanel sql={QUERIES.s2_by_categorie} queryKey="MV_STARS_BY_CATEGORIE" onRun={() => run("cat", QUERIES.s2_by_categorie, setDataCat)} loading={loading.cat} ran={ran.cat} />
        {loading.cat ? <LoadingBar /> : !dataCat.length ? <EmptyState onRun={() => run("cat", QUERIES.s2_by_categorie, setDataCat)} /> : (
          <Card title="MV_STARS_BY_CATEGORIE — avg_stars (DIM_CATEGORIE)">
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={dataCat} layout="vertical" barSize={14}>
                <XAxis type="number" domain={[3, 4.5]} tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
                <YAxis type="category" dataKey="categorie_name" tick={{ fill: C.text, fontSize: 11 }} axisLine={false} tickLine={false} width={100} />
                <Tooltip content={<Tt />} />
                <Bar dataKey="avg_stars" name="avg_stars" radius={[0, 5, 5, 0]}>
                  {dataCat.map((e, i) => <Cell key={i} fill={Number(e.avg_stars) >= 4 ? C.green : Number(e.avg_stars) >= 3.7 ? C.accent : C.red} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </Card>
        )}
      </div>

      {/* MV_STARS_BY_TYPE */}
      <div>
        <SqlPanel sql={QUERIES.s2_by_type} queryKey="MV_STARS_BY_TYPE" onRun={() => run("type", QUERIES.s2_by_type, setDataType)} loading={loading.type} ran={ran.type} />
        {loading.type ? <LoadingBar /> : !dataType.length ? <EmptyState onRun={() => run("type", QUERIES.s2_by_type, setDataType)} /> : (
          <Card title="MV_STARS_BY_TYPE — nb_business (DIM_TYPE_BUSINESS)">
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={dataType} barGap={3}>
                <XAxis dataKey="type_name" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
                <Tooltip content={<Tt />} />
                <Bar dataKey="nb_business"   name="nb_business"   fill={C.purple}      radius={[4,4,0,0]} barSize={20} />
                <Bar dataKey="total_reviews" name="total_reviews" fill={C.teal + "90"} radius={[4,4,0,0]} barSize={20} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        )}
      </div>

      {/* Table résultats catégories */}
      {dataCat.length > 0 && (
        <Card title="Tableau — MV_STARS_BY_CATEGORIE" style={{ gridColumn: "1 / 3" }}>
          <DataTable data={dataCat} colorMap={{ avg_stars: C.accent, nb_business: C.blue, total_reviews: C.muted }} />
        </Card>
      )}

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

// ─── Scénario 3 ───────────────────────────────────────────────
function Scenario3() {
  const [dataEvol, setDataEvol]   = useState([]);
  const [dataLag,  setDataLag]    = useState([]);
  const [loading, setLoading]     = useState({ evol: false, lag: false });
  const [ran, setRan]             = useState({ evol: false, lag: false });

  const run = useCallback(async (key, query, setter) => {
    setLoading(l => ({ ...l, [key]: true }));
    const d = await runQuery(query);
    setter(d); setRan(r => ({ ...r, [key]: true }));
    setLoading(l => ({ ...l, [key]: false }));
  }, []);

  // Pivot : transformer [{city, annee, avg_stars}] en [{annee, City1: x, City2: y}]
  const pivot = (rows) => {
    const cities = [...new Set(rows.map(r => r.city))];
    const years  = [...new Set(rows.map(r => r.annee))].sort();
    return years.map(yr => {
      const obj = { annee: yr };
      cities.forEach(c => {
        const row = rows.find(r => r.city === c && r.annee == yr);
        obj[c] = row ? Number(row.avg_stars) : null;
      });
      return obj;
    });
  };

  const CITY_COLORS = [C.accent, C.red, C.green, C.blue, C.purple, C.teal];
  const cities = [...new Set(dataEvol.map(r => r.city))];
  const pivoted = pivot(dataEvol);

  // Alertes depuis dataLag
  const alertes = dataLag.filter(r => r.delta_stars !== null && Number(r.delta_stars) < -0.3);

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
      <div style={{ gridColumn: "1 / 3" }}>
        <SqlPanel sql={QUERIES.s3_evolution} queryKey="MV_STARS_EVOLUTION" onRun={() => run("evol", QUERIES.s3_evolution, setDataEvol)} loading={loading.evol} ran={ran.evol} />
      </div>

      {loading.evol ? <LoadingBar /> : !dataEvol.length ? <EmptyState onRun={() => run("evol", QUERIES.s3_evolution, setDataEvol)} /> : (
        <Card title="MV_STARS_EVOLUTION — avg_stars par ville × annee" style={{ gridColumn: "1 / 3" }}>
          <ResponsiveContainer width="100%" height={230}>
            <LineChart data={pivoted}>
              <XAxis dataKey="annee" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis domain={["auto", "auto"]} tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
              <Tooltip content={<Tt />} />
              {cities.map((c, i) => (
                <Line key={c} type="monotone" dataKey={c} name={c}
                  stroke={CITY_COLORS[i % CITY_COLORS.length]} strokeWidth={2.5}
                  dot={{ r: 4 }} activeDot={{ r: 6 }} connectNulls />
              ))}
            </LineChart>
          </ResponsiveContainer>
          <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
            {cities.map((c, i) => <Badge key={c} color={CITY_COLORS[i % CITY_COLORS.length]} label={c} />)}
          </div>
        </Card>
      )}

      <div style={{ gridColumn: "1 / 3" }}>
        <SqlPanel sql={QUERIES.s3_lag} queryKey="LAG() sur MV_STARS_EVOLUTION" onRun={() => run("lag", QUERIES.s3_lag, setDataLag)} loading={loading.lag} ran={ran.lag} />
      </div>

      {loading.lag ? <LoadingBar /> : dataLag.length > 0 && (
        <>
          <Card title="Résultat brut — delta_stars par ville × annee">
            <DataTable data={dataLag.slice(0, 12)} colorMap={{
              delta_stars: C.red, avg_stars: C.accent, nb_reviews: C.blue, total_useful: C.green
            }} />
          </Card>

          <Card title="Alertes signaux faibles (delta_stars < -0.3)">
            {alertes.length === 0 ? (
              <p style={{ color: C.green, fontSize: 13 }}>✅ Aucune dégradation détectée</p>
            ) : alertes.map((a, i) => (
              <div key={i} style={{
                display: "flex", alignItems: "center", gap: 12, padding: "10px 14px",
                background: (Number(a.delta_stars) < -0.6 ? C.red : C.accent) + "10",
                border: `1px solid ${(Number(a.delta_stars) < -0.6 ? C.red : C.accent)}25`,
                borderRadius: 8, marginBottom: 8
              }}>
                <span style={{ fontSize: 18 }}>{Number(a.delta_stars) < -0.6 ? "🚨" : "⚠️"}</span>
                <div style={{ flex: 1 }}>
                  <p style={{ color: C.text, fontWeight: 600, fontSize: 13 }}>{a.city} — {a.annee}</p>
                  <p style={{ color: C.muted, fontSize: 11 }}>avg_stars : {a.avg_stars} · Δ {a.delta_stars}</p>
                </div>
                <Badge color={Number(a.delta_stars) < -0.6 ? C.red : C.accent} label={Number(a.delta_stars) < -0.6 ? "Critique" : "Surveillance"} />
              </div>
            ))}
          </Card>
        </>
      )}

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

// ─── Scénario 4 ───────────────────────────────────────────────
function Scenario4() {
  const [dataTop,   setDataTop]   = useState([]);
  const [dataVotes, setDataVotes] = useState([]);
  const [dataElite, setDataElite] = useState([]);
  const [loading, setLoading]     = useState({ top: false, votes: false, elite: false });
  const [ran, setRan]             = useState({ top: false, votes: false, elite: false });

  const run = useCallback(async (key, query, setter) => {
    setLoading(l => ({ ...l, [key]: true }));
    const d = await runQuery(query);
    setter(d); setRan(r => ({ ...r, [key]: true }));
    setLoading(l => ({ ...l, [key]: false }));
  }, []);

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
      {/* MV_BUSINESS_TOP_USERS */}
      <div style={{ gridColumn: "1 / 3" }}>
        <SqlPanel sql={QUERIES.s4_top_users} queryKey="MV_BUSINESS_TOP_USERS" onRun={() => run("top", QUERIES.s4_top_users, setDataTop)} loading={loading.top} ran={ran.top} />
        {loading.top ? <LoadingBar /> : !dataTop.length ? <EmptyState onRun={() => run("top", QUERIES.s4_top_users, setDataTop)} /> : (
          <Card title="MV_BUSINESS_TOP_USERS — users utiles (useful > 100) par type × ville">
            <DataTable data={dataTop} colorMap={{ total_useful: C.green, avg_stars_reviews: C.accent, nb_users_distincts: C.blue }} />
          </Card>
        )}
      </div>

      {/* FAIT_USER : useful / funny / cool */}
      <div>
        <SqlPanel sql={QUERIES.s4_votes} queryKey="FAIT_USER (useful / funny / cool)" onRun={() => run("votes", QUERIES.s4_votes, setDataVotes)} loading={loading.votes} ran={ran.votes} />
        {loading.votes ? <LoadingBar /> : !dataVotes.length ? <EmptyState onRun={() => run("votes", QUERIES.s4_votes, setDataVotes)} /> : (
          <Card title="FAIT_USER — votes useful / funny / cool (top users)">
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={dataVotes} barGap={3}>
                <XAxis dataKey="name" tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
                <Tooltip content={<Tt />} />
                <Bar dataKey="useful" name="useful" fill={C.green}  radius={[3,3,0,0]} />
                <Bar dataKey="funny"  name="funny"  fill={C.accent} radius={[3,3,0,0]} />
                <Bar dataKey="cool"   name="cool"   fill={C.blue}   radius={[3,3,0,0]} />
              </BarChart>
            </ResponsiveContainer>
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              <Badge color={C.green}  label="useful" />
              <Badge color={C.accent} label="funny" />
              <Badge color={C.blue}   label="cool" />
            </div>
          </Card>
        )}
      </div>

      {/* MV_ELITE_USERS_BY_BUSINESS */}
      <div>
        <SqlPanel sql={QUERIES.s4_elite_by_biz} queryKey="MV_ELITE_USERS_BY_BUSINESS" onRun={() => run("elite", QUERIES.s4_elite_by_biz, setDataElite)} loading={loading.elite} ran={ran.elite} />
        {loading.elite ? <LoadingBar /> : !dataElite.length ? <EmptyState onRun={() => run("elite", QUERIES.s4_elite_by_biz, setDataElite)} /> : (
          <Card title="MV_ELITE_USERS_BY_BUSINESS — users élite par type">
            {dataElite.map((e, i) => (
              <div key={i} style={{ marginBottom: 10 }}>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                  <span style={{ color: C.text, fontSize: 12 }}>{e.type_name} — <span style={{ color: C.muted }}>{e.city}</span></span>
                  <span style={{ color: C.accent, fontWeight: 700, fontSize: 12 }}>★ {e.avg_stars} · {e.nb_users_elite} élite</span>
                </div>
                <div style={{ background: C.border, borderRadius: 4, height: 6, overflow: "hidden" }}>
                  <div style={{ width: `${Math.min(100, (Number(e.nb_users_elite)||0) / 10)}%`, height: "100%", background: C.purple, borderRadius: 4 }} />
                </div>
              </div>
            ))}
          </Card>
        )}
      </div>

      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

// ─── App ──────────────────────────────────────────────────────
const TABS = [
  { label: "① Contributeurs",           comp: <Scenario1 /> },
  { label: "② Opportunités",            comp: <Scenario2 /> },
  { label: "③ Signaux faibles",         comp: <Scenario3 /> },
  { label: "④ Émotions / Satisfaction", comp: <Scenario4 /> },
];

const DESCS = [
  "MV_USER_SCORE_GLOBAL — score_pertinence = useful + fans + (review_count × avg_stars) + (nb_annees_elite × 100) + (friend_count / 10) + DIM_USER_ELITE",
  "MV_STARS_BY_CITY · MV_STARS_BY_CATEGORIE (DIM_CATEGORIE) · MV_STARS_BY_TYPE (DIM_TYPE_BUSINESS) — nb_business, avg_stars, total_reviews",
  "MV_STARS_EVOLUTION (FAIT_REVIEW × DIM_LOCALISATION × DIM_TEMPS) — avg_stars × annee × city + détection LAG()",
  "MV_BUSINESS_TOP_USERS (useful > 100) · FAIT_USER.useful/funny/cool · MV_ELITE_USERS_BY_BUSINESS (nb_annees_elite > 0)",
];

export default function App() {
  const [tab, setTab] = useState(0);
  return (
    <div style={{ minHeight: "100vh", background: C.bg, color: C.text, fontFamily: "'DM Mono','IBM Plex Mono','Fira Code',monospace", paddingBottom: 48 }}>
      <div style={{
        background: C.surface, borderBottom: `1px solid ${C.border}`,
        padding: "0 28px", display: "flex", alignItems: "center", justifyContent: "space-between",
        height: 56, position: "sticky", top: 0, zIndex: 100
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <div style={{ width: 26, height: 26, background: C.accent, borderRadius: 5, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 13 }}>★</div>
          <span style={{ fontWeight: 800, fontSize: 14, letterSpacing: "0.08em" }}>YELP DW</span>
          <span style={{ color: C.border }}>│</span>
          <span style={{ color: C.muted, fontSize: 11 }}>Oracle 19c · Schéma constellation · Master 2 BDIA</span>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Badge color={C.green}  label="DM01 · FAIT_BUSINESS" />
          <Badge color={C.purple} label="DM02 · FAIT_USER" />
          <Badge color={C.teal}   label="FAIT_REVIEW bridge" />
        </div>
      </div>

      <div style={{ background: C.surface, borderBottom: `1px solid ${C.border}`, padding: "0 28px", display: "flex", gap: 4 }}>
        {TABS.map((t, i) => (
          <button key={i} onClick={() => setTab(i)} style={{
            padding: "12px 18px", border: "none", cursor: "pointer", background: "transparent",
            borderBottom: `2px solid ${tab === i ? C.accent : "transparent"}`,
            color: tab === i ? C.accent : C.muted, fontWeight: tab === i ? 700 : 400,
            fontSize: 12, letterSpacing: "0.04em", transition: "all .15s", fontFamily: "inherit"
          }}>{t.label}</button>
        ))}
      </div>

      <div style={{ padding: "12px 28px 0" }}>
        <p style={{ color: C.muted, fontSize: 11, background: C.surface, border: `1px solid ${C.border}`, borderRadius: 7, padding: "8px 14px", lineHeight: 1.6 }}>
          <span style={{ color: C.accent, fontWeight: 700 }}>Scénario {tab + 1} — </span>{DESCS[tab]}
        </p>
      </div>

      <div style={{ padding: "16px 28px 0" }}>
        {TABS[tab].comp}
      </div>
    </div>
  );
}