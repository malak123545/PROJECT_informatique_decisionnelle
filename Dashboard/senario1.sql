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
    type_business,
    rang,
    user_id,
    nom_user,
    score_pertinence,
    review_count,
    useful,
    funny,
    cool,
    nb_amis,
    fans,
    nb_annees_elite,
    derniere_annee_elite,
    average_stars,
    best_review_id,
    best_review_business_id,
    best_review_stars,
    best_review_useful,
    best_review_date
FROM V_TOP_USERS_BY_BUSINESS_TYPE
ORDER BY type_business, rang;
