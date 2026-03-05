-- ═══════════════════════════════════════════════════════════
-- SCRIPT POUR L'ADMINISTRATEUR
-- À exécuter avec un compte superuser (postgres ou id)
-- ═══════════════════════════════════════════════════════════

-- Donner les droits de création sur le schéma yelp à l'utilisateur tpid
GRANT CREATE ON SCHEMA yelp TO tpid;

-- Ou créer un nouveau schéma dédié au datamart
CREATE SCHEMA datamart02 AUTHORIZATION tpid;
GRANT ALL ON SCHEMA datamart02 TO tpid;

\echo ''
\echo '✓ Droits accordés à l''utilisateur tpid'
\echo '  L''utilisateur peut maintenant créer des tables dans:'
\echo '  - yelp (si première option choisie)'
\echo '  - datamart02 (si deuxième option choisie)'
\echo ''
