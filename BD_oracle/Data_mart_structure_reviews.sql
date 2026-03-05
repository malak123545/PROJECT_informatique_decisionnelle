-- ============================================================
-- DATA MART 02 - USER REVIEWS
-- Oracle 19c | Schema en etoile
-- Cas d'etude : Mesure de la pertinence des reviews
-- Questions dashboard :
--   1. Meilleur user par qualite des reviews (stars + useful)
--   2. Meilleur user par nombre de reviews
--   3. Meilleur user par nombre d'annees elite
--   4. Meilleur user par nombre d'amis
-- ============================================================


-- ============================================================
-- SEQUENCES
-- ============================================================
CREATE SEQUENCE SEQ_TEMPS   START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_TIP     START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_ELITE   START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE SEQ_REVIEW  START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;


-- ============================================================
-- DIMENSION SANS DEPENDANCE (creee en premier)
-- ============================================================

-- DIMENSION : Temps
CREATE TABLE DIM_TEMPS (
    temps_id            NUMBER(10)      PRIMARY KEY,
    annee               NUMBER(4),
    mois                NUMBER(2),
    trimestre           NUMBER(1),
    jour                NUMBER(2)
);


-- ============================================================
-- TABLE DE FAITS : FAIT_USER
-- Granularite : 1 ligne par user
-- Contient toutes les mesures pour repondre aux 4 questions
-- ============================================================
CREATE TABLE FAIT_USER (
    user_id             VARCHAR2(255)   PRIMARY KEY,
    -- Attributs descriptifs
    name                VARCHAR2(255),
    yelping_since       DATE,
    -- Question 4 : meilleur par nombre d'amis
    friend_count        NUMBER(10),
    -- Question 2 : meilleur par nombre de reviews
    review_count        NUMBER(10),
    -- Question 1 : meilleur par qualite des reviews
    average_stars       NUMBER(19, 4),
    useful              NUMBER(10),     -- total votes utiles recus
    funny               NUMBER(10),
    cool                NUMBER(10),
    fans                NUMBER(10),
    -- Question 3 : meilleur par annees elite (colonne calculee)
    nb_annees_elite     NUMBER(10),     -- total annees elite du user
    -- Compliments recus (mesure de popularite)
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
    -- Cles etrangeres vers les dimensions
    id_review           NUMBER(10),
    id_elite            NUMBER(10),
    id_tip              NUMBER(10),
    temps_id            NUMBER(10)      REFERENCES DIM_TEMPS(temps_id)
);


-- ============================================================
-- DIMENSION ELITE (1-1 avec user)
-- id_elite comme PK, nbr_elite_years + annees en colonnes booleennes
-- ============================================================
CREATE TABLE DIM_USER_ELITE (
    id_elite            NUMBER(10)      PRIMARY KEY,
    user_id             VARCHAR2(255)   REFERENCES FAIT_USER(user_id),
    nbr_elite_years     NUMBER(10),     -- nombre total d'annees elite
    elite_2015          NUMBER(1),
    elite_2016          NUMBER(1),
    elite_2017          NUMBER(1),
    elite_2018          NUMBER(1),
    elite_2019          NUMBER(1),
    elite_2020          NUMBER(1),
    elite_2021          NUMBER(1),
    elite_2022          NUMBER(1),
    elite_2023          NUMBER(1),
    elite_2024          NUMBER(1)
);


-- ============================================================
-- DIMENSION REVIEW (1-to-many : N reviews pour 1 user)
-- id_review comme PK, agregats calcules par user
-- ============================================================
CREATE TABLE DIM_REVIEW (
    id_review           NUMBER(10)      PRIMARY KEY,
    review_id           VARCHAR2(255)   UNIQUE NOT NULL,  -- identifiant source Yelp
    user_id             VARCHAR2(255)   REFERENCES FAIT_USER(user_id),
    nbr_reviews         NUMBER(10),     -- nombre total de reviews du user
    avg_stars           NUMBER(19, 4),  -- moyenne des etoiles du user
    total_useful        NUMBER(10),     -- total votes useful recus
    total_funny         NUMBER(10),     -- total votes funny recus
    total_cool          NUMBER(10),     -- total votes cool recus
    stars               NUMBER(2, 1),   -- note de la review individuelle
    date_review         DATE
);


-- ============================================================
-- DIMENSION TIP (1-to-many : N tips pour 1 user)
-- id_tip comme PK
-- ============================================================
CREATE TABLE DIM_TIP (
    id_tip              NUMBER(10)      PRIMARY KEY,
    user_id             VARCHAR2(255)   REFERENCES FAIT_USER(user_id),
    date_tip            DATE,
    compliment_count    NUMBER(10)
);


-- ============================================================
-- CONTRAINTES FK sur FAIT_USER (apres creation des dimensions)
-- ============================================================
ALTER TABLE FAIT_USER ADD CONSTRAINT FK_USER_REVIEW  FOREIGN KEY (id_review) REFERENCES DIM_REVIEW(id_review);
ALTER TABLE FAIT_USER ADD CONSTRAINT FK_USER_ELITE   FOREIGN KEY (id_elite)  REFERENCES DIM_USER_ELITE(id_elite);
ALTER TABLE FAIT_USER ADD CONSTRAINT FK_USER_TIP     FOREIGN KEY (id_tip)    REFERENCES DIM_TIP(id_tip);


