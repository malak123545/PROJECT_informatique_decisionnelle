-- ═══════════════════════════════════════════════════════════
-- Script de vérification du Data Mart 02
-- Analyse des résultats après exécution de l'ETL
-- ═══════════════════════════════════════════════════════════

\echo '═══════════════════════════════════════════════════════════'
\echo '  VÉRIFICATION DU DATA MART 02 - YELP PERTINENCE'
\echo '═══════════════════════════════════════════════════════════'
\echo ''

-- 1. STATISTIQUES GÉNÉRALES
\echo '─────────────────────────────────────────'
\echo '1. STATISTIQUES GÉNÉRALES'
\echo '─────────────────────────────────────────'

SELECT
    COUNT(*) as total_utilisateurs,
    COUNT(CASE WHEN nbr_reviews > 0 THEN 1 END) as avec_reviews,
    COUNT(CASE WHEN nbr_elite_years > 0 THEN 1 END) as utilisateurs_elite,
    COUNT(CASE WHEN nbr_tips > 0 THEN 1 END) as avec_tips,
    ROUND(AVG(pertinence_score), 2) as score_moyen,
    MAX(pertinence_score) as score_max,
    MIN(pertinence_score) as score_min
FROM yelp.fact_user_pertinence;

\echo ''
\echo '─────────────────────────────────────────'
\echo '2. TOP 10 UTILISATEURS PAR PERTINENCE'
\echo '─────────────────────────────────────────'

SELECT
    user_id,
    name,
    pertinence_score,
    nbr_reviews,
    ROUND(avg_stars::numeric, 2) as avg_stars,
    total_useful,
    total_cool,
    total_funny,
    nbr_elite_years,
    fans
FROM yelp.fact_user_pertinence
ORDER BY pertinence_score DESC
LIMIT 10;

\echo ''
\echo '─────────────────────────────────────────'
\echo '3. TOP 10 UTILISATEURS PAR NOMBRE DE REVIEWS'
\echo '─────────────────────────────────────────'

SELECT
    user_id,
    name,
    nbr_reviews,
    ROUND(avg_stars::numeric, 2) as avg_stars,
    total_useful,
    pertinence_score,
    nbr_elite_years
FROM yelp.fact_user_pertinence
ORDER BY nbr_reviews DESC
LIMIT 10;

\echo ''
\echo '─────────────────────────────────────────'
\echo '4. DISTRIBUTION DES SCORES DE PERTINENCE'
\echo '─────────────────────────────────────────'

SELECT
    CASE
        WHEN pertinence_score = 0 THEN '0 - Aucune activité'
        WHEN pertinence_score BETWEEN 1 AND 100 THEN '1-100 - Faible'
        WHEN pertinence_score BETWEEN 101 AND 500 THEN '101-500 - Moyen'
        WHEN pertinence_score BETWEEN 501 AND 1000 THEN '501-1000 - Bon'
        WHEN pertinence_score BETWEEN 1001 AND 5000 THEN '1001-5000 - Très bon'
        ELSE '5000+ - Excellent'
    END as categorie_pertinence,
    COUNT(*) as nombre_utilisateurs,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER(), 2) as pourcentage
FROM yelp.fact_user_pertinence
GROUP BY categorie_pertinence
ORDER BY MIN(pertinence_score);

\echo ''
\echo '─────────────────────────────────────────'
\echo '5. STATISTIQUES PAR STATUT ÉLITE'
\echo '─────────────────────────────────────────'

SELECT
    CASE
        WHEN nbr_elite_years = 0 THEN 'Non-élite'
        WHEN nbr_elite_years BETWEEN 1 AND 3 THEN 'Élite 1-3 ans'
        WHEN nbr_elite_years BETWEEN 4 AND 6 THEN 'Élite 4-6 ans'
        ELSE 'Élite 7+ ans'
    END as statut_elite,
    COUNT(*) as nombre_utilisateurs,
    ROUND(AVG(pertinence_score), 2) as score_moyen,
    ROUND(AVG(nbr_reviews), 2) as reviews_moyennes,
    ROUND(AVG(total_useful), 2) as useful_moyen,
    ROUND(AVG(fans), 2) as fans_moyens
FROM yelp.fact_user_pertinence
GROUP BY statut_elite
ORDER BY MIN(nbr_elite_years);

\echo ''
\echo '─────────────────────────────────────────'
\echo '6. UTILISATEURS ÉLITES LES PLUS RÉCENTS'
\echo '─────────────────────────────────────────'

SELECT
    user_id,
    name,
    nbr_elite_years,
    last_elite_year,
    pertinence_score,
    nbr_reviews,
    fans
FROM yelp.fact_user_pertinence
WHERE nbr_elite_years > 0
ORDER BY last_elite_year DESC, pertinence_score DESC
LIMIT 10;

\echo ''
\echo '─────────────────────────────────────────'
\echo '7. CORRÉLATION ENTRE MÉTRIQUES'
\echo '─────────────────────────────────────────'

SELECT
    'Reviews vs Pertinence' as correlation,
    ROUND(CORR(nbr_reviews, pertinence_score)::numeric, 3) as coefficient
FROM yelp.fact_user_pertinence
WHERE nbr_reviews > 0

UNION ALL

SELECT
    'Fans vs Pertinence' as correlation,
    ROUND(CORR(fans, pertinence_score)::numeric, 3) as coefficient
FROM yelp.fact_user_pertinence
WHERE fans > 0

UNION ALL

SELECT
    'Elite vs Pertinence' as correlation,
    ROUND(CORR(nbr_elite_years, pertinence_score)::numeric, 3) as coefficient
FROM yelp.fact_user_pertinence
WHERE nbr_elite_years > 0;

\echo ''
\echo '─────────────────────────────────────────'
\echo '8. UTILISATEURS AVEC LE MEILLEUR ENGAGEMENT'
\echo '   (Ratio useful/review le plus élevé)'
\echo '─────────────────────────────────────────'

SELECT
    user_id,
    name,
    nbr_reviews,
    total_useful,
    ROUND((total_useful::numeric / NULLIF(nbr_reviews, 0)), 2) as useful_par_review,
    pertinence_score,
    nbr_elite_years
FROM yelp.fact_user_pertinence
WHERE nbr_reviews >= 10  -- Au moins 10 reviews pour être significatif
ORDER BY (total_useful::numeric / NULLIF(nbr_reviews, 0)) DESC
LIMIT 10;

\echo ''
\echo '─────────────────────────────────────────'
\echo '9. ANCIENNETÉ DES UTILISATEURS'
\echo '─────────────────────────────────────────'

SELECT
    CASE
        WHEN yelping_since >= '2020-01-01' THEN '2020-2026 (Récents)'
        WHEN yelping_since >= '2015-01-01' THEN '2015-2019 (Moyens)'
        WHEN yelping_since >= '2010-01-01' THEN '2010-2014 (Anciens)'
        ELSE '2004-2009 (Très anciens)'
    END as periode_inscription,
    COUNT(*) as nombre_utilisateurs,
    ROUND(AVG(pertinence_score), 2) as score_moyen,
    ROUND(AVG(nbr_reviews), 2) as reviews_moyennes
FROM yelp.fact_user_pertinence
GROUP BY periode_inscription
ORDER BY MIN(yelping_since);

\echo ''
\echo '─────────────────────────────────────────'
\echo '10. QUALITÉ DES INDEX'
\echo '─────────────────────────────────────────'

SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan as utilisations_index,
    idx_tup_read as lignes_lues,
    idx_tup_fetch as lignes_retournees
FROM pg_stat_user_indexes
WHERE tablename = 'fact_user_pertinence'
ORDER BY idx_scan DESC;

\echo ''
\echo '═══════════════════════════════════════════════════════════'
\echo '  VÉRIFICATION TERMINÉE'
\echo '═══════════════════════════════════════════════════════════'
