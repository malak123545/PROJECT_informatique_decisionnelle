import { useState } from "react";
import {
  BarChart, Bar, LineChart, Line, XAxis, YAxis, Tooltip,
  ResponsiveContainer, RadarChart, Radar, PolarGrid, PolarAngleAxis,
  ScatterChart, Scatter, ZAxis, Cell, PieChart, Pie
} from "recharts";

// ─── Palette ──────────────────────────────────────────────────
const C = {
  bg: "#080b12", surface: "#0f1320", card: "#141927",
  border: "#1c2235", accent: "#f5a623", accentDim: "#b87a1a",
  blue: "#4f8ef7", green: "#3ecf8e", red: "#f74f6f",
  purple: "#a78bfa", teal: "#2dd4bf", text: "#dde2ef", muted: "#5a637a",
};

// ─── Données mock ALIGNÉES sur le vrai schéma Oracle ─────────
//
// Scénario 1 — MV_USER_SCORE_GLOBAL
// Colonnes : user_id, name, friend_count, review_count, average_stars,
//            useful, nb_annees_elite, fans,
//            score_pertinence = useful + fans + (review_count*average_stars)
//                               + (nb_annees_elite*100) + (friend_count/10)
const MV_USER_SCORE_GLOBAL = [
  { name: "Jessica M.", friend_count: 1240, review_count: 842, average_stars: 4.1, useful: 4100, fans: 210, nb_annees_elite: 7,
    score_pertinence: 4100 + 210 + Math.round(842*4.1) + 7*100 + Math.round(1240/10) },
  { name: "Carlos V.",  friend_count: 980,  review_count: 721, average_stars: 3.9, useful: 3800, fans: 185, nb_annees_elite: 6,
    score_pertinence: 3800 + 185 + Math.round(721*3.9) + 6*100 + Math.round(980/10)  },
  { name: "Mei L.",     friend_count: 870,  review_count: 633, average_stars: 4.3, useful: 3200, fans: 150, nb_annees_elite: 5,
    score_pertinence: 3200 + 150 + Math.round(633*4.3) + 5*100 + Math.round(870/10)  },
  { name: "Tom R.",     friend_count: 640,  review_count: 590, average_stars: 3.7, useful: 2900, fans: 98,  nb_annees_elite: 4,
    score_pertinence: 2900 + 98  + Math.round(590*3.7) + 4*100 + Math.round(640/10)  },
  { name: "Aisha K.",   friend_count: 520,  review_count: 510, average_stars: 4.0, useful: 2600, fans: 82,  nb_annees_elite: 3,
    score_pertinence: 2600 + 82  + Math.round(510*4.0) + 3*100 + Math.round(520/10)  },
  { name: "Pierre D.",  friend_count: 410,  review_count: 440, average_stars: 3.8, useful: 2100, fans: 61,  nb_annees_elite: 2,
    score_pertinence: 2100 + 61  + Math.round(440*3.8) + 2*100 + Math.round(410/10)  },
];

// MV_TOP_USER_ELITE — colonnes : name, nb_annees_elite, avg_stars, review_count, useful, elite_2022/23/24
const MV_TOP_USER_ELITE = [
  { name: "Jessica M.", nb_annees_elite: 7, average_stars: 4.1, review_count: 842, useful: 4100, elite_2022: 1, elite_2023: 1, elite_2024: 1 },
  { name: "Carlos V.",  nb_annees_elite: 6, average_stars: 3.9, review_count: 721, useful: 3800, elite_2022: 1, elite_2023: 1, elite_2024: 1 },
  { name: "Mei L.",     nb_annees_elite: 5, average_stars: 4.3, review_count: 633, useful: 3200, elite_2022: 0, elite_2023: 1, elite_2024: 1 },
  { name: "Tom R.",     nb_annees_elite: 4, average_stars: 3.7, review_count: 590, useful: 2900, elite_2022: 0, elite_2023: 1, elite_2024: 1 },
];

// Décomposition du score_pertinence pour le pie
const scoreBreakdown = (u) => [
  { name: "Votes utiles",   value: u.useful,                              color: C.green   },
  { name: "Fans",           value: u.fans,                                color: C.teal    },
  { name: "RC × ★ moy.",   value: Math.round(u.review_count * u.average_stars), color: C.blue  },
  { name: "Élite × 100",   value: u.nb_annees_elite * 100,               color: C.purple  },
  { name: "Amis / 10",     value: Math.round(u.friend_count / 10),       color: C.accent  },
];

