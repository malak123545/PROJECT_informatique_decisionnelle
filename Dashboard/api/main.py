from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import oracledb
import os

app = FastAPI()

# ── CORS : autorise le frontend React ─────────────────────
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ══════════════════════════════════════════
#  CONFIG Oracle — Remplissez ici
# ══════════════════════════════════════════
ORACLE_HOST     = os.getenv("ORACLE_HOST",     "stendhal.iem")   # ← hôte Oracle
ORACLE_PORT     = int(os.getenv("ORACLE_PORT", "1521"))           # ← port
ORACLE_SERVICE  = os.getenv("ORACLE_SERVICE",  "enss2025")        # ← service/SID
ORACLE_USER     = os.getenv("ORACLE_USER",     "VOTRE_LOGIN")     # ← login
ORACLE_PASSWORD = os.getenv("ORACLE_PASSWORD", "VOTRE_MOT_DE_PASSE") # ← mot de passe
# ══════════════════════════════════════════

def get_connection():
    dsn = f"{ORACLE_HOST}:{ORACLE_PORT}/{ORACLE_SERVICE}"
    return oracledb.connect(user=ORACLE_USER, password=ORACLE_PASSWORD, dsn=dsn)

def query_to_list(sql: str, params: dict = {}):
    """Exécute une requête et retourne une liste de dicts."""
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            cols = [c[0].lower() for c in cur.description]
            return [dict(zip(cols, row)) for row in cur.fetchall()]

# ── Health check ──────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok"}

# ── Scénario 1 : Top Users ────────────────────────────────
@app.get("/api/scenario1/top-users")
def top_users():
    try:
        rows = query_to_list("""
            SELECT
                type_business, rang, user_id, nom_user,
                score_pertinence, review_count, useful, funny, cool,
                nb_amis, fans, nb_annees_elite, derniere_annee_elite,
                average_stars, best_review_stars, best_review_useful,
                TO_CHAR(best_review_date, 'YYYY-MM-DD') AS best_review_date
            FROM V_TOP_USERS_BY_BUSINESS_TYPE
            WHERE rang <= 100
            ORDER BY score_pertinence DESC
        """)
        return {"data": rows}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# ── Scénario 1 : Stats globales (KPIs) ───────────────────
@app.get("/api/scenario1/kpis")
def kpis():
    try:
        rows = query_to_list("""
            SELECT
                MAX(score_pertinence)                          AS score_max,
                ROUND(100 * SUM(CASE WHEN nb_annees_elite > 0 THEN 1 ELSE 0 END)
                      / COUNT(DISTINCT user_id))               AS pct_elite,
                ROUND(AVG(review_count))                       AS avg_reviews,
                SUM(useful)                                    AS total_useful
            FROM (
                SELECT DISTINCT user_id, score_pertinence,
                       nb_annees_elite, review_count, useful
                FROM V_TOP_USERS_BY_BUSINESS_TYPE
            )
        """)
        return {"data": rows[0] if rows else {}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))