-- ═══════════════════════════════════════════════════════════
-- CRÉATION DU SCHÉMA DATAMART02 - YELP PERTINENCE
-- Schéma en étoile pour l'analyse de la pertinence des utilisateurs
-- ═══════════════════════════════════════════════════════════

-- Créer le schéma
DROP SCHEMA IF EXISTS datamart02 CASCADE;
CREATE SCHEMA datamart02;

COMMENT ON SCHEMA datamart02 IS 'Data Mart pour l''analyse de la pertinence des utilisateurs Yelp';

-- ───────────────────────────────────────────────────────────
-- DIMENSION: USER (Informations utilisateur de base)
-- ───────────────────────────────────────────────────────────
CREATE TABLE datamart02.dim_user (
    user_id         VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    fans            INTEGER DEFAULT 0,
    yelping_since   DATE NOT NULL,

    -- Métadonnées
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_dim_user_name ON datamart02.dim_user(name);
CREATE INDEX idx_dim_user_yelping_since ON datamart02.dim_user(yelping_since);

COMMENT ON TABLE datamart02.dim_user IS 'Dimension utilisateur avec informations de base';
COMMENT ON COLUMN datamart02.dim_user.user_id IS 'Identifiant unique de l''utilisateur';
COMMENT ON COLUMN datamart02.dim_user.fans IS 'Nombre de fans de l''utilisateur';
COMMENT ON COLUMN datamart02.dim_user.yelping_since IS 'Date d''inscription sur Yelp';

-- ───────────────────────────────────────────────────────────
-- DIMENSION: REVIEW (Agrégations des avis)
-- ───────────────────────────────────────────────────────────
CREATE TABLE datamart02.dim_review (
    user_id         VARCHAR(50) PRIMARY KEY,
    nbr_reviews     BIGINT DEFAULT 0,
    avg_stars       DOUBLE PRECISION,
    total_useful    BIGINT DEFAULT 0,
    total_funny     BIGINT DEFAULT 0,
    total_cool      BIGINT DEFAULT 0,

    -- Métadonnées
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_dim_review_user FOREIGN KEY (user_id)
        REFERENCES datamart02.dim_user(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_dim_review_nbr_reviews ON datamart02.dim_review(nbr_reviews);
CREATE INDEX idx_dim_review_avg_stars ON datamart02.dim_review(avg_stars);
CREATE INDEX idx_dim_review_total_useful ON datamart02.dim_review(total_useful);

COMMENT ON TABLE datamart02.dim_review IS 'Dimension agrégée des reviews par utilisateur';
COMMENT ON COLUMN datamart02.dim_review.nbr_reviews IS 'Nombre total de reviews postées';
COMMENT ON COLUMN datamart02.dim_review.avg_stars IS 'Note moyenne donnée (1-5 étoiles)';
COMMENT ON COLUMN datamart02.dim_review.total_useful IS 'Total des votes "useful" reçus';

-- ───────────────────────────────────────────────────────────
-- DIMENSION: ELITE (Statuts élite)
-- ───────────────────────────────────────────────────────────
CREATE TABLE datamart02.dim_elite (
    user_id         VARCHAR(50) PRIMARY KEY,
    nbr_elite_years BIGINT DEFAULT 0,
    last_elite_year INTEGER,

    -- Métadonnées
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_dim_elite_user FOREIGN KEY (user_id)
        REFERENCES datamart02.dim_user(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_dim_elite_nbr_years ON datamart02.dim_elite(nbr_elite_years);
CREATE INDEX idx_dim_elite_last_year ON datamart02.dim_elite(last_elite_year);

COMMENT ON TABLE datamart02.dim_elite IS 'Dimension du statut élite par utilisateur';
COMMENT ON COLUMN datamart02.dim_elite.nbr_elite_years IS 'Nombre d''années avec le statut élite';
COMMENT ON COLUMN datamart02.dim_elite.last_elite_year IS 'Dernière année avec le statut élite';

-- ───────────────────────────────────────────────────────────
-- TABLE DE FAITS: USER PERTINENCE (Table centrale)
-- ───────────────────────────────────────────────────────────
CREATE TABLE datamart02.fact_user_pertinence (
    user_id             VARCHAR(50) PRIMARY KEY,

    -- Métriques de reviews
    nbr_reviews         BIGINT DEFAULT 0,
    avg_stars           DOUBLE PRECISION,
    total_useful        BIGINT DEFAULT 0,
    total_funny         BIGINT DEFAULT 0,
    total_cool          BIGINT DEFAULT 0,

    -- Métriques élite
    nbr_elite_years     BIGINT DEFAULT 0,
    last_elite_year     INTEGER DEFAULT 0,

    -- Métriques additionnelles (pour évolutions futures)
    nbr_tips            BIGINT DEFAULT 0,
    total_compliments   BIGINT DEFAULT 0,

    -- Score de pertinence calculé
    pertinence_score    DOUBLE PRECISION DEFAULT 0,

    -- Métadonnées
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),

    CONSTRAINT fk_fact_pertinence_user FOREIGN KEY (user_id)
        REFERENCES datamart02.dim_user(user_id) ON DELETE CASCADE
);

-- Index pour requêtes analytiques
CREATE INDEX idx_fact_pertinence_score ON datamart02.fact_user_pertinence(pertinence_score DESC);
CREATE INDEX idx_fact_nbr_reviews ON datamart02.fact_user_pertinence(nbr_reviews);
CREATE INDEX idx_fact_nbr_elite_years ON datamart02.fact_user_pertinence(nbr_elite_years);
CREATE INDEX idx_fact_avg_stars ON datamart02.fact_user_pertinence(avg_stars);

COMMENT ON TABLE datamart02.fact_user_pertinence IS 'Table de faits pour l''analyse de la pertinence des utilisateurs';
COMMENT ON COLUMN datamart02.fact_user_pertinence.pertinence_score IS 'Score de pertinence = (useful×3 + cool×2 + funny + elite_years×10)';

-- ───────────────────────────────────────────────────────────
-- VUES ANALYTIQUES
-- ───────────────────────────────────────────────────────────

-- Vue: Top utilisateurs par pertinence
CREATE VIEW datamart02.v_top_users AS
SELECT
    u.user_id,
    u.name,
    u.fans,
    u.yelping_since,
    f.nbr_reviews,
    f.avg_stars,
    f.total_useful,
    f.total_funny,
    f.total_cool,
    f.nbr_elite_years,
    f.pertinence_score,
    RANK() OVER (ORDER BY f.pertinence_score DESC) as rank_pertinence
FROM datamart02.fact_user_pertinence f
JOIN datamart02.dim_user u ON f.user_id = u.user_id
ORDER BY f.pertinence_score DESC;

COMMENT ON VIEW datamart02.v_top_users IS 'Vue des utilisateurs classés par score de pertinence';

-- Vue: Statistiques par catégorie d'activité
CREATE VIEW datamart02.v_user_activity_stats AS
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
    ROUND(AVG(avg_stars)::numeric, 2) as avg_rating,
    SUM(total_useful) as total_useful_votes
FROM datamart02.fact_user_pertinence
GROUP BY 1
ORDER BY avg_pertinence DESC;

COMMENT ON VIEW datamart02.v_user_activity_stats IS 'Statistiques par niveau d''activité des utilisateurs';

-- Vue: Statistiques par années élite
CREATE VIEW datamart02.v_elite_stats AS
SELECT
    nbr_elite_years,
    COUNT(*) as nbr_users,
    ROUND(AVG(pertinence_score)::numeric, 2) as avg_pertinence,
    ROUND(AVG(nbr_reviews)::numeric, 2) as avg_reviews,
    ROUND(AVG(avg_stars)::numeric, 2) as avg_rating
FROM datamart02.fact_user_pertinence
WHERE nbr_elite_years > 0
GROUP BY nbr_elite_years
ORDER BY nbr_elite_years DESC;

COMMENT ON VIEW datamart02.v_elite_stats IS 'Statistiques des utilisateurs élites par nombre d''années';

-- ═══════════════════════════════════════════════════════════
-- AFFICHAGE DES TABLES CRÉÉES
-- ═══════════════════════════════════════════════════════════
\echo '✓ Schéma datamart02 créé avec succès'
\echo ''
\echo 'Tables créées:'
\dt datamart02.*
\echo ''
\echo 'Vues créées:'
\dv datamart02.*