// Scénario 2 — MV_STARS_BY_CITY / MV_STARS_BY_CATEGORIE / MV_STARS_BY_TYPE
const MV_STARS_BY_CITY = [
  { city: "Las Vegas",  state: "NV", nb_business: 6312, avg_stars: 3.72, max_stars: 5, min_stars: 1, total_reviews: 482000 },
  { city: "Phoenix",    state: "AZ", nb_business: 5820, avg_stars: 3.68, max_stars: 5, min_stars: 1, total_reviews: 410000 },
  { city: "Toronto",    state: "ON", nb_business: 4910, avg_stars: 3.81, max_stars: 5, min_stars: 1, total_reviews: 352000 },
  { city: "Charlotte",  state: "NC", nb_business: 3740, avg_stars: 3.75, max_stars: 5, min_stars: 1, total_reviews: 218000 },
  { city: "Pittsburgh", state: "PA", nb_business: 2980, avg_stars: 3.63, max_stars: 5, min_stars: 1, total_reviews: 195000 },
  { city: "Cleveland",  state: "OH", nb_business: 2540, avg_stars: 3.58, max_stars: 5, min_stars: 1, total_reviews: 162000 },
];

const MV_STARS_BY_CATEGORIE = [
  { categorie_name: "Restaurants",  nb_business: 8420, avg_stars: 3.71, total_reviews: 920000 },
  { categorie_name: "Bars",         nb_business: 3210, avg_stars: 3.64, total_reviews: 410000 },
  { categorie_name: "Beauty",       nb_business: 4100, avg_stars: 4.02, total_reviews: 280000 },
  { categorie_name: "Auto Repair",  nb_business: 2860, avg_stars: 3.45, total_reviews: 190000 },
  { categorie_name: "Health",       nb_business: 3500, avg_stars: 4.10, total_reviews: 240000 },
  { categorie_name: "Shopping",     nb_business: 5100, avg_stars: 3.78, total_reviews: 340000 },
];

const MV_STARS_BY_TYPE = [
  { type_name: "Food",          nb_business: 11200, avg_stars: 3.68, total_reviews: 1320000 },
  { type_name: "Services",      nb_business: 6800,  avg_stars: 3.82, total_reviews: 680000  },
  { type_name: "Health",        nb_business: 4200,  avg_stars: 4.08, total_reviews: 420000  },
  { type_name: "Entertainment", nb_business: 2900,  avg_stars: 3.74, total_reviews: 310000  },
  { type_name: "Retail",        nb_business: 5100,  avg_stars: 3.71, total_reviews: 390000  },
];

// Scénario 3 — MV_STARS_EVOLUTION
// Colonnes : city, state, annee, nb_reviews, avg_stars, total_useful
const MV_STARS_EVOLUTION = [
  { annee: 2019, "Las Vegas": 3.82, "Phoenix": 3.74, "Toronto": 3.91, "Pittsburgh": 3.65 },
  { annee: 2020, "Las Vegas": 3.71, "Phoenix": 3.68, "Toronto": 3.88, "Pittsburgh": 3.58 },
  { annee: 2021, "Las Vegas": 3.65, "Phoenix": 3.62, "Toronto": 3.84, "Pittsburgh": 3.48 },
  { annee: 2022, "Las Vegas": 3.70, "Phoenix": 3.59, "Toronto": 3.86, "Pittsburgh": 3.41 },
  { annee: 2023, "Las Vegas": 3.72, "Phoenix": 3.55, "Toronto": 3.89, "Pittsburgh": 3.35 },
  { annee: 2024, "Las Vegas": 3.68, "Phoenix": 3.51, "Toronto": 3.92, "Pittsburgh": 3.28 },
];

const MV_STARS_EVOLUTION_AGG = [
  { annee: 2019, nb_reviews: 82000,  total_useful: 41000 },
  { annee: 2020, nb_reviews: 95000,  total_useful: 49000 },
  { annee: 2021, nb_reviews: 108000, total_useful: 58000 },
  { annee: 2022, nb_reviews: 124000, total_useful: 67000 },
  { annee: 2023, nb_reviews: 138000, total_useful: 78000 },
  { annee: 2024, nb_reviews: 151000, total_useful: 88000 },
];

