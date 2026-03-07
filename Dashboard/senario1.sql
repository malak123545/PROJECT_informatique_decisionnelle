-- QUESTION 1 : Classement global par score de pertinence
SELECT
    f.user_id,
    f.name,
    f.review_count,
    ROUND(f.average_stars, 2)       AS avg_stars,
    f.useful,
    f.fans,
    f.nb_annees_elite,
    f.friend_count,
    f.derniere_annee_elite,
    ROUND(
        (f.useful * 2)
        + (f.fans * 3)
        + (f.review_count * f.average_stars)
        + (f.nb_annees_elite * 100)
        + (f.friend_count / 10)
        + (CASE WHEN f.derniere_annee_elite = 2024 THEN 50 ELSE 0 END)
    , 2) AS score_pertinence
FROM FAIT_USER f
WHERE f.review_count > 0
ORDER BY score_pertinence DESC
FETCH FIRST 100 ROWS ONLY