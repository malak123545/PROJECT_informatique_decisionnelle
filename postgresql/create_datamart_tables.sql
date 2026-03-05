-- ═══════════════════════════════════════════════════════════
-- CRÉATION DES TABLES DATAMART02 dans le schéma YELP
-- Préfixe: dm02_ (DataMart 02)
-- ═══════════════════════════════════════════════════════════

\echo ''
\echo '═══════════════════════════════════════════════════════════'
\echo '   CRÉATION DES TABLES DATAMART02'
\echo '═══════════════════════════════════════════════════════════'
\echo ''

-- Définir le schéma de travail
SET search_path TO yelp;

-- Supprimer les tables si elles existent (ordre important pour les FK)
DROP TABLE IF EXISTS yelp.dm02_fact_user_pertinence CASCADE;
DROP TABLE IF EXISTS yelp.dm02_dim_elite CASCADE;
DROP TABLE IF EXISTS yelp.dm02_dim_review CASCADE;
DROP TABLE IF EXISTS yelp.dm02_dim_user CASCADE;

-- ───────────────────────────────────────────────────────────
-- DIMENSION: USER (Informations utilisateur de base)
-- ───────────────────────────────────────────────────────────
\echo 'Création: dm02_dim_user...'

CREATE TABLE yelp.dm02_dim_user (
    user_id         VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    fans            INTEGER DEFAULT 0,
    yelping_since   DATE NOT NULL,
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_dm02_dim_user_name ON yelp.dm02_dim_user(name);
CREATE INDEX idx_dm02_dim_user_yelping ON yelp.dm02_dim_user(yelping_since);

COMMENT ON TABLE yelp.dm02_dim_user IS 'DataMart02: Dimension utilisateur';

-- ───────────────────────────────────────────────────────────
-- DIMENSION: REVIEW (Agrégations des avis)
-- ───────────────────────────────────────────────────────────
\echo 'Création: dm02_dim_review...'

CREATE TABLE yelp.dm02_dim_review (
    user_id         VARCHAR(50) PRIMARY KEY,
    nbr_reviews     BIGINT DEFAULT 0,
    avg_stars       DOUBLE PRECISION,
    total_useful    BIGINT DEFAULT 0,
    total_funny     BIGINT DEFAULT 0,
    total_cool      BIGINT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_dm02_review_user FOREIGN KEY (user_id)
        REFERENCES yelp.dm02_dim_user(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_dm02_review_nbr ON yelp.dm02_dim_review(nbr_reviews);
CREATE INDEX idx_dm02_review_useful ON yelp.dm02_dim_review(total_useful);

COMMENT ON TABLE yelp.dm02_dim_review IS 'DataMart02: Dimension reviews agrégées';

-- ───────────────────────────────────────────────────────────
-- DIMENSION: ELITE (Statuts élite)
-- ───────────────────────────────────────────────────────────
\echo 'Création: dm02_dim_elite...'

CREATE TABLE yelp.dm02_dim_elite (
    user_id         VARCHAR(50) PRIMARY KEY,
    nbr_elite_years BIGINT DEFAULT 0,
    last_elite_year INTEGER,
    created_at      TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_dm02_elite_user FOREIGN KEY (user_id)
        REFERENCES yelp.dm02_dim_user(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_dm02_elite_nbr_years ON yelp.dm02_dim_elite(nbr_elite_years);

COMMENT ON TABLE yelp.dm02_dim_elite IS 'DataMart02: Dimension statut élite';

-- ───────────────────────────────────────────────────────────
-- TABLE DE FAITS: USER PERTINENCE (Table centrale)
-- ───────────────────────────────────────────────────────────
\echo 'Création: dm02_fact_user_pertinence...'

CREATE TABLE yelp.dm02_fact_user_pertinence (
    user_id             VARCHAR(50) PRIMARY KEY,
    nbr_reviews         BIGINT DEFAULT 0,
    avg_stars           DOUBLE PRECISION,
    total_useful        BIGINT DEFAULT 0,
    total_funny         BIGINT DEFAULT 0,
    total_cool          BIGINT DEFAULT 0,
    nbr_elite_years     BIGINT DEFAULT 0,
    last_elite_year     INTEGER DEFAULT 0,
    nbr_tips            BIGINT DEFAULT 0,
    total_compliments   BIGINT DEFAULT 0,
    pertinence_score    DOUBLE PRECISION DEFAULT 0,
    created_at          TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_dm02_fact_user FOREIGN KEY (user_id)
        REFERENCES yelp.dm02_dim_user(user_id) ON DELETE CASCADE
);

-- Index pour requêtes analytiques
CREATE INDEX idx_dm02_fact_score ON yelp.dm02_fact_user_pertinence(pertinence_score DESC);
CREATE INDEX idx_dm02_fact_reviews ON yelp.dm02_fact_user_pertinence(nbr_reviews);
CREATE INDEX idx_dm02_fact_elite ON yelp.dm02_fact_user_pertinence(nbr_elite_years);

COMMENT ON TABLE yelp.dm02_fact_user_pertinence IS 'DataMart02: Table de faits - pertinence utilisateurs';
COMMENT ON COLUMN yelp.dm02_fact_user_pertinence.pertinence_score IS 'Score = (useful×3 + cool×2 + funny + elite_years×10)';

-- ───────────────────────────────────────────────────────────
-- VUES ANALYTIQUES
-- ───────────────────────────────────────────────────────────
\echo 'Création: Vues analytiques...'

-- Vue: Top utilisateurs
CREATE OR REPLACE VIEW yelp.dm02_v_top_users AS
SELECT
    u.user_id,
    u.name,
    u.fans,
    u.yelping_since,
    f.nbr_reviews,
    f.avg_stars,
    f.total_useful,
    f.nbr_elite_years,
    f.pertinence_score,
    RANK() OVER (ORDER BY f.pertinence_score DESC) as rank_pertinence
FROM yelp.dm02_fact_user_pertinence f
JOIN yelp.dm02_dim_user u ON f.user_id = u.user_id
ORDER BY f.pertinence_score DESC;

-- Vue: Statistiques par activité
CREATE OR REPLACE VIEW yelp.dm02_v_activity_stats AS
SELECT
    CASE
        WHEN nbr_reviews = 1 THEN '1 review'
        WHEN nbr_reviews BETWEEN 2 AND 10 THEN '2-10 reviews'
        WHEN nbr_reviews BETWEEN 11 AND 50 THEN '11-50 reviews'
        WHEN nbr_reviews BETWEEN 51 AND 100 THEN '51-100 reviews'
        WHEN nbr_reviews > 100 THEN '100+ reviews'
    END as activity_category,
    COUNT(*) as nbr_users,
    ROUND(AVG(pertinence_score)::numeric, 2) as avg_pertinence,
    ROUND(AVG(avg_stars)::numeric, 2) as avg_rating
FROM yelp.dm02_fact_user_pertinence
GROUP BY 1
ORDER BY avg_pertinence DESC;

-- Vue: Statistiques élites
CREATE OR REPLACE VIEW yelp.dm02_v_elite_stats AS
SELECT
    nbr_elite_years,
    COUNT(*) as nbr_users,
    ROUND(AVG(pertinence_score)::numeric, 2) as avg_pertinence,
    ROUND(AVG(nbr_reviews)::numeric, 2) as avg_reviews
FROM yelp.dm02_fact_user_pertinence
WHERE nbr_elite_years > 0
GROUP BY nbr_elite_years
ORDER BY nbr_elite_years DESC;

\echo ''
\echo '✓ Tables et vues créées avec succès'
\echo ''
\echo 'Tables DataMart02:'
\dt yelp.dm02_*
\echo ''
\echo 'Vues DataMart02:'
\dv yelp.dm02_*
\echo ''