// Scénario 4 — MV_BUSINESS_TOP_USERS + FAIT_USER (useful/funny/cool)
// MV_BUSINESS_TOP_USERS : type_name, city, nb_users_distincts, avg_stars_reviews, total_useful
const MV_BUSINESS_TOP_USERS = [
  { type_name: "Food",          city: "Las Vegas",  nb_users_distincts: 3200, avg_stars_reviews: 3.82, total_useful: 58000 },
  { type_name: "Food",          city: "Phoenix",    nb_users_distincts: 2800, avg_stars_reviews: 3.71, total_useful: 49000 },
  { type_name: "Services",      city: "Toronto",    nb_users_distincts: 1900, avg_stars_reviews: 3.94, total_useful: 34000 },
  { type_name: "Health",        city: "Charlotte",  nb_users_distincts: 1500, avg_stars_reviews: 4.12, total_useful: 28000 },
  { type_name: "Entertainment", city: "Las Vegas",  nb_users_distincts: 2100, avg_stars_reviews: 3.68, total_useful: 38000 },
  { type_name: "Retail",        city: "Toronto",    nb_users_distincts: 1200, avg_stars_reviews: 3.75, total_useful: 22000 },
];

// Votes FAIT_USER : useful / funny / cool (colonnes directes)
const FAIT_USER_VOTES = [
  { type_name: "Food",          useful: 142000, funny: 48000, cool: 89000 },
  { type_name: "Services",      useful: 98000,  funny: 21000, cool: 45000 },
  { type_name: "Health",        useful: 87000,  funny: 9000,  cool: 18000 },
  { type_name: "Entertainment", useful: 61000,  funny: 72000, cool: 95000 },
  { type_name: "Retail",        useful: 54000,  funny: 18000, cool: 36000 },
];

// MV_ELITE_USERS_BY_BUSINESS : type_name, city, nb_users_elite, avg_stars, avg_annees_elite
const MV_ELITE_USERS_BY_BUSINESS = [
  { type_name: "Food",          nb_users_elite: 820, avg_stars: 3.91, avg_annees_elite: 3.4 },
  { type_name: "Health",        nb_users_elite: 610, avg_stars: 4.18, avg_annees_elite: 4.1 },
  { type_name: "Services",      nb_users_elite: 540, avg_stars: 3.88, avg_annees_elite: 2.9 },
  { type_name: "Retail",        nb_users_elite: 380, avg_stars: 3.75, avg_annees_elite: 2.5 },
  { type_name: "Entertainment", nb_users_elite: 490, avg_stars: 3.68, avg_annees_elite: 3.0 },
];

