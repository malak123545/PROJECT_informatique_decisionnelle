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
    best_review_stars,
    best_review_useful,
    best_review_date
FROM ranked
WHERE rang <= 100;
