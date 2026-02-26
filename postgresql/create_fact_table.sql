-- ═══════════════════════════════════════════════════════════
-- Script SQL pour créer la table FACT_USER_PERTINENCE
-- Data Mart 02 - Mesure de la pertinence des reviews Yelp
-- ═══════════════════════════════════════════════════════════

-- Supprimer la table si elle existe déjà
DROP TABLE IF EXISTS yelp.fact_user_pertinence CASCADE;

-- Créer la table de faits
CREATE TABLE yelp.fact_user_pertinence (
    -- Clé primaire
    user_id VARCHAR(50) PRIMARY KEY,

    -- Informations utilisateur de base
    name VARCHAR(255) NOT NULL,
    fans INTEGER DEFAULT 0,
    yelping_since DATE NOT NULL,

    -- Métriques de reviews (DIM_REVIEW)
    nbr_reviews BIGINT DEFAULT 0,
    avg_stars DOUBLE PRECISION DEFAULT 0.0,
    total_useful BIGINT DEFAULT 0,
    total_funny BIGINT DEFAULT 0,
    total_cool BIGINT DEFAULT 0,

    -- Métriques élite (DIM_ELITE)
    nbr_elite_years BIGINT DEFAULT 0,
    last_elite_year INTEGER,

    -- Métriques tips (DIM_TIP)
    nbr_tips BIGINT DEFAULT 0,
    total_compliments BIGINT DEFAULT 0,

    -- Score de pertinence calculé
    pertinence_score BIGINT NOT NULL,

    -- Métadonnées
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Créer des index pour améliorer les performances des requêtes
CREATE INDEX idx_fact_pertinence_score ON yelp.fact_user_pertinence(pertinence_score DESC);
CREATE INDEX idx_fact_nbr_reviews ON yelp.fact_user_pertinence(nbr_reviews DESC);
CREATE INDEX idx_fact_nbr_elite_years ON yelp.fact_user_pertinence(nbr_elite_years DESC);
CREATE INDEX idx_fact_fans ON yelp.fact_user_pertinence(fans DESC);
CREATE INDEX idx_fact_yelping_since ON yelp.fact_user_pertinence(yelping_since);

-- Ajouter des commentaires pour la documentation
COMMENT ON TABLE yelp.fact_user_pertinence IS
'Table de faits pour mesurer la pertinence des utilisateurs Yelp basée sur leurs reviews, statut élite, tips et engagement';

COMMENT ON COLUMN yelp.fact_user_pertinence.user_id IS
'Identifiant unique de l''utilisateur (clé primaire)';

COMMENT ON COLUMN yelp.fact_user_pertinence.pertinence_score IS
'Score calculé : (total_useful × 3) + (total_cool × 2) + (total_funny × 1) + (nbr_elite_years × 10)';

COMMENT ON COLUMN yelp.fact_user_pertinence.nbr_reviews IS
'Nombre total de reviews écrites par l''utilisateur';

COMMENT ON COLUMN yelp.fact_user_pertinence.avg_stars IS
'Moyenne des étoiles données par l''utilisateur dans ses reviews';

COMMENT ON COLUMN yelp.fact_user_pertinence.total_useful IS
'Total des votes "useful" reçus sur toutes les reviews';

COMMENT ON COLUMN yelp.fact_user_pertinence.total_funny IS
'Total des votes "funny" reçus sur toutes les reviews';

COMMENT ON COLUMN yelp.fact_user_pertinence.total_cool IS
'Total des votes "cool" reçus sur toutes les reviews';

COMMENT ON COLUMN yelp.fact_user_pertinence.nbr_elite_years IS
'Nombre d''années pendant lesquelles l''utilisateur a été élite';

COMMENT ON COLUMN yelp.fact_user_pertinence.last_elite_year IS
'Dernière année où l''utilisateur était élite';

COMMENT ON COLUMN yelp.fact_user_pertinence.nbr_tips IS
'Nombre total de tips laissés par l''utilisateur';

COMMENT ON COLUMN yelp.fact_user_pertinence.total_compliments IS
'Total des compliments reçus sur les tips';

-- Afficher la structure de la table créée
\d+ yelp.fact_user_pertinence

-- Message de confirmation
\echo '✓ Table yelp.fact_user_pertinence créée avec succès'
\echo '✓ 5 index créés pour optimiser les requêtes'
\echo ''
\echo 'Pour vérifier la création :'
\echo '  SELECT count(*) FROM yelp.fact_user_pertinence;'