// ─── Composants UI ────────────────────────────────────────────
const T = ({ active, payload, label }) => {
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

function Card({ title, sub, children, style = {} }) {
  return (
    <div style={{ background: C.card, border: `1px solid ${C.border}`, borderRadius: 12, padding: "18px 22px", ...style }}>
      {title && <p style={{ color: C.muted, fontSize: 10, letterSpacing: "0.13em", textTransform: "uppercase", marginBottom: sub ? 2 : 14 }}>{title}</p>}
      {sub && <p style={{ color: C.muted, fontSize: 11, marginBottom: 14, fontStyle: "italic" }}>{sub}</p>}
      {children}
    </div>
  );
}

function Kpi({ label, value, color = C.accent, sub }) {
  return (
    <div style={{ background: C.card, border: `1px solid ${C.border}`, borderRadius: 10, padding: "14px 18px" }}>
      <p style={{ color: C.muted, fontSize: 10, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 6 }}>{label}</p>
      <p style={{ color, fontSize: 26, fontWeight: 800, lineHeight: 1 }}>{value}</p>
      {sub && <p style={{ color: C.muted, fontSize: 11, marginTop: 4 }}>{sub}</p>}
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

function SqlChip({ sql }) {
  return (
    <div style={{ background: "#0a0d18", border: `1px solid ${C.teal}30`, borderRadius: 6, padding: "6px 12px", marginBottom: 14, gridColumn: "1 / -1" }}>
      <p style={{ color: C.muted, fontSize: 10, letterSpacing: "0.1em", marginBottom: 2 }}>VUES / TABLES ORACLE UTILISÉES</p>
      <code style={{ color: C.teal, fontSize: 11 }}>{sql}</code>
    </div>
  );
}

// ── Scénario 1 : Récompenser les users contributeurs ──────────
function Scenario1() {
  const [sel, setSel] = useState(0);
  const u = MV_USER_SCORE_GLOBAL[sel];
  const breakdown = scoreBreakdown(u);

  return (
    <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 16 }}>
      <SqlChip sql="MV_USER_SCORE_GLOBAL  ·  MV_TOP_USER_ELITE  ·  MV_TOP_USER_QUALITE" />

      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <Card title="Classement — score_pertinence" sub="useful + fans + (review_count × average_stars) + (nb_annees_elite × 100) + (friend_count / 10)">
          {MV_USER_SCORE_GLOBAL.map((u, i) => (
            <div key={i} onClick={() => setSel(i)} style={{
              display: "flex", alignItems: "center", gap: 12, padding: "10px 12px",
              borderRadius: 8, cursor: "pointer", marginBottom: 4,
              background: sel === i ? C.accent + "12" : "transparent",
              border: `1px solid ${sel === i ? C.accent + "35" : "transparent"}`,
              transition: "all .15s"
            }}>
              <span style={{ color: i < 3 ? C.accent : C.muted, fontWeight: 800, fontSize: 15, width: 22 }}>#{i + 1}</span>
              <div style={{ flex: 1 }}>
                <p style={{ color: C.text, fontWeight: 600, fontSize: 13, marginBottom: 4 }}>{u.name}</p>
                <div style={{ display: "flex", gap: 5, flexWrap: "wrap" }}>
                  <Badge color={C.green}  label={`${u.useful.toLocaleString("fr-FR")} useful`} />
                  <Badge color={C.blue}   label={`${u.review_count} reviews`} />
                  <Badge color={C.purple} label={`${u.nb_annees_elite} ans élite`} />
                  <Badge color={C.teal}   label={`${u.friend_count} amis`} />
                  <Badge color={C.accent} label={`${u.fans} fans`} />
                </div>
              </div>
              <div style={{ textAlign: "right" }}>
                <p style={{ color: C.accent, fontWeight: 800, fontSize: 20 }}>{u.score_pertinence.toLocaleString("fr-FR")}</p>
                <p style={{ color: C.muted, fontSize: 10 }}>score_pertinence</p>
              </div>
            </div>
          ))}
        </Card>

        <Card title="Comparaison score_pertinence — MV_USER_SCORE_GLOBAL">
          <ResponsiveContainer width="100%" height={180}>
            <BarChart data={MV_USER_SCORE_GLOBAL} barSize={28}>
              <XAxis dataKey="name" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
              <Tooltip content={<T />} />
              <Bar dataKey="score_pertinence" name="score_pertinence" radius={[5, 5, 0, 0]}>
                {MV_USER_SCORE_GLOBAL.map((_, i) => <Cell key={i} fill={i === sel ? C.accent : C.blue + "70"} />)}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </Card>
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <Card title={`Profil sélectionné — ${u.name}`}>
          {[
            ["score_pertinence", u.score_pertinence.toLocaleString("fr-FR"), C.accent],
            ["useful",           u.useful.toLocaleString("fr-FR"), C.green],
            ["fans",             u.fans, C.teal],
            ["review_count",     u.review_count, C.blue],
            ["average_stars",    u.average_stars + " ★", C.accent],
            ["nb_annees_elite",  u.nb_annees_elite, C.purple],
            ["friend_count",     u.friend_count.toLocaleString("fr-FR"), C.text],
          ].map(([k, v, c]) => (
            <div key={k} style={{ display: "flex", justifyContent: "space-between", padding: "6px 0", borderBottom: `1px solid ${C.border}` }}>
              <code style={{ color: C.muted, fontSize: 11 }}>{k}</code>
              <span style={{ color: c, fontWeight: 700, fontSize: 12 }}>{v}</span>
            </div>
          ))}
        </Card>

        <Card title="Décomposition score_pertinence">
          <ResponsiveContainer width="100%" height={150}>
            <PieChart>
              <Pie data={breakdown} cx="50%" cy="50%" innerRadius={40} outerRadius={62} dataKey="value" stroke="none">
                {breakdown.map((e, i) => <Cell key={i} fill={e.color} />)}
              </Pie>
              <Tooltip content={<T />} />
            </PieChart>
          </ResponsiveContainer>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 4, justifyContent: "center" }}>
            {breakdown.map(b => <Badge key={b.name} color={b.color} label={b.name} />)}
          </div>
        </Card>

        <Card title="DIM_USER_ELITE — statut élite 2022→2024">
          {MV_TOP_USER_ELITE.map((u, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 7 }}>
              <span style={{ color: C.text, fontSize: 12, width: 78 }}>{u.name}</span>
              {[["22", u.elite_2022], ["23", u.elite_2023], ["24", u.elite_2024]].map(([yr, v]) => (
                <span key={yr} style={{
                  background: v ? C.purple + "30" : C.border, color: v ? C.purple : C.muted,
                  borderRadius: 4, padding: "1px 6px", fontSize: 10, fontWeight: 700
                }}>{yr}</span>
              ))}
              <span style={{ color: C.accent, fontSize: 12, fontWeight: 700, marginLeft: "auto" }}>{u.nb_annees_elite} ans</span>
            </div>
          ))}
        </Card>
      </div>
    </div>
  );
}

