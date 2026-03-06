# Projet Informatique Décisionnelle — Yelp Dataset
**Master 2 BDIA — Année 2025-2026**
**Groupe :** 
**Date de rendu :** 8 mars 2026

---

> 📌 **Comment utiliser ce document**
> Ce carnet de bord évolue tout au long du projet. Remplissez chaque section au fur et à mesure. Il servira de base directe pour la rédaction du rapport PDF final (min. 15 pages).

---

## 1. Analyse du sujet & Cas d'usage


### 1.1 Cas d'usage choisi

> *Décrivez en quelques phrases l'angle d'analyse retenu. Ex : "Nous analysons la qualité des commerces de restauration aux États-Unis pour aider un investisseur à identifier les villes les plus attractives."*

```
Nous sommes une équipe BI au sein de Yelp. Notre mission est triple : (1) identifier et récompenser les utilisateurs qui font la qualité de la plateforme pour maximiser la collecte de données terrain, (2) fournir aux collectivités et investisseurs une cartographie des opportunités commerciales par métropole, (3) détecter les signaux faibles de dégradation de la qualité d'un commerce via l'évolution temporelle de ses notes.
```

### 1.2 Types d'utilisateurs cibles (personas)

| Persona | Rôle | Besoin principal |
|---------|------|-----------------|
| CDO (Chief Data Officer) | Décideur technique & sponsor | Valider l'architecture DW, la fiabilité des métriques et porter les idées aux directions métier |
| CMO (Chief Marketing Officer) | Décideur marketing | Valider et financer le système de récompense des users contributeurs |
| CRO (Chief Revenue Officer) | Décideur commercial | Valider la cartographie des métropoles comme nouveau produit B2B vendable |
### 1.3 Scénarios d'analyse

