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


--- Par Etat et type de business : nombre de business, note moyenne, volume d'avis, moyenne de reviews par commerce
--- Carte des USA pour visualiser les opportunités commerciales
SELECT                                                                                                                                                                            
    state as "Etat",
    type_business as "Type d'entreprise",                                                                                                                                                          
    avg_stars as "Note moyenne",
    NB_BUSINESS as "Nombre d'entreprise"
FROM V_PERFORMANCE_BY_STATE                                                                                                                                                 
WHERE Statut = {{Classement}}

--- Par Etat sélectionné : histogramme du nombre d'entreprise par type et courbe de tendance de la note moyenne
SELECT
  state as "Etat",
  type_business as "Type d'entreprise",
  nb_business as "Nombre d'entreprise",
  pct_business as "Pourcentage du nb d'entreprise",
  avg_stars as "Note moyenne",
  total_reviews as "Nombre d'avis",
  avg_reviews_par_business "Note moyenne par Entreprise",
  statut
FROM V_PERFORMANCE_BY_STATE
WHERE state = {{state}}
ORDER BY avg_stars DESC