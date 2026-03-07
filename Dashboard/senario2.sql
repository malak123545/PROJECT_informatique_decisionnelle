-- ============================================================
-- Scénario 2 — Cartographie des opportunités commerciales
-- Performance par type de business par state
-- ============================================================
-- Pour chaque state, et chaque type de business :
--   - nb_business        : nombre de commerces de ce type
--   - avg_stars          : note moyenne
--   - total_reviews      : volume d'avis
--   - avg_reviews        : moyenne de reviews par commerce
--   - rang_meilleur      : classement du meilleur type par state (par avg_stars)
--   - rang_pire          : classement du pire type par state
--
-- Exclusion : combinaisons (state, type) avec moins de 5 business
--             et states avec moins de 100 business au total
-- ============================================================

SELECT
    state,
    type_business,
    nb_business,
    avg_stars,
    total_reviews,
    avg_reviews_par_business,
    rang_meilleur,
    rang_pire,
    statut
FROM V_PERFORMANCE_BY_STATE
ORDER BY state, avg_stars DESC;