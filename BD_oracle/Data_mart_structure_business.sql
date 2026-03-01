-- ============================================================
-- DATA MART 01 - BUSINESS
-- Oracle 19c | Schema en etoile
-- Optimisations : index, sequences, vues materialisees
-- Granularite : 1 ligne par business
-- ============================================================


-- ============================================================
-- SEQUENCES
-- ============================================================
CREATE SEQUENCE SEQ_LOCALISATION  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_TYPE_BUSINESS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_FAIT_BUSINESS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_CATEGORIE     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- DIMENSIONS SANS DEPENDANCES (creees en premier)
-- ============================================================

-- DIMENSION : Localisation
CREATE TABLE DIM_LOCALISATION (
    localisation_id     NUMBER(10)      PRIMARY KEY,
    city                VARCHAR2(100),
    state               VARCHAR2(50),
    postal_code         VARCHAR2(20),
    address             VARCHAR2(4000),
    latitude            NUMBER(19, 6),
    longitude           NUMBER(19, 6)
);

-- DIMENSION : Type de business
CREATE TABLE DIM_TYPE_BUSINESS (
    type_id             NUMBER(10)      PRIMARY KEY,
    type_name           VARCHAR2(255)
);


-- ============================================================
-- TABLE DE FAITS (creee avant DIM_HORAIRE, DIM_CATEGORIE, DIM_PARKING)
-- has_parking supprime : remplace par DIM_PARKING
-- ============================================================
CREATE TABLE FAIT_BUSINESS (
    business_id         VARCHAR2(255)   UNIQUE NOT NULL,
    -- Attributs descriptifs denormalises
    name                VARCHAR2(4000),
    is_open             NUMBER(1),
    -- Cles etrangeres
    localisation_id     NUMBER(10)      REFERENCES DIM_LOCALISATION(localisation_id),
    type_id             NUMBER(10)      REFERENCES DIM_TYPE_BUSINESS(type_id),
    -- MESURES
    stars               NUMBER(19, 4),
    review_count        NUMBER(10)
);


-- ============================================================
-- DIMENSION PARKING (1 ligne par business | business_id comme PK)
-- Attributs detailles du parking issus des donnees Yelp
-- ============================================================
CREATE TABLE DIM_PARKING (
    business_id         VARCHAR2(255)   PRIMARY KEY REFERENCES FAIT_BUSINESS(business_id),
    garage              NUMBER(1),      -- 0 ou 1
    street              NUMBER(1),      -- 0 ou 1
    lot                 NUMBER(1),      -- 0 ou 1
    paid                NUMBER(1),      -- 0 ou 1
    validated           NUMBER(1),      -- 0 ou 1
    valet               NUMBER(1)       -- 0 ou 1
);


-- ============================================================
-- DIMENSION HORAIRE (1 ligne par business | business_id comme PK)
-- ============================================================
CREATE TABLE DIM_HORAIRE (
    business_id           VARCHAR2(255)  PRIMARY KEY REFERENCES FAIT_BUSINESS(business_id),
    monday_opening        VARCHAR2(10),
    monday_closing        VARCHAR2(10),
    tuesday_opening       VARCHAR2(10),
    tuesday_closing       VARCHAR2(10),
    wednesday_opening     VARCHAR2(10),
    wednesday_closing     VARCHAR2(10),
    thursday_opening      VARCHAR2(10),
    thursday_closing      VARCHAR2(10),
    friday_opening        VARCHAR2(10),
    friday_closing        VARCHAR2(10),
    saturday_opening      VARCHAR2(10),
    saturday_closing      VARCHAR2(10),
    sunday_opening        VARCHAR2(10),
    sunday_closing        VARCHAR2(10)
);


-- ============================================================
-- DIMENSION CATEGORIE (relation 1-to-many)
-- 1 business -> plusieurs categories
-- Exemple :
--   business_id = 'biz_001' | categorie_name = 'Mexicain'
--   business_id = 'biz_001' | categorie_name = 'Algerien'
-- ============================================================
CREATE TABLE DIM_CATEGORIE (
    categorie_id        NUMBER(10)      PRIMARY KEY,
    business_id         VARCHAR2(255)   REFERENCES FAIT_BUSINESS(business_id),
    categorie_name      VARCHAR2(500)
);


-- ============================================================
-- INDEX
-- ============================================================