// ── Scénario 2 : Cartographie opportunités commerciales ───────
function Scenario2() {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
      <SqlChip sql="MV_STARS_BY_CITY  ·  MV_STARS_BY_CATEGORIE  ·  MV_STARS_BY_TYPE" />

      <Card title="MV_STARS_BY_CITY — nb_business & avg_stars par ville" style={{ gridColumn: "1 / 3" }}>
        <ResponsiveContainer width="100%" height={210}>
          <BarChart data={MV_STARS_BY_CITY} barGap={5}>
            <XAxis dataKey="city" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis yAxisId="left"  tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
            <YAxis yAxisId="right" orientation="right" domain={[3.4, 4.0]} tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
            <Tooltip content={<T />} />
            <Bar yAxisId="left"  dataKey="nb_business" name="nb_business"   fill={C.blue}   radius={[4,4,0,0]} barSize={26} />
            <Bar yAxisId="right" dataKey="avg_stars"   name="avg_stars"     fill={C.accent} radius={[4,4,0,0]} barSize={26} />
          </BarChart>
        </ResponsiveContainer>
        <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
          <Badge color={C.blue}   label="nb_business (axe G)" />
          <Badge color={C.accent} label="avg_stars (axe D)" />
          <Badge color={C.muted}  label="total_reviews dans le tooltip" />
        </div>
      </Card>

      <Card title="MV_STARS_BY_CATEGORIE — avg_stars par catégorie (DIM_CATEGORIE)">
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={MV_STARS_BY_CATEGORIE} layout="vertical" barSize={16}>
            <XAxis type="number" domain={[3, 4.5]} tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
            <YAxis type="category" dataKey="categorie_name" tick={{ fill: C.text, fontSize: 11 }} axisLine={false} tickLine={false} width={90} />
            <Tooltip content={<T />} />
            <Bar dataKey="avg_stars" name="avg_stars" radius={[0, 5, 5, 0]}>
              {MV_STARS_BY_CATEGORIE.map((e, i) => <Cell key={i} fill={e.avg_stars >= 4 ? C.green : e.avg_stars >= 3.7 ? C.accent : C.red} />)}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </Card>

      <Card title="MV_STARS_BY_TYPE — nb_business & total_reviews (DIM_TYPE_BUSINESS)">
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={MV_STARS_BY_TYPE} barGap={4}>
            <XAxis dataKey="type_name" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
            <Tooltip content={<T />} />
            <Bar dataKey="nb_business"   name="nb_business"   fill={C.purple} radius={[4,4,0,0]} barSize={22} />
            <Bar dataKey="total_reviews" name="total_reviews" fill={C.teal+"80"} radius={[4,4,0,0]} barSize={22} />
          </BarChart>
        </ResponsiveContainer>
      </Card>

      <Card title="Tableau de synthèse — opportunités" style={{ gridColumn: "1 / 3" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
          <thead>
            <tr>{["categorie_name", "nb_business", "avg_stars", "total_reviews", "Signal"].map(h => (
              <th key={h} style={{ color: C.muted, textAlign: "left", padding: "6px 12px", borderBottom: `1px solid ${C.border}`, fontWeight: 600 }}>{h}</th>
            ))}</tr>
          </thead>
          <tbody>
            {MV_STARS_BY_CATEGORIE.map((r, i) => (
              <tr key={i} style={{ borderBottom: `1px solid ${C.border}` }}>
                <td style={{ color: C.text,   padding: "8px 12px" }}>{r.categorie_name}</td>
                <td style={{ color: C.blue,   padding: "8px 12px", fontWeight: 700 }}>{r.nb_business.toLocaleString("fr-FR")}</td>
                <td style={{ color: r.avg_stars >= 4 ? C.green : r.avg_stars >= 3.7 ? C.accent : C.red, padding: "8px 12px", fontWeight: 700 }}>★ {r.avg_stars}</td>
                <td style={{ color: C.muted,  padding: "8px 12px" }}>{r.total_reviews.toLocaleString("fr-FR")}</td>
                <td style={{ padding: "8px 12px" }}>
                  {r.avg_stars >= 4 ? <Badge color={C.green} label="Performant" />
                   : r.avg_stars < 3.6 ? <Badge color={C.red} label="Sous-performant" />
                   : <Badge color={C.accent} label="Correct" />}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}

// ── Scénario 3 : Signaux faibles de dégradation ───────────────
function Scenario3() {
  const cities = ["Las Vegas", "Phoenix", "Toronto", "Pittsburgh"];
  const colors = [C.accent, C.red, C.green, C.blue];

  const deltas = cities.map((c, i) => {
    const first = MV_STARS_EVOLUTION[0][c];
    const last  = MV_STARS_EVOLUTION[MV_STARS_EVOLUTION.length - 1][c];
    return { city: c, color: colors[i], delta: +(last - first).toFixed(2), last };
  });

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: 16 }}>
      <SqlChip sql="MV_STARS_EVOLUTION  (FAIT_REVIEW × DIM_LOCALISATION × DIM_TEMPS)  — annee, nb_reviews, avg_stars, total_useful" />

      {deltas.map((d, i) => (
        <Kpi key={i} label={d.city} value={`★ ${d.last}`} color={d.color}
          sub={`Δ ${d.delta > 0 ? "+" : ""}${d.delta} (2019 → 2024)`} />
      ))}

      <Card title="MV_STARS_EVOLUTION — avg_stars × annee × city" style={{ gridColumn: "1 / 3" }}>
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={MV_STARS_EVOLUTION}>
            <XAxis dataKey="annee" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis domain={[3.1, 4.1]} tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
            <Tooltip content={<T />} />
            {cities.map((c, i) => (
              <Line key={c} type="monotone" dataKey={c} name={c}
                stroke={colors[i]} strokeWidth={2.5}
                dot={{ r: 4, fill: colors[i] }} activeDot={{ r: 6 }} />
            ))}
          </LineChart>
        </ResponsiveContainer>
        <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
          {cities.map((c, i) => <Badge key={c} color={colors[i]} label={c} />)}
        </div>
      </Card>

      <Card title="nb_reviews & total_useful — DIM_TEMPS.annee" style={{ gridColumn: "3 / 5" }}>
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={MV_STARS_EVOLUTION_AGG}>
            <XAxis dataKey="annee" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
            <Tooltip content={<T />} />
            <Line type="monotone" dataKey="nb_reviews"   name="nb_reviews"   stroke={C.blue}  strokeWidth={2.5} dot={{ r: 4, fill: C.blue  }} />
            <Line type="monotone" dataKey="total_useful" name="total_useful (FAIT_REVIEW.nbr_useful)" stroke={C.green} strokeWidth={2.5} dot={{ r: 4, fill: C.green }} />
          </LineChart>
        </ResponsiveContainer>
        <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
          <Badge color={C.blue}  label="nb_reviews" />
          <Badge color={C.green} label="total_useful" />
        </div>
      </Card>

      <Card title="Alertes signaux faibles" style={{ gridColumn: "1 / 3" }}>
        {deltas.filter(d => d.delta < -0.2).map((d, i) => (
          <div key={i} style={{
            display: "flex", alignItems: "center", gap: 12, padding: "10px 14px",
            background: (d.delta < -0.4 ? C.red : C.accent) + "10",
            border: `1px solid ${(d.delta < -0.4 ? C.red : C.accent)}25`,
            borderRadius: 8, marginBottom: 8
          }}>
            <span style={{ fontSize: 18 }}>{d.delta < -0.4 ? "🚨" : "⚠️"}</span>
            <div style={{ flex: 1 }}>
              <p style={{ color: C.text, fontWeight: 600, fontSize: 13 }}>{d.city}</p>
              <p style={{ color: C.muted, fontSize: 11 }}>avg_stars actuel : {d.last} · Δ {d.delta}</p>
            </div>
            <Badge color={d.delta < -0.4 ? C.red : C.accent} label={d.delta < -0.4 ? "Critique" : "Surveillance"} />
          </div>
        ))}
        {deltas.filter(d => d.delta >= 0).map((d, i) => (
          <div key={i} style={{
            display: "flex", alignItems: "center", gap: 12, padding: "10px 14px",
            background: C.green + "10", border: `1px solid ${C.green}25`, borderRadius: 8, marginBottom: 8
          }}>
            <span style={{ fontSize: 18 }}>✅</span>
            <div style={{ flex: 1 }}>
              <p style={{ color: C.text, fontWeight: 600, fontSize: 13 }}>{d.city}</p>
              <p style={{ color: C.muted, fontSize: 11 }}>avg_stars : {d.last} · Δ +{d.delta}</p>
            </div>
            <Badge color={C.green} label="Hausse" />
          </div>
        ))}
      </Card>

      <Card title="Requête Oracle — fenêtre LAG sur MV_STARS_EVOLUTION" style={{ gridColumn: "3 / 5" }}>
        <pre style={{ color: C.teal, fontSize: 10, lineHeight: 1.7, background: "#0a0d18", borderRadius: 6, padding: 12, overflow: "auto" }}>{`SELECT city, state, annee,
  avg_stars,
  LAG(avg_stars, 1) OVER (
    PARTITION BY city
    ORDER BY annee
  ) AS prev_avg_stars,
  avg_stars
    - LAG(avg_stars,1) OVER (
        PARTITION BY city
        ORDER BY annee
      ) AS delta_stars,
  nb_reviews,
  total_useful  -- = SUM(FAIT_REVIEW.nbr_useful)
FROM MV_STARS_EVOLUTION
ORDER BY city, annee;`}</pre>
      </Card>
    </div>
  );
}

// ── Scénario 4 : Émotions & satisfaction client ───────────────
function Scenario4() {
  const radarData = MV_ELITE_USERS_BY_BUSINESS.map(e => ({
    type: e.type_name,
    nb_users_elite: e.nb_users_elite,
    avg_stars_scaled: Math.round(e.avg_stars * 20),
    avg_annees_elite_scaled: Math.round(e.avg_annees_elite * 20),
  }));

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 16 }}>
      <SqlChip sql="MV_BUSINESS_TOP_USERS  ·  MV_ELITE_USERS_BY_BUSINESS  ·  FAIT_USER (useful / funny / cool)" />

      <Kpi label="Total useful — FAIT_USER" value={FAIT_USER_VOTES.reduce((a,c)=>a+c.useful,0).toLocaleString("fr-FR")} color={C.green} sub="Proxy pertinence des reviews" />
      <Kpi label="Total funny — FAIT_USER"  value={FAIT_USER_VOTES.reduce((a,c)=>a+c.funny,0).toLocaleString("fr-FR")}  color={C.accent} sub="Proxy humour / légèreté" />
      <Kpi label="Total cool — FAIT_USER"   value={FAIT_USER_VOTES.reduce((a,c)=>a+c.cool,0).toLocaleString("fr-FR")}   color={C.blue} sub="Proxy style / recommandation" />

      <Card title="FAIT_USER — useful / funny / cool par type de commerce" style={{ gridColumn: "1 / 3" }}>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={FAIT_USER_VOTES} barGap={3}>
            <XAxis dataKey="type_name" tick={{ fill: C.muted, fontSize: 11 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: C.muted, fontSize: 10 }} axisLine={false} tickLine={false} />
            <Tooltip content={<T />} />
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

      <Card title="MV_ELITE_USERS_BY_BUSINESS — radar">
        <ResponsiveContainer width="100%" height={220}>
          <RadarChart data={radarData} cx="50%" cy="50%">
            <PolarGrid stroke={C.border} />
            <PolarAngleAxis dataKey="type" tick={{ fill: C.muted, fontSize: 10 }} />
            <Radar name="nb_users_elite" dataKey="nb_users_elite" stroke={C.purple} fill={C.purple} fillOpacity={0.18} />
            <Radar name="avg_stars ×20"  dataKey="avg_stars_scaled" stroke={C.accent} fill={C.accent} fillOpacity={0.12} />
            <Tooltip content={<T />} />
          </RadarChart>
        </ResponsiveContainer>
        <div style={{ display: "flex", gap: 8, justifyContent: "center", marginTop: 4 }}>
          <Badge color={C.purple} label="nb_users_elite" />
          <Badge color={C.accent} label="avg_stars ×20" />
        </div>
      </Card>

      <Card title="MV_BUSINESS_TOP_USERS — users utiles (useful > 100) par type × ville" style={{ gridColumn: "1 / 3" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
          <thead>
            <tr>{["type_name", "city", "nb_users_distincts", "avg_stars_reviews", "total_useful (nbr_useful)"].map(h => (
              <th key={h} style={{ color: C.muted, textAlign: "left", padding: "5px 10px", borderBottom: `1px solid ${C.border}`, fontWeight: 600 }}>{h}</th>
            ))}</tr>
          </thead>
          <tbody>
            {MV_BUSINESS_TOP_USERS.map((r, i) => (
              <tr key={i} style={{ borderBottom: `1px solid ${C.border}` }}>
                <td style={{ color: C.text,   padding: "7px 10px" }}>{r.type_name}</td>
                <td style={{ color: C.muted,  padding: "7px 10px" }}>{r.city}</td>
                <td style={{ color: C.blue,   padding: "7px 10px", fontWeight: 700 }}>{r.nb_users_distincts.toLocaleString("fr-FR")}</td>
                <td style={{ color: C.accent, padding: "7px 10px", fontWeight: 700 }}>★ {r.avg_stars_reviews}</td>
                <td style={{ color: C.green,  padding: "7px 10px", fontWeight: 700 }}>{r.total_useful.toLocaleString("fr-FR")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>

      <Card title="Satisfaction élite par type — avg_stars">
        {MV_ELITE_USERS_BY_BUSINESS.map((e, i) => (
          <div key={i} style={{ marginBottom: 10 }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
              <span style={{ color: C.text, fontSize: 12 }}>{e.type_name}</span>
              <span style={{ color: C.accent, fontWeight: 700, fontSize: 12 }}>★ {e.avg_stars} · {e.nb_users_elite} élite</span>
            </div>
            <div style={{ background: C.border, borderRadius: 4, height: 6, overflow: "hidden" }}>
              <div style={{ width: `${((e.avg_stars - 3) / 1.5) * 100}%`, height: "100%", background: e.avg_stars >= 4 ? C.green : C.accent, borderRadius: 4 }} />
            </div>
          </div>
        ))}
      </Card>
    </div>
  );
}

// ── App principale ────────────────────────────────────────────
const TABS = [
  { label: "① Contributeurs",           component: <Scenario1 /> },
  { label: "② Opportunités",            component: <Scenario2 /> },
  { label: "③ Signaux faibles",         component: <Scenario3 /> },
  { label: "④ Émotions / Satisfaction", component: <Scenario4 /> },
];

const DESCS = [
  "MV_USER_SCORE_GLOBAL — score_pertinence = useful + fans + (review_count × average_stars) + (nb_annees_elite × 100) + (friend_count / 10) · DIM_USER_ELITE : elite_2022/23/24",
  "MV_STARS_BY_CITY (nb_business, avg_stars, total_reviews) · MV_STARS_BY_CATEGORIE (DIM_CATEGORIE.categorie_name) · MV_STARS_BY_TYPE (DIM_TYPE_BUSINESS.type_name)",
  "MV_STARS_EVOLUTION — FAIT_REVIEW × DIM_LOCALISATION × DIM_TEMPS · colonnes : annee, nb_reviews, avg_stars, total_useful (FAIT_REVIEW.nbr_useful)",
  "FAIT_USER (useful, funny, cool) · MV_BUSINESS_TOP_USERS (nb_users_distincts, total_useful, WHERE u.useful > 100) · MV_ELITE_USERS_BY_BUSINESS (nb_users_elite, avg_annees_elite)",
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
          <span style={{ color: C.muted, fontSize: 11 }}>Oracle 19c · Schéma constellation · Master 2 BDIA 2025-2026</span>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Badge color={C.green}  label="DM01 · FAIT_BUSINESS" />
          <Badge color={C.purple} label="DM02 · FAIT_USER" />
          <Badge color={C.teal}   label="FAIT_REVIEW (bridge)" />
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
        {TABS[tab].component}
      </div>
    </div>
  );
}