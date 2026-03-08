-- ============================================================
-- VUE : V_PERFORMANCE_BY_STATE
-- Scénario 2 — Cartographie des opportunités commerciales
-- ============================================================
-- Objectif : pour chaque state et chaque type de business,
--   mesurer la performance (avg_stars, volume de reviews,
--   densité) et identifier le meilleur et le pire type.
--
-- Exclusions :
--   - combinaisons (state, type) avec < 5 business
--   - states avec < 100 business au total
-- ============================================================

CREATE OR REPLACE VIEW V_PERFORMANCE_BY_STATE AS

WITH

-- ── 1. Agrégation par state + type de business ───────────────
perf_par_type AS (
    SELECT
        l.state,
        t.type_name,
        COUNT(b.business_id)            AS nb_business,
        ROUND(AVG(b.stars), 2)          AS avg_stars,
        SUM(b.review_count)             AS total_reviews,
        ROUND(AVG(b.review_count), 0)   AS avg_reviews_par_business
    FROM FAIT_BUSINESS b
    JOIN DIM_LOCALISATION  l ON b.localisation_id = l.localisation_id
    JOIN DIM_TYPE_BUSINESS t ON b.type_id         = t.type_id
    WHERE l.state IS NOT NULL
    GROUP BY l.state, t.type_name
    HAVING COUNT(b.business_id) >= 5
),

-- ── 2. States avec suffisamment de données ───────────────────
states_valides AS (
    SELECT state
    FROM (
        SELECT l.state, COUNT(b.business_id) AS total
        FROM FAIT_BUSINESS b
        JOIN DIM_LOCALISATION l ON b.localisation_id = l.localisation_id
        GROUP BY l.state
    )
    WHERE total >= 100
),

-- ── 3. Classement meilleur / pire + % par state ──────────────
ranked AS (
    SELECT
        p.*,
        ROUND(p.nb_business * 100.0 / SUM(p.nb_business) OVER (PARTITION BY p.state), 2) AS pct_business,
        RANK() OVER (
            PARTITION BY p.state
            ORDER BY p.avg_stars DESC, p.total_reviews DESC
        ) AS rang_meilleur,
        RANK() OVER (
            PARTITION BY p.state
            ORDER BY p.avg_stars ASC, p.total_reviews ASC
        ) AS rang_pire
    FROM perf_par_type p
    WHERE p.state IN (SELECT state FROM states_valides)
)

-- ── 4. Résultat final ────────────────────────────────────────
SELECT
    state,
    type_name                   AS type_business,
    nb_business,
    pct_business,
    avg_stars,
    total_reviews,
    avg_reviews_par_business,
    rang_meilleur,
    rang_pire,
    CASE
        WHEN rang_meilleur = 1 THEN 'MEILLEUR'
        WHEN rang_pire     = 1 THEN 'PIRE'
        ELSE '-'
    END                         AS statut
FROM ranked;
