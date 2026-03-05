-- ============================================================
-- DATA WAREHOUSE - YELP
-- Oracle 19c | Schema en constellation
-- Contient :
--   DataMart 01 : Analyse des Businesses
--   DataMart 02 : Mesure de la pertinence des Users Reviews
--   Lien        : FAIT_REVIEW (pont entre DM01 et DM02)
-- ============================================================
-- Ordre d'execution :
--   1. SEQUENCES
--   2. DIMENSIONS sans dependances
--   3. FAIT_BUSINESS (DM01)
--   4. FAIT_USER (DM02)
--   5. Dimensions dependantes de FAIT_BUSINESS
--   6. Dimensions dependantes de FAIT_USER
--   7. ALTER TABLE FK sur FAIT_USER
--   8. FAIT_REVIEW (lien entre DM01 et DM02)
--   9. INDEX
--  10. VUES MATERIALISEES
-- ============================================================


-- ============================================================
-- 0. NETTOYAGE (DROP IF EXISTS - style Oracle)
-- Suppression dans l'ordre inverse des dependances
-- ============================================================
BEGIN
    -- Vues materialisees
    FOR v IN (SELECT mview_name FROM user_mviews) LOOP
        EXECUTE IMMEDIATE 'DROP MATERIALIZED VIEW ' || v.mview_name;
    END LOOP;

    -- Tables (ordre inverse des dependances)
    FOR t IN (
        SELECT table_name FROM user_tables
        WHERE table_name IN (
            'FAIT_REVIEW',
            'DIM_PARKING', 'DIM_HORAIRE', 'DIM_CATEGORIE',
            'DIM_USER_ELITE', 'DIM_REVIEW', 'DIM_TIP',
            'FAIT_BUSINESS', 'FAIT_USER',
            'DIM_LOCALISATION', 'DIM_TYPE_BUSINESS', 'DIM_TEMPS'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS';
    END LOOP;

    -- Sequences
    FOR s IN (
        SELECT sequence_name FROM user_sequences
        WHERE sequence_name IN (
            'SEQ_LOCALISATION', 'SEQ_TYPE_BUSINESS', 'SEQ_CATEGORIE',
            'SEQ_TEMPS', 'SEQ_TIP', 'SEQ_ELITE', 'SEQ_REVIEW'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
    END LOOP;
END;
/


-- ============================================================
-- 1. SEQUENCES
-- ============================================================
CREATE SEQUENCE SEQ_LOCALISATION  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_TYPE_BUSINESS START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_CATEGORIE     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_TEMPS         START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_TIP           START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_ELITE         START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_REVIEW        START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- 2. DIMENSIONS SANS DEPENDANCES
-- ============================================================

-- DM01 : Localisation
CREATE TABLE DIM_LOCALISATION (
    localisation_id     NUMBER(10)      PRIMARY KEY,
    city                VARCHAR2(100),
    state               VARCHAR2(50),
    postal_code         VARCHAR2(20),
    address             VARCHAR2(4000),
    latitude            NUMBER(19, 6),
    longitude           NUMBER(19, 6)
);

-- DM01 : Type de business
CREATE TABLE DIM_TYPE_BUSINESS (
    type_id             NUMBER(10)      PRIMARY KEY,
    type_name           VARCHAR2(255)
);

-- DM02 : Temps
CREATE TABLE DIM_TEMPS (
    temps_id            NUMBER(10)      PRIMARY KEY,
    annee               NUMBER(4),
    mois                NUMBER(2),
    trimestre           NUMBER(1),
    jour                NUMBER(2)
);


-- ============================================================
-- 3. TABLE DE FAITS : FAIT_BUSINESS (DM01)
-- Granularite : 1 ligne par business
-- ============================================================
CREATE TABLE FAIT_BUSINESS (
    business_id         VARCHAR2(255)   PRIMARY KEY,
    name                VARCHAR2(4000),
    is_open             NUMBER(1),
    localisation_id     NUMBER(10)      REFERENCES DIM_LOCALISATION(localisation_id),
    type_id             NUMBER(10)      REFERENCES DIM_TYPE_BUSINESS(type_id),
    -- MESURES
    stars               NUMBER(19, 4),
    review_count        NUMBER(10)
);


-- ============================================================
-- 4. TABLE DE FAITS : FAIT_USER (DM02)
-- Granularite : 1 ligne par user
-- Les FK id_review, id_elite, id_tip sont ajoutees via ALTER TABLE
-- apres creation des dimensions dependantes
-- ============================================================
CREATE TABLE FAIT_USER (
    user_id             VARCHAR2(255)   PRIMARY KEY,
    -- Attributs descriptifs
    name                VARCHAR2(255),
    yelping_since       DATE,
    -- Mesure : activite sociale
    friend_count        NUMBER(10),
    -- Mesures : pertinence des reviews
    review_count        NUMBER(10),
    average_stars       NUMBER(19, 4),
    useful              NUMBER(10),
    funny               NUMBER(10),
    cool                NUMBER(10),
    fans                NUMBER(10),
    -- Mesure : annees elite (calculee lors ETL)
    nb_annees_elite     NUMBER(10),
    -- Compliments recus
    compliment_hot      NUMBER(10),
    compliment_more     NUMBER(10),
    compliment_profile  NUMBER(10),
    compliment_cute     NUMBER(10),
    compliment_list     NUMBER(10),
    compliment_note     NUMBER(10),
    compliment_plain    NUMBER(10),
    compliment_cool     NUMBER(10),
    compliment_funny    NUMBER(10),
    compliment_writer   NUMBER(10),
    compliment_photos   NUMBER(10),
    -- Cles etrangeres vers dimensions DM02
    id_review           NUMBER(10),
    id_elite            NUMBER(10),
    id_tip              NUMBER(10),
    temps_id            NUMBER(10)      REFERENCES DIM_TEMPS(temps_id)
);


-- ============================================================
-- 5. DIMENSIONS DEPENDANTES DE FAIT_BUSINESS (DM01)
-- ============================================================

-- Parking (1-1 avec business)
CREATE TABLE DIM_PARKING (
    business_id         VARCHAR2(255)   PRIMARY KEY REFERENCES FAIT_BUSINESS(business_id),
    garage              NUMBER(1),
    street              NUMBER(1),
    lot                 NUMBER(1),
    paid                NUMBER(1),
    validated           NUMBER(1),
    valet               NUMBER(1)
);

-- Horaires (1-1 avec business)
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

-- Categories (1-to-many avec business)
CREATE TABLE DIM_CATEGORIE (
    categorie_id        NUMBER(10)      PRIMARY KEY,
    business_id         VARCHAR2(255)   REFERENCES FAIT_BUSINESS(business_id),
    categorie_name      VARCHAR2(500)
);


-- ============================================================
-- 6. DIMENSIONS DEPENDANTES DE FAIT_USER (DM02)
-- ============================================================

-- Elite (1-1 avec user | id_elite comme PK + nbr_elite_years)
CREATE TABLE DIM_USER_ELITE (
    id_elite            NUMBER(10)      PRIMARY KEY,
    user_id             VARCHAR2(255)   REFERENCES FAIT_USER(user_id),
    nbr_elite_years     NUMBER(10),
    derniere_annee_elite NUMBER(4)
);

-- Reviews (1-to-many avec user | id_review comme PK numerique)
CREATE TABLE DIM_REVIEW (
    id_review           NUMBER(10)      PRIMARY KEY,
    review_id           VARCHAR2(255)   UNIQUE NOT NULL,  -- identifiant source Yelp
    user_id             VARCHAR2(255)   REFERENCES FAIT_USER(user_id),
    nbr_reviews         NUMBER(10),     -- nombre total de reviews du user
    avg_stars           NUMBER(19, 4),  -- moyenne des etoiles du user
    total_useful        NUMBER(10),
    total_funny         NUMBER(10),
    total_cool          NUMBER(10),
    stars               NUMBER(2, 1),   -- note de la review individuelle
    date_review         DATE
);

-- Tips (1-to-many avec user | id_tip comme PK)
CREATE TABLE DIM_TIP (
    id_tip              NUMBER(10)      PRIMARY KEY,
    user_id             VARCHAR2(255)   REFERENCES FAIT_USER(user_id),
    date_tip            DATE,
    compliment_count    NUMBER(10)
);


-- ============================================================
-- 7. CONTRAINTES FK sur FAIT_USER
-- (ajoutees apres creation des dimensions dependantes)
-- ============================================================
ALTER TABLE FAIT_USER ADD CONSTRAINT FK_USER_REVIEW FOREIGN KEY (id_review) REFERENCES DIM_REVIEW(id_review);
ALTER TABLE FAIT_USER ADD CONSTRAINT FK_USER_ELITE  FOREIGN KEY (id_elite)  REFERENCES DIM_USER_ELITE(id_elite);
ALTER TABLE FAIT_USER ADD CONSTRAINT FK_USER_TIP    FOREIGN KEY (id_tip)    REFERENCES DIM_TIP(id_tip);


-- ============================================================
-- 8. TABLE DE FAITS : FAIT_REVIEW (LIEN DM01 <-> DM02)
-- Granularite : 1 ligne par review
-- Relie FAIT_BUSINESS et FAIT_USER
-- ============================================================
CREATE TABLE FAIT_REVIEW (
    review_id           VARCHAR2(255)   PRIMARY KEY,
    business_id         VARCHAR2(255)   REFERENCES FAIT_BUSINESS(business_id),
    user_id             VARCHAR2(255)   REFERENCES FAIT_USER(user_id),
    temps_id            NUMBER(10)      REFERENCES DIM_TEMPS(temps_id),
    -- MESURES
    stars               NUMBER(2, 1),
    date_review         DATE,
    nbr_useful          NUMBER(10),
    nbr_funny           NUMBER(10),
    nbr_cool            NUMBER(10)
);


-- ============================================================
-- 9. INDEX
-- ============================================================

-- Index FAIT_BUSINESS
CREATE INDEX IDX_FB_LOC         ON FAIT_BUSINESS(localisation_id);
CREATE INDEX IDX_FB_TYPE        ON FAIT_BUSINESS(type_id);
CREATE INDEX IDX_FB_STARS       ON FAIT_BUSINESS(stars);
CREATE INDEX IDX_FB_ISOPEN      ON FAIT_BUSINESS(is_open);

-- Index DIM_PARKING
CREATE INDEX IDX_PARK_GARAGE    ON DIM_PARKING(garage);
CREATE INDEX IDX_PARK_STREET    ON DIM_PARKING(street);
CREATE INDEX IDX_PARK_LOT       ON DIM_PARKING(lot);

-- Index DIM_CATEGORIE
CREATE INDEX IDX_CAT_NAME       ON DIM_CATEGORIE(categorie_name);
CREATE INDEX IDX_CAT_BIZ        ON DIM_CATEGORIE(business_id);

-- Index DIM_LOCALISATION
CREATE INDEX IDX_LOC_CITY       ON DIM_LOCALISATION(city);
CREATE INDEX IDX_LOC_STATE      ON DIM_LOCALISATION(state);

-- Index DIM_TYPE_BUSINESS
CREATE INDEX IDX_TYPE_NAME      ON DIM_TYPE_BUSINESS(type_name);

-- Index DIM_HORAIRE
CREATE INDEX IDX_HOR_SAT        ON DIM_HORAIRE(saturday_opening);
CREATE INDEX IDX_HOR_SUN        ON DIM_HORAIRE(sunday_opening);

-- Index FAIT_USER
CREATE INDEX IDX_FU_FRIENDS     ON FAIT_USER(friend_count);
CREATE INDEX IDX_FU_RC          ON FAIT_USER(review_count);
CREATE INDEX IDX_FU_USEFUL      ON FAIT_USER(useful);
CREATE INDEX IDX_FU_ELITE_NB    ON FAIT_USER(nb_annees_elite);
CREATE INDEX IDX_FU_STARS       ON FAIT_USER(average_stars);
CREATE INDEX IDX_FU_FANS        ON FAIT_USER(fans);
CREATE INDEX IDX_FU_SINCE       ON FAIT_USER(yelping_since);
CREATE INDEX IDX_FU_TEMPS       ON FAIT_USER(temps_id);
CREATE INDEX IDX_FU_ID_REV      ON FAIT_USER(id_review);
CREATE INDEX IDX_FU_ID_ELITE    ON FAIT_USER(id_elite);
CREATE INDEX IDX_FU_ID_TIP      ON FAIT_USER(id_tip);

-- Index DIM_REVIEW
CREATE INDEX IDX_REV_USER       ON DIM_REVIEW(user_id);
CREATE INDEX IDX_REV_STARS      ON DIM_REVIEW(avg_stars);
CREATE INDEX IDX_REV_DATE       ON DIM_REVIEW(date_review);
CREATE INDEX IDX_REV_USEFUL     ON DIM_REVIEW(total_useful);

-- Index DIM_TIP
CREATE INDEX IDX_TIP_USER       ON DIM_TIP(user_id);
CREATE INDEX IDX_TIP_DATE       ON DIM_TIP(date_tip);

-- Index DIM_USER_ELITE
CREATE INDEX IDX_ELITE_2024     ON DIM_USER_ELITE(elite_2024);
CREATE INDEX IDX_ELITE_NBR      ON DIM_USER_ELITE(nbr_elite_years);

-- Index DIM_TEMPS
CREATE INDEX IDX_TEMPS_ANNEE    ON DIM_TEMPS(annee);
CREATE INDEX IDX_TEMPS_MOIS     ON DIM_TEMPS(mois);

-- Index FAIT_REVIEW (lien DM01 <-> DM02)
CREATE INDEX IDX_FR_BUSINESS    ON FAIT_REVIEW(business_id);
CREATE INDEX IDX_FR_USER        ON FAIT_REVIEW(user_id);
CREATE INDEX IDX_FR_TEMPS       ON FAIT_REVIEW(temps_id);
CREATE INDEX IDX_FR_STARS       ON FAIT_REVIEW(stars);
CREATE INDEX IDX_FR_DATE        ON FAIT_REVIEW(date_review);


-- ============================================================
-- 10. VUES MATERIALISEES
-- Rafraichir apres chaque chargement ETL :
--   EXEC DBMS_MVIEW.REFRESH('nom_vue');
-- ============================================================

-- ── DM01 : Business ─────────────────────────────────────────

CREATE MATERIALIZED VIEW MV_STARS_BY_CITY
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    l.city, l.state,
    COUNT(f.business_id)    AS nb_business,
    AVG(f.stars)            AS avg_stars,
    MAX(f.stars)            AS max_stars,
    MIN(f.stars)            AS min_stars,
    SUM(f.review_count)     AS total_reviews
FROM FAIT_BUSINESS f
JOIN DIM_LOCALISATION l ON f.localisation_id = l.localisation_id
GROUP BY l.city, l.state;

CREATE MATERIALIZED VIEW MV_STARS_BY_CATEGORIE
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    c.categorie_name,
    COUNT(DISTINCT f.business_id)   AS nb_business,
    AVG(f.stars)                    AS avg_stars,
    SUM(f.review_count)             AS total_reviews
FROM FAIT_BUSINESS f
JOIN DIM_CATEGORIE c ON f.business_id = c.business_id
GROUP BY c.categorie_name;

CREATE MATERIALIZED VIEW MV_STARS_BY_TYPE
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    t.type_name,
    COUNT(f.business_id)    AS nb_business,
    AVG(f.stars)            AS avg_stars,
    SUM(f.review_count)     AS total_reviews
FROM FAIT_BUSINESS f
JOIN DIM_TYPE_BUSINESS t ON f.type_id = t.type_id
GROUP BY t.type_name;

CREATE MATERIALIZED VIEW MV_OPEN_WEEKEND
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    f.business_id, f.name, f.stars, l.city,
    h.saturday_opening, h.saturday_closing,
    h.sunday_opening,   h.sunday_closing
FROM FAIT_BUSINESS f
JOIN DIM_LOCALISATION l ON f.localisation_id = l.localisation_id
JOIN DIM_HORAIRE h      ON f.business_id = h.business_id
WHERE h.saturday_opening IS NOT NULL
   OR h.sunday_opening   IS NOT NULL;

CREATE MATERIALIZED VIEW MV_BUSINESS_PARKING
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    f.business_id, f.name, f.stars, l.city,
    p.garage, p.street, p.lot, p.paid, p.validated, p.valet
FROM FAIT_BUSINESS f
JOIN DIM_LOCALISATION l ON f.localisation_id = l.localisation_id
JOIN DIM_PARKING p      ON f.business_id = p.business_id
WHERE p.garage = 1 OR p.street = 1 OR p.lot = 1;

-- ── DM02 : User Reviews ──────────────────────────────────────

CREATE MATERIALIZED VIEW MV_TOP_USER_QUALITE
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    f.user_id, f.name, f.review_count, f.average_stars, f.useful,
    r.nbr_reviews,
    r.avg_stars           AS avg_stars_reviews,
    r.total_useful        AS total_useful_reviews
FROM FAIT_USER f
JOIN DIM_REVIEW r ON f.id_review = r.id_review
ORDER BY r.total_useful DESC, r.avg_stars DESC;

CREATE MATERIALIZED VIEW MV_TOP_USER_NB_REVIEWS
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT user_id, name, review_count, average_stars, useful, friend_count
FROM FAIT_USER
ORDER BY review_count DESC;

CREATE MATERIALIZED VIEW MV_TOP_USER_ELITE
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    f.user_id, f.name, f.nb_annees_elite,
    f.average_stars, f.review_count, f.useful,
    e.nbr_elite_years,
    e.elite_2022, e.elite_2023, e.elite_2024
FROM FAIT_USER f
JOIN DIM_USER_ELITE e ON f.id_elite = e.id_elite
WHERE e.nbr_elite_years > 0
ORDER BY e.nbr_elite_years DESC;

CREATE MATERIALIZED VIEW MV_TOP_USER_FRIENDS
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT user_id, name, friend_count, review_count, average_stars, useful, fans
FROM FAIT_USER
ORDER BY friend_count DESC;

CREATE MATERIALIZED VIEW MV_USER_SCORE_GLOBAL
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    f.user_id, f.name, f.friend_count, f.review_count,
    f.average_stars, f.useful, f.nb_annees_elite, f.fans,
    (f.useful + f.fans + (f.review_count * f.average_stars) +
    (f.nb_annees_elite * 100) + (f.friend_count / 10)) AS score_pertinence
FROM FAIT_USER f
ORDER BY score_pertinence DESC;

-- ── LIEN DM01 <-> DM02 : Requetes cross-datamart ────────────

-- Quels types de business attirent les meilleurs users ?
CREATE MATERIALIZED VIEW MV_BUSINESS_TOP_USERS
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    t.type_name,
    l.city,
    COUNT(DISTINCT r.user_id)   AS nb_users_distincts,
    AVG(r.stars)                AS avg_stars_reviews,
    SUM(r.nbr_useful)           AS total_useful
FROM FAIT_REVIEW r
JOIN FAIT_BUSINESS b        ON r.business_id = b.business_id
JOIN DIM_TYPE_BUSINESS t    ON b.type_id = t.type_id
JOIN DIM_LOCALISATION l     ON b.localisation_id = l.localisation_id
JOIN FAIT_USER u            ON r.user_id = u.user_id
WHERE u.useful > 100
GROUP BY t.type_name, l.city
ORDER BY total_useful DESC;

-- Quels types de business attirent les users elite ?
CREATE MATERIALIZED VIEW MV_ELITE_USERS_BY_BUSINESS
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    t.type_name,
    l.city,
    COUNT(DISTINCT r.user_id)   AS nb_users_elite,
    AVG(r.stars)                AS avg_stars,
    AVG(u.nb_annees_elite)      AS avg_annees_elite
FROM FAIT_REVIEW r
JOIN FAIT_BUSINESS b        ON r.business_id = b.business_id
JOIN DIM_TYPE_BUSINESS t    ON b.type_id = t.type_id
JOIN DIM_LOCALISATION l     ON b.localisation_id = l.localisation_id
JOIN FAIT_USER u            ON r.user_id = u.user_id
WHERE u.nb_annees_elite > 0
GROUP BY t.type_name, l.city
ORDER BY nb_users_elite DESC;

-- Evolution des stars par ville et par annee
CREATE MATERIALIZED VIEW MV_STARS_EVOLUTION
BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS
SELECT
    l.city, l.state, t.annee,
    COUNT(r.review_id)  AS nb_reviews,
    AVG(r.stars)        AS avg_stars,
    SUM(r.nbr_useful)   AS total_useful
FROM FAIT_REVIEW r
JOIN FAIT_BUSINESS b        ON r.business_id = b.business_id
JOIN DIM_LOCALISATION l     ON b.localisation_id = l.localisation_id
JOIN DIM_TEMPS t            ON r.temps_id = t.temps_id
GROUP BY l.city, l.state, t.annee
ORDER BY l.city, t.annee;