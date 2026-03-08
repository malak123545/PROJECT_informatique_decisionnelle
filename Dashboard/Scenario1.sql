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


-- Premier tableau : les 10 meilleurs users contributeurs pour le type de business sélectionné
SELECT
      rang,
      user_id,
      nom_user as "Utilisateur",
      score_pertinence,
      review_count as "nombre d'avis",
      nb_amis as "nombre d'amis",
      fans as "nombre de fan",
      nb_annees_elite as "nombre d'années élite",
      average_stars "note moyenne",
      best_review_stars "note du meilleur avis",                                                                                                                                                            
      best_review_useful as "nombre de personne qui ont trouvé l'avis utile",
      best_review_date as "Date du meilleur avis"   
FROM V_TOP_USERS_BY_BUSINESS_TYPE
WHERE {{type_business}}
AND rang <= 10
ORDER BY rang


--- Meilleur avis du user sélectionné

SELECT
  u.name                  AS "Utilisateur",
  r.review_id             AS best_review_id,
  r.business_id           AS best_review_business_id,
  b.name                  AS "Nom de l'entreprise",
  l.city                  AS "Ville de l'entreprise",
  r.stars                 AS "Note",
  r.total_useful          AS "Nombre personnes qui ont trouvé l'avis utile",
  r.date_review           AS "Date de l'avis"
FROM DIM_REVIEW r
JOIN FAIT_BUSINESS    b ON r.business_id      = b.business_id
JOIN DIM_LOCALISATION l ON b.localisation_id  = l.localisation_id
JOIN FAIT_USER        u ON r.user_id          = u.user_id
WHERE r.user_id = {{user_id}}
ORDER BY r.total_useful DESC, r.date_review DESC
FETCH FIRST 1 ROW ONLY