> *Listez 3 à 5 scénarios concrets que votre DW doit permettre d'explorer.*
- **Scénario 1 — Récompenser les users contributeurs** : Identifier les utilisateurs les plus utiles à la plateforme via un score de pertinence (qualité des reviews, statut élite, réseau d'amis) pour mettre en place un système de récompense qui maximise la collecte de données terrain sans agents coûteux.

- **Scénario 2 — Cartographie des opportunités commerciales par métropole** : Pour chaque ville/région, identifier les types de commerces qui performent bien et ceux qui manquent, afin de fournir une analyse B2B vendable aux collectivités territoriales, franchiseurs et investisseurs souhaitant s'implanter.

- **Scénario 3 — Détection des signaux faibles de dégradation** : Surveiller l'évolution temporelle des notes par commerce pour détecter les tendances négatives avant qu'elles ne deviennent critiques, permettant aux équipes terrain d'intervenir proactivement et de protéger l'image de qualité de Yelp.

- **Scénario 4 — Analyse des émotions et satisfaction client** : Exploiter les votes useful/funny/cool et l'évolution des étoiles dans le temps comme proxy émotionnel pour mesurer la satisfaction globale des clients par type de commerce et par zone géographique, avec comme perspective d'enrichissement une analyse NLP sur les textes de reviews.


### 1.4 Exploration préliminaire des données

> *Notez ici vos observations après avoir exploré les données : volume, qualité, valeurs manquantes, distributions remarquables...*

```

=== Statistiques Business ===
[info] Nombre total de business: 150346 
[info] nombre des business restaurant et tout 75 000

sur json ya 198 7897 de users
```

---

## 2. Spécification du schéma du Data Warehouse

### 2.1 Approche choisie

- [x] **Kimball** (bottom-up, orientée data marts) ✅
- [ ] Inmon

**Justification :**
```
[À compléter — ex : "Approche Kimball retenue car nous partons de besoins
analytiques précis par domaine (business, users), ce qui favorise
des data marts indépendants et des requêtes optimisées."]
```

### 2.2 Architecture globale — Constellation

```
         DIM_LOCALISATION      DIM_TYPE_BUSINESS
               │                      │
               └──────────────────────┘
                          │
                    FAIT_BUSINESS (DM01)
                          │
                     FAIT_REVIEW  ◄──── DIM_TEMPS
                          │
                    FAIT_USER (DM02)
                          │
          ┌───────────────┼───────────────┐
     DIM_REVIEW    DIM_USER_ELITE      DIM_TIP
```

> *Ajoutez ici un diagramme plus détaillé (image ou schéma ASCII) si nécessaire.*

### 2.3 Data Mart 01 — Business

**Table de faits : `FAIT_BUSINESS`**

| Colonne | Type | Rôle |
|---------|------|------|
| business_id | VARCHAR2(255) | Clé primaire |
| stars | NUMBER | Mesure : note moyenne |
| review_count | NUMBER | Mesure : nombre d'avis |
| is_open | NUMBER(1) | Mesure : ouvert/fermé |
| localisation_id | FK | → DIM_LOCALISATION |
| type_id | FK | → DIM_TYPE_BUSINESS |

**Dimensions associées :** DIM_LOCALISATION · DIM_TYPE_BUSINESS · DIM_CATEGORIE · DIM_HORAIRE · DIM_PARKING

**Granularité :** 1 ligne par commerce

**Justification des choix :**
```
[À compléter — ex : "DIM_CATEGORIE en relation 1-to-many car un commerce
peut appartenir à plusieurs catégories. DIM_PARKING dénormalisé en
dimension séparée pour simplifier les requêtes de filtrage."]
```

### 2.4 Data Mart 02 — User Reviews

**Table de faits : `FAIT_USER`**

| Colonne | Type | Rôle |
|---------|------|------|
| user_id | VARCHAR2(255) | Clé primaire |
| review_count | NUMBER | Mesure : nb de reviews |
| average_stars | NUMBER | Mesure : note moyenne donnée |
| useful | NUMBER | Mesure : votes utiles reçus |
| nb_annees_elite | NUMBER | Mesure : ancienneté élite |
| friend_count | NUMBER | Mesure : réseau social |

**Dimensions associées :** DIM_REVIEW · DIM_USER_ELITE · DIM_TIP · DIM_TEMPS

**Granularité :** 1 ligne par utilisateur

**Justification des choix :**
```
[À compléter]
```

### 2.5 Table de liaison — `FAIT_REVIEW`

> *Pont entre DM01 et DM02 — permet les analyses croisées business × user*

**Granularité :** 1 ligne par avis (review)

**Justification :**
```
[À compléter — ex : "FAIT_REVIEW permet de répondre à des questions
cross-datamart comme : quels types de commerces attirent les meilleurs users ?"]
```

---

## 3. ETL — Spark / Scala

### 3.1 Architecture ETL

```
PostgreSQL (user, review)  ──┐
JSON (business, checkin)   ──┤──► Spark ETL ──► Oracle DW
CSV (tip)                  ──┘
```

### 3.2 Choix techniques

| Composant | Technologie | Version | Justification |
|-----------|------------|---------|---------------|
| ETL | Spark / Scala | `[À compléter]` | `[À compléter]` |
| DW cible | Oracle | 19c | Imposé |
| Source SGBD | PostgreSQL | 10.23 | Imposé |
| Build tool | sbt | `[À compléter]` | `[À compléter]` |

### 3.3 Étapes de transformation

> *Décrivez les transformations appliquées pour chaque source.*

**Business (JSON → FAIT_BUSINESS + dimensions)**
```
[À compléter — ex : parsing des attributs JSON imbriqués (parking, horaires),
explosion des catégories en lignes séparées pour DIM_CATEGORIE...]
```

**Users (PostgreSQL → FAIT_USER + DIM_USER_ELITE)**
```
[À compléter — ex : calcul de nb_annees_elite depuis yelp.elite,
comptage des amis depuis yelp.friend...]
```

**Reviews (PostgreSQL → FAIT_REVIEW + DIM_REVIEW)**
```
[À compléter]
```

**Tips (CSV → DIM_TIP)**
```
[À compléter]
```

### 3.4 Gestion des types Oracle (OracleDialect)

> *Mapping Spark → Oracle implémenté via JdbcDialect*

```scala
// Extrait du code ETL
class OracleDialect extends JdbcDialect {
  override def getJDBCType(dt: DataType): Option[JdbcType] = dt match {
    case BooleanType => Some(JdbcType("NUMBER(1)", java.sql.Types.INTEGER))
    case StringType  => Some(JdbcType("VARCHAR2(4000)", java.sql.Types.VARCHAR))
    // ...
  }
}
```

### 3.5 Optimisations Spark utilisées

- [ ] Partitionnement par colonne numérique (`spark_partition`)
- [ ] Parallélisation des lectures JDBC (`numPartitions`)
- [ ] `[Autre optimisation]`

### 3.6 Problèmes rencontrés & solutions

| Problème | Solution appliquée |
|----------|--------------------|
| `[À compléter]` | `[À compléter]` |
| | |

---

## 4. Requêtes d'analyse

> *Pour chaque requête : contexte, code SQL, résultats obtenus, interprétation.*

### 4.1 Requête 1 — `[Titre]`

**Objectif :** `[À compléter]`

**Type :** `[ ] GROUP BY CUBE` `[ ] ROLLUP` `[ ] RANK` `[ ] Fenêtre temporelle` `[ ] Autre`

```sql
-- [Coller la requête ici]
```

**Résultats :**
```
[Tableau ou description des résultats]
```

**Interprétation :**
```
[À compléter]
```

---

### 4.2 Requête 2 — `[Titre]`

**Objectif :** `[À compléter]`

```sql
-- [Coller la requête ici]
```

**Résultats :**
```
[À compléter]
```

**Interprétation :**
```
[À compléter]
```

---

### 4.3 Requête 3 — `[Titre]`

**Objectif :** `[À compléter]`

```sql
-- [Coller la requête ici]
```

**Résultats & interprétation :**
```
[À compléter]
```

---

### 4.4 Requête 4 — `[Titre]`

```sql
-- [À compléter]
```

---

### 4.5 Requête 5 — `[Titre]`

```sql
-- [À compléter]
```

---

## 5. Évaluation des performances

### 5.1 Méthodologie

```
[Décrivez comment vous avez mesuré les temps d'exécution et l'occupation mémoire.
Ex : EXPLAIN PLAN Oracle, mesure avant/après index, avant/après vues matérialisées...]
```

### 5.2 Résultats

| Requête | Sans optimisation | Avec index | Avec vue matérialisée | Gain |
|---------|-----------------|------------|----------------------|------|
| Requête 1 | `[ms]` | `[ms]` | `[ms]` | `[%]` |
| Requête 2 | `[ms]` | `[ms]` | `[ms]` | `[%]` |
| Requête 3 | `[ms]` | `[ms]` | `[ms]` | `[%]` |

### 5.3 Analyse

```
[À compléter — commentez les écarts, expliquez pourquoi certains index
ou vues matérialisées ont eu un impact plus important que d'autres]
```

---

## 6. Dashboards — Metabase

### 6.1 Configuration

- **Connexion Oracle :** `stendhal.iem:1521/enss2025`
- **Plugin Oracle Metabase :** `[Version utilisée]`

### 6.2 KPIs retenus

| KPI | Indicateur | Visualisation | Source (vue/requête) |
|-----|-----------|---------------|----------------------|
| `[Ex : Note moyenne par ville]` | `AVG(stars)` | Carte choroplèthe | `MV_STARS_BY_CITY` |
| `[À compléter]` | | | |
| `[À compléter]` | | | |
| `[À compléter]` | | | |

### 6.3 Description des dashboards

**Dashboard 1 — `[Titre]`**
```
[Décrivez les visualisations, ce qu'elles montrent, pourquoi ce choix]
```

**Dashboard 2 — `[Titre]`**
```
[À compléter]
```

> *Captures d'écran à insérer dans le rapport PDF final.*

---

## 7. Retour d'expérience

### 7.1 Ce qui a bien fonctionné

```
[À compléter — ex : "Le schéma en constellation s'est avéré pertinent
pour les requêtes cross-datamart. Les vues matérialisées ont divisé
les temps de réponse par X sur les agrégations par ville."]
```

### 7.2 Difficultés rencontrées

```
[À compléter — ex : "Le parsing des attributs JSON imbriqués dans
les données business a nécessité une transformation complexe.
La gestion des types Oracle avec Spark a demandé l'implémentation
d'un OracleDialect custom."]
```

### 7.3 Propositions d'amélioration

```
[À compléter — ex : "Ajouter une dimension temporelle plus fine
(semaine, heure) pour analyser les tendances intra-journalières.
Intégrer les données checkin pour enrichir l'analyse de fréquentation."]
```

---

## 8. Documentation technique

### 8.1 Prérequis

```
- Java JDK : [version]
- Scala    : [version]
- sbt      : [version]
- Spark    : [version]
- Oracle ojdbc : [version]
- PostgreSQL driver : [version]
```

### 8.2 Installation & configuration

```bash
# Cloner le dépôt
git clone [URL du dépôt]

# Configurer les connexions (fichier application.conf ou Main.scala)
# PostgreSQL : stendhal.iem:5432/tpid2020 (login: tpid / tpid)
# Oracle     : stendhal.iem:1521/enss2025 (login: votre_login)

# Fixer les variables d'environnement Oracle sur stendhal
. /opt/oraenv.sh
```

### 8.3 Exécution de l'ETL

```bash
# Lancer avec 16Go de RAM alloués à la JVM
sbt -J-Xmx16g run

# Exécuter uniquement une partie du schéma
# [À compléter selon votre implémentation]
```

### 8.4 Création du schéma Oracle

```bash
# Via le script Python fourni
python main.py

# Ou directement via sqlplus sur stendhal
sqlplus votre_login/votre_login@enss2025
@datawarehouse_yelp.sql
```

### 8.5 Rafraîchissement des vues matérialisées

```sql
-- Après chaque chargement ETL
EXEC DBMS_MVIEW.REFRESH('MV_STARS_BY_CITY');
EXEC DBMS_MVIEW.REFRESH('MV_STARS_BY_CATEGORIE');
-- [etc.]
```

### 8.6 Structure du dépôt

```
projet-bi-yelp/
├── etl/
│   ├── src/main/scala/
│   │   └── Main.scala          # Point d'entrée ETL
│   └── build.sbt
├── sql/
│   ├── datawarehouse_yelp.sql  # Création du schéma complet
│   ├── Data_mart_structure_business.sql
│   └── Data_mart_structure_reviews.sql
├── python/
│   ├── Data_base_connexion.py
│   └── main.py
├── dashboards/                 # Exports Metabase (PDF/images)
└── rapport/                    # Sources LaTeX ou Word du rapport
```

---

## Annexes

### A. Schéma détaillé du Data Warehouse

> *Insérer ici le diagramme entité-relation complet (image)*

### B. Captures d'écran des dashboards Metabase

> *À compléter une fois les dashboards créés*

### C. Résultats complets des requêtes

> *Tableaux de données exportés depuis Oracle*

---
# specification nettoyage des données : 
- Business : 
    - Supprimer les doublons (business_id) (done)
    - Traiter les valeurs manquantes (ex : parking inconnu → "unknown") mettre a 0 (a faire )
    - Normaliser les catégories (ex : mettre tout en maj et singulier et genre neutre) (a faire) 
    - Extraire les horaires d'ouverture en colonnes séparées (done)
    - Convertir les coordonnées GPS en zones géographiques (ville, région)
    - tout mettre en englais dans la base de données (a faire)
    