-- Index sur les cles etrangeres de FAIT_BUSINESS
CREATE INDEX IDX_FAIT_LOC      ON FAIT_BUSINESS(localisation_id);
CREATE INDEX IDX_FAIT_TYPE     ON FAIT_BUSINESS(type_id);

-- Index sur les mesures
CREATE INDEX IDX_FAIT_STARS    ON FAIT_BUSINESS(stars);
CREATE INDEX IDX_FAIT_ISOPEN   ON FAIT_BUSINESS(is_open);

-- Index sur DIM_PARKING
CREATE INDEX IDX_PARK_GARAGE   ON DIM_PARKING(garage);
CREATE INDEX IDX_PARK_STREET   ON DIM_PARKING(street);
CREATE INDEX IDX_PARK_LOT      ON DIM_PARKING(lot);

-- Index sur DIM_CATEGORIE
CREATE INDEX IDX_CAT_NAME      ON DIM_CATEGORIE(categorie_name);
CREATE INDEX IDX_CAT_BIZ       ON DIM_CATEGORIE(business_id);

-- Index sur DIM_LOCALISATION
CREATE INDEX IDX_LOC_CITY      ON DIM_LOCALISATION(city);
CREATE INDEX IDX_LOC_STATE     ON DIM_LOCALISATION(state);

-- Index sur DIM_TYPE_BUSINESS
CREATE INDEX IDX_TYPE_NAME     ON DIM_TYPE_BUSINESS(type_name);

-- Index sur DIM_HORAIRE
CREATE INDEX IDX_HOR_SAT       ON DIM_HORAIRE(saturday_opening);
CREATE INDEX IDX_HOR_SUN       ON DIM_HORAIRE(sunday_opening);


-- ============================================================
-- VUES MATERIALISEES
-- Rafraichir apres chaque chargement ETL :
--   EXEC DBMS_MVIEW.REFRESH('nom_vue');
-- ============================================================

-- Stars moyennes par ville
CREATE MATERIALIZED VIEW MV_STARS_BY_CITY
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    l.city,
    l.state,
    COUNT(f.business_id)    AS nb_business,
    AVG(f.stars)        AS avg_stars,
    MAX(f.stars)        AS max_stars,
    MIN(f.stars)        AS min_stars,
    SUM(f.review_count) AS total_reviews
FROM FAIT_BUSINESS f
JOIN DIM_LOCALISATION l ON f.localisation_id = l.localisation_id
GROUP BY l.city, l.state;

-- Stars moyennes par categorie
CREATE MATERIALIZED VIEW MV_STARS_BY_CATEGORIE
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    c.categorie_name,
    COUNT(DISTINCT f.business_id)   AS nb_business,
    AVG(f.stars)                AS avg_stars,
    SUM(f.review_count)         AS total_reviews
FROM FAIT_BUSINESS f
JOIN DIM_CATEGORIE c ON f.business_id = c.business_id
GROUP BY c.categorie_name;

-- Stars moyennes par type de business
CREATE MATERIALIZED VIEW MV_STARS_BY_TYPE
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    t.type_name,
    COUNT(f.business_id)    AS nb_business,
    AVG(f.stars)        AS avg_stars,
    SUM(f.review_count) AS total_reviews
FROM FAIT_BUSINESS f
JOIN DIM_TYPE_BUSINESS t ON f.type_id = t.type_id
GROUP BY t.type_name;

-- Businesses ouverts le weekend
CREATE MATERIALIZED VIEW MV_OPEN_WEEKEND
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    f.business_id,
    f.name,
    f.stars,
    l.city,
    h.saturday_opening,
    h.saturday_closing,
    h.sunday_opening,
    h.sunday_closing
FROM FAIT_BUSINESS f
JOIN DIM_LOCALISATION l ON f.localisation_id = l.localisation_id
JOIN DIM_HORAIRE h      ON f.business_id = h.business_id
WHERE h.saturday_opening IS NOT NULL
   OR h.sunday_opening   IS NOT NULL;

-- Businesses avec parking disponible
CREATE MATERIALIZED VIEW MV_BUSINESS_PARKING
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    f.business_id,
    f.name,
    f.stars,
    l.city,
    p.garage,
    p.street,
    p.lot,
    p.paid,
    p.validated,
    p.valet
FROM FAIT_BUSINESS f
JOIN DIM_LOCALISATION l ON f.localisation_id = l.localisation_id
JOIN DIM_PARKING p      ON f.business_id = p.business_id
WHERE p.garage = 1 OR p.street = 1 OR p.lot = 1;