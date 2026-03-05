-- ═══════════════════════════════════════════════════════════
-- VÉRIFICATION DU DATAMART02
-- Script pour valider l'import des données
-- ═══════════════════════════════════════════════════════════

\echo ''
\echo '═══════════════════════════════════════════════════════════'
\echo '   VÉRIFICATION DU DATAMART02'
\echo '═══════════════════════════════════════════════════════════'
\echo ''

-- Définir le schéma de travail
SET search_path TO datamart02;

-- ───────────────────────────────────────────────────────────
-- 1. COMPTAGE DES LIGNES
-- ───────────────────────────────────────────────────────────
\echo '─────────────────────────────────────────'
\echo '1. COMPTAGE DES LIGNES'
\echo '─────────────────────────────────────────'
\echo ''

SELECT 'dim_user' as table_name, COUNT(*) as nbr_lignes FROM dim_user
UNION ALL
SELECT 'dim_review', COUNT(*) FROM dim_review
UNION ALL
SELECT 'dim_elite', COUNT(*) FROM dim_elite
UNION ALL
SELECT 'fact_user_pertinence', COUNT(*) FROM fact_user_pertinence
ORDER BY table_name;

\echo ''

-- ───────────────────────────────────────────────────────────
-- 2. STATISTIQUES GÉNÉRALES
-- ───────────────────────────────────────────────────────────
\echo '─────────────────────────────────────────'
\echo '2. STATISTIQUES GÉNÉRALES'
\echo '─────────────────────────────────────────'
\echo ''

SELECT
    COUNT(*) as total_users,
    COUNT(*) FILTER (WHERE nbr_reviews > 0) as users_with_reviews,
    COUNT(*) FILTER (WHERE nbr_elite_years > 0) as elite_users,
    ROUND(AVG(pertinence_score)::numeric, 2) as avg_pertinence_score,
    ROUND(MIN(pertinence_score)::numeric, 2) as min_score,
    ROUND(MAX(pertinence_score)::numeric, 2) as max_score
FROM fact_user_pertinence;

\echo ''

-- ───────────────────────────────────────────────────────────
-- 3. TOP 10 UTILISATEURS PAR PERTINENCE
-- ───────────────────────────────────────────────────────────
\echo '─────────────────────────────────────────'
\echo '3. TOP 10 UTILISATEURS PAR PERTINENCE'
\echo '─────────────────────────────────────────'
\echo ''

SELECT
    u.name,
    u.fans,
    f.nbr_reviews,
    ROUND(f.avg_stars::numeric, 2) as avg_stars,
    f.total_useful,
    f.nbr_elite_years,
    ROUND(f.pertinence_score::numeric, 2) as score
FROM fact_user_pertinence f
JOIN dim_user u ON f.user_id = u.user_id
ORDER BY f.pertinence_score DESC
LIMIT 10;

\echo ''

-- ───────────────────────────────────────────────────────────
-- 4. RÉPARTITION PAR ACTIVITÉ
-- ───────────────────────────────────────────────────────────
\echo '─────────────────────────────────────────'
\echo '4. RÉPARTITION PAR NIVEAU D''ACTIVITÉ'
\echo '─────────────────────────────────────────'
\echo ''

SELECT * FROM v_user_activity_stats;

\echo ''

-- ───────────────────────────────────────────────────────────
-- 5. RÉPARTITION PAR ANNÉES ÉLITE (TOP 10)
-- ───────────────────────────────────────────────────────────
\echo '─────────────────────────────────────────'
\echo '5. UTILISATEURS ÉLITES (Top années)'
\echo '─────────────────────────────────────────'
\echo ''

SELECT * FROM v_elite_stats LIMIT 10;

\echo ''

-- ───────────────────────────────────────────────────────────
-- 6. INTÉGRITÉ RÉFÉRENTIELLE
-- ───────────────────────────────────────────────────────────
\echo '─────────────────────────────────────────'
\echo '6. VÉRIFICATION DE L''INTÉGRITÉ'
\echo '─────────────────────────────────────────'
\echo ''

-- Vérifier que tous les users de fact sont dans dim_user
SELECT
    'Fact sans User' as test,
    COUNT(*) as anomalies
FROM fact_user_pertinence f
LEFT JOIN dim_user u ON f.user_id = u.user_id
WHERE u.user_id IS NULL

UNION ALL

-- Vérifier que tous les users de dim_review sont dans dim_user
SELECT
    'Review sans User' as test,
    COUNT(*)
FROM dim_review r
LEFT JOIN dim_user u ON r.user_id = u.user_id
WHERE u.user_id IS NULL

UNION ALL

-- Vérifier que tous les users de dim_elite sont dans dim_user
SELECT
    'Elite sans User' as test,
    COUNT(*)
FROM dim_elite e
LEFT JOIN dim_user u ON e.user_id = u.user_id
WHERE u.user_id IS NULL;

\echo ''

-- ───────────────────────────────────────────────────────────
-- 7. STATISTIQUES PAR ANNÉE D'INSCRIPTION
-- ───────────────────────────────────────────────────────────
\echo '─────────────────────────────────────────'
\echo '7. RÉPARTITION PAR ANNÉE D''INSCRIPTION'
\echo '─────────────────────────────────────────'
\echo ''

SELECT
    EXTRACT(YEAR FROM yelping_since)::integer as year,
    COUNT(*) as new_users,
    ROUND(AVG(f.pertinence_score)::numeric, 2) as avg_score
FROM dim_user u
JOIN fact_user_pertinence f ON u.user_id = f.user_id
WHERE yelping_since >= '2010-01-01'
GROUP BY EXTRACT(YEAR FROM yelping_since)
ORDER BY year DESC
LIMIT 10;

\echo ''
\echo '═══════════════════════════════════════════════════════════'
\echo '   ✓ VÉRIFICATION TERMINÉE'
\echo '═══════════════════════════════════════════════════════════'
\echo ''
