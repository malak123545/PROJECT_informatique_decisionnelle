-- ============================================================
-- VUE : V_TOP_USERS_BY_BUSINESS_TYPE
-- Scénario 1 — Récompenser les users contributeurs
-- ============================================================
-- Objectif : identifier les 100 meilleurs utilisateurs par
--   type de business, selon un score de pertinence composé de :
--
--   SCORE_BASE = review_count + useful + funny + cool
--             + tous les compliment_X
--
--   SCORE_FINAL = SCORE_BASE * (1 + SQRT(friend_count)) * (1 + SQRT(fans))
--
--   Affiché en plus : nb_annees_elite, derniere_annee_elite
--   Et pour chaque user : sa review la plus useful
-- ============================================================
CREATE MATERIALIZED VIEW V_TOP_USERS_BY_BUSINESS_TYPE
BUILD IMMEDIATE
REFRESH COMPLETE
START WITH SYSDATE
NEXT TRUNC(SYSDATE + 1) + 2/24
AS

WITH

-- ── 1. Score de pertinence global par user ───────────────────
user_scores AS (
    SELECT
        u.user_id,
        u.name,
        u.review_count,
        u.useful,
        u.funny,
        u.cool,
        u.friend_count,
        u.fans,
        u.nb_annees_elite,
        u.derniere_annee_elite,
        u.average_stars,
        -- Score de base : activité + tous les compliments reçus
        (
            NVL(u.review_count,         0) +
            NVL(u.useful,               0) +
            NVL(u.funny,                0) +
            NVL(u.cool,                 0) +
            NVL(u.compliment_hot,       0) +
            NVL(u.compliment_more,      0) +
            NVL(u.compliment_profile,   0) +
            NVL(u.compliment_cute,      0) +
            NVL(u.compliment_list,      0) +
            NVL(u.compliment_note,      0) +
            NVL(u.compliment_plain,     0) +
            NVL(u.compliment_cool,      0) +
            NVL(u.compliment_funny,     0)
        ) / 13
          * (1 + (2 * SQRT(NVL(u.fans,          0)) + SQRT(NVL(u.friend_count, 0))) / 10)  AS score_pertinence
    FROM FAIT_USER u
),

-- ── 2. Review la plus useful par user ───────────────────────
-- En cas d'égalité sur total_useful, on prend la plus récente
best_review AS (
    SELECT
        r.user_id,
        r.review_id         AS best_review_id,
        r.business_id       AS best_review_business_id,
        b.name              AS best_review_business_name,
        l.city              AS best_review_city,
        r.stars             AS best_review_stars,
        r.total_useful      AS best_review_useful,
        r.date_review       AS best_review_date
    FROM (
        SELECT
            r2.*,
            ROW_NUMBER() OVER (
                PARTITION BY r2.user_id
                ORDER BY r2.total_useful DESC, r2.date_review DESC
            ) AS rn
        FROM DIM_REVIEW r2
    ) r
    JOIN FAIT_BUSINESS   b ON r.business_id     = b.business_id
    JOIN DIM_LOCALISATION l ON b.localisation_id = l.localisation_id
    WHERE r.rn = 1
),

-- ── 3. Association user <-> type de business (via ses reviews) ─
-- Un user peut apparaître dans plusieurs types de business
user_business_type AS (
    SELECT DISTINCT
        r.user_id,
        t.type_id,
        t.type_name
    FROM DIM_REVIEW r
    JOIN FAIT_BUSINESS     b ON r.business_id = b.business_id
    JOIN DIM_TYPE_BUSINESS t ON b.type_id     = t.type_id
),

-- ── 4. Classement des users au sein de chaque type de business ─
ranked AS (
    SELECT
        ubt.type_name,
        s.user_id,
        s.name,
        s.score_pertinence,
        s.review_count,
        s.useful,
        s.funny,
        s.cool,
        s.friend_count,
        s.fans,
        s.nb_annees_elite,
        s.derniere_annee_elite,
        s.average_stars,
        br.best_review_id,
        br.best_review_business_id,
        br.best_review_business_name,
        br.best_review_city,
        br.best_review_stars,
        br.best_review_useful,
        br.best_review_date,
        ROW_NUMBER() OVER (
            PARTITION BY ubt.type_name
            ORDER BY s.score_pertinence DESC
        ) AS rang
    FROM user_business_type ubt
    JOIN user_scores  s  ON ubt.user_id = s.user_id
    LEFT JOIN best_review br ON s.user_id = br.user_id
)

-- ── 5. Résultat final : top 100 par type ────────────────────
SELECT
    type_name               AS type_business,
    rang,
    user_id,
    name                    AS nom_user,
    ROUND(score_pertinence) AS score_pertinence,
    -- Métriques d'activité
    review_count,
    useful,
    funny,
    cool,
    -- Réseau social
    friend_count            AS nb_amis,
    fans,
    -- Statut élite
    nb_annees_elite,
    derniere_annee_elite,
    -- Qualité générale
    average_stars,
    -- Meilleure review
    best_review_id,
    best_review_business_id,
    best_review_business_name,
    best_review_city,
    best_review_stars,
    best_review_useful,
    best_review_date
FROM ranked
WHERE rang <= 100;