-- ============================================================
-- INDEX
-- ============================================================

-- Index sur les 4 criteres de classement principaux
CREATE INDEX IDX_USER_FRIENDS   ON FAIT_USER(friend_count);
CREATE INDEX IDX_USER_RC        ON FAIT_USER(review_count);
CREATE INDEX IDX_USER_USEFUL    ON FAIT_USER(useful);
CREATE INDEX IDX_USER_ELITE_NB  ON FAIT_USER(nb_annees_elite);

-- Index supplementaires sur FAIT_USER
CREATE INDEX IDX_USER_STARS     ON FAIT_USER(average_stars);
CREATE INDEX IDX_USER_FANS      ON FAIT_USER(fans);
CREATE INDEX IDX_USER_SINCE     ON FAIT_USER(yelping_since);
CREATE INDEX IDX_USER_TEMPS     ON FAIT_USER(temps_id);
CREATE INDEX IDX_USER_ID_REV    ON FAIT_USER(id_review);
CREATE INDEX IDX_USER_ID_ELITE  ON FAIT_USER(id_elite);
CREATE INDEX IDX_USER_ID_TIP    ON FAIT_USER(id_tip);

-- Index sur DIM_REVIEW
CREATE INDEX IDX_REV_USER       ON DIM_REVIEW(user_id);
CREATE INDEX IDX_REV_STARS      ON DIM_REVIEW(avg_stars);
CREATE INDEX IDX_REV_DATE       ON DIM_REVIEW(date_review);
CREATE INDEX IDX_REV_USEFUL     ON DIM_REVIEW(total_useful);

-- Index sur DIM_TIP
CREATE INDEX IDX_TIP_USER       ON DIM_TIP(user_id);
CREATE INDEX IDX_TIP_DATE       ON DIM_TIP(date_tip);

-- Index sur DIM_USER_ELITE
CREATE INDEX IDX_ELITE_2024     ON DIM_USER_ELITE(elite_2024);
CREATE INDEX IDX_ELITE_NBR      ON DIM_USER_ELITE(nbr_elite_years);

-- Index sur DIM_TEMPS
CREATE INDEX IDX_TEMPS_ANNEE    ON DIM_TEMPS(annee);
CREATE INDEX IDX_TEMPS_MOIS     ON DIM_TEMPS(mois);


-- ============================================================
-- VUES MATERIALISEES
-- Rafraichir apres chaque chargement ETL :
--   EXEC DBMS_MVIEW.REFRESH('nom_vue');
-- ============================================================

-- Question 1 : Top users par qualite des reviews (stars + useful)
CREATE MATERIALIZED VIEW MV_TOP_USER_QUALITE
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    f.user_id,
    f.name,
    f.review_count,
    f.average_stars,
    f.useful,
    r.nbr_reviews,
    r.avg_stars           AS avg_stars_reviews,
    r.total_useful        AS total_useful_reviews
FROM FAIT_USER f
JOIN DIM_REVIEW r ON f.id_review = r.id_review
ORDER BY r.total_useful DESC, r.avg_stars DESC;

-- Question 2 : Top users par nombre de reviews
CREATE MATERIALIZED VIEW MV_TOP_USER_NB_REVIEWS
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    user_id,
    name,
    review_count,
    average_stars,
    useful,
    friend_count
FROM FAIT_USER
ORDER BY review_count DESC;

-- Question 3 : Top users par nombre d'annees elite
CREATE MATERIALIZED VIEW MV_TOP_USER_ELITE
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    f.user_id,
    f.name,
    f.nb_annees_elite,
    f.average_stars,
    f.review_count,
    f.useful,
    e.nbr_elite_years,
    e.elite_2022,
    e.elite_2023,
    e.elite_2024
FROM FAIT_USER f
JOIN DIM_USER_ELITE e ON f.id_elite = e.id_elite
WHERE e.nbr_elite_years > 0
ORDER BY e.nbr_elite_years DESC;

-- Question 4 : Top users par nombre d'amis
CREATE MATERIALIZED VIEW MV_TOP_USER_FRIENDS
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    user_id,
    name,
    friend_count,
    review_count,
    average_stars,
    useful,
    fans
FROM FAIT_USER
ORDER BY friend_count DESC;

-- Vue globale : score de pertinence combine (toutes les questions)
CREATE MATERIALIZED VIEW MV_USER_SCORE_GLOBAL
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
    f.user_id,
    f.name,
    f.friend_count,
    f.review_count,
    f.average_stars,
    f.useful,
    f.nb_annees_elite,
    f.fans,
    -- Score global normalise (pour le dashboard)
    (f.useful + f.fans + (f.review_count * f.average_stars) +
    (f.nb_annees_elite * 100) + (f.friend_count / 10))
                                AS score_pertinence
FROM FAIT_USER f
ORDER BY score_pertinence DESC;