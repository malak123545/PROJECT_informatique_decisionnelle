# Data Mart 02 - ETL Yelp Pertinence Utilisateurs

## Description

Pipeline ETL complet en Scala + Apache Spark pour construire un Data Mart de mesure de la pertinence des reviews utilisateurs Yelp.

## Architecture du Data Mart 02

### Tables Sources (PostgreSQL - Schéma yelp)
- `yelp.user` : Informations utilisateurs
- `yelp.review` : Reviews des utilisateurs
- `yelp.elite` : Années où l'utilisateur était élite
- `yelp.tip` : Tips/conseils laissés par les utilisateurs

### Table de Faits (Destination)
- `yelp.fact_user_pertinence` : Table de faits avec le score de pertinence

## Pipeline ETL

### ÉTAPE 1 : Extract
Lecture depuis PostgreSQL via JDBC avec partitionnement :
- **yelp.user** : Partitionné sur `user_id` (10 partitions)
- **yelp.review** : Partitionné sur `user_id` (10 partitions)
- **yelp.elite** : Sans partitionnement
- **yelp.tip** : Partitionné sur `user_id` (10 partitions)

### ÉTAPE 2 : Transform - Cleaning
Pour chaque table, nettoyage systématique :

1. **Doublons**
   - `user` : dédoublonnage sur `user_id`
   - `review` : dédoublonnage sur `review_id`
   - `elite` : dédoublonnage sur `(user_id, year)`
   - `tip` : dédoublonnage sur `(user_id, date, business_id)`

2. **Valeurs nulles**
   - Colonnes numériques → 0
   - Colonnes string → "unknown"
   - Colonnes date → lignes supprimées si null

3. **Valeurs aberrantes**
   - `stars` : entre 1.0 et 5.0
   - `useful`, `funny`, `cool` : >= 0
   - `review_count` : >= 0
   - `yelping_since` : >= 2004-01-01

4. **Format**
   - Trim sur tous les strings
   - `user_id` en minuscules

### ÉTAPE 3 : Transform - Aggregations

#### DIM_REVIEW
Agrégation par `user_id` depuis `yelp.review` :
- `nbr_reviews` : nombre de reviews
- `avg_stars` : moyenne des étoiles
- `total_useful` : somme des votes utiles
- `total_funny` : somme des votes drôles
- `total_cool` : somme des votes cool

#### DIM_ELITE
Agrégation par `user_id` depuis `yelp.elite` :
- `nbr_elite_years` : nombre d'années élite
- `last_elite_year` : dernière année élite

#### DIM_TIP
Agrégation par `user_id` depuis `yelp.tip` :
- `nbr_tips` : nombre de tips
- `total_compliments` : somme des compliments

#### FACT_USER_PERTINENCE
Jointure de toutes les dimensions :

**Colonnes finales :**
- `user_id` (PK)
- `name`
- `fans`
- `yelping_since`
- `nbr_reviews`
- `avg_stars`
- `total_useful`
- `total_funny`
- `total_cool`
- `nbr_elite_years`
- `last_elite_year`
- `nbr_tips`
- `total_compliments`
- `pertinence_score` : **(total_useful × 3) + (total_cool × 2) + (total_funny × 1) + (nbr_elite_years × 10)**

### ÉTAPE 4 : Load
Écriture dans PostgreSQL :
- Table : `yelp.fact_user_pertinence`
- Mode : `overwrite`
- Affichage du top 5 par pertinence avant écriture

## Prérequis

### Logiciels
- Apache Spark 3.5.0+
- Scala 2.12.x
- PostgreSQL 12+
- Driver JDBC PostgreSQL : `postgresql-42.7.1.jar` (déjà inclus)

### Variables d'environnement
```bash
export SPARK_HOME=/path/to/spark
export PATH=$SPARK_HOME/bin:$PATH
```

### Base de données
```bash
# Créer la table de destination
psql -U yelp_user -d yelp -f create_fact_table.sql
```

## Utilisation

### Méthode 1 : Compilation + Spark-submit (RECOMMANDÉE)

```bash
# 1. Compiler le code Scala
./compile-spark.sh

# 2. Exécuter le job Spark
./run-spark.sh
```

### Méthode 2 : Exécution directe avec spark-shell

```bash
./run-spark-scala.sh
```

### Méthode 3 : Exécution manuelle

```bash
# Compilation
scalac -classpath "$SPARK_HOME/jars/*:postgresql-42.7.1.jar" \
       -d bin/spark \
       src/spark/DataMart02ETL.scala

# Exécution
$SPARK_HOME/bin/spark-submit \
    --class DataMart02ETL \
    --master local[*] \
    --driver-memory 2g \
    --jars postgresql-42.7.1.jar \
    bin/spark
```

## Configuration JDBC

Paramètres de connexion (modifiables dans `DataMart02ETL.scala`) :

```scala
val jdbcUrl = "jdbc:postgresql://localhost:5432/yelp"
val jdbcUser = "yelp_user"
val jdbcPassword = "yelp_pass"
```

## Structure du Projet

```
postgresql/
├── src/
│   └── spark/
│       └── DataMart02ETL.scala       # Script ETL principal
├── bin/
│   └── spark/                         # Classes compilées
├── postgresql-42.7.1.jar              # Driver JDBC
├── compile-spark.sh                   # Script de compilation
├── run-spark.sh                       # Script d'exécution (spark-submit)
├── run-spark-scala.sh                 # Script d'exécution (spark-shell)
├── create_fact_table.sql              # Script SQL de création
└── DATAMART02_README.md               # Cette documentation
```

## Logs et Monitoring

Le script affiche des logs détaillés à chaque étape :

```
═══════════════════════════════════════
   DATA MART 02 ETL - YELP PERTINENCE
═══════════════════════════════════════

─────────────────────────────────────────
ÉTAPE 1 : EXTRACTION DES DONNÉES
─────────────────────────────────────────
[EXTRACT] ✓ yelp.user chargé : 10000 lignes
[EXTRACT] ✓ yelp.review chargé : 50000 lignes
...

─────────────────────────────────────────
ÉTAPE 2 : NETTOYAGE DES DONNÉES
─────────────────────────────────────────
[CLEANING] Table user : 5 doublons supprimés, 2 nulls filtrés, 1 lignes aberrantes filtrées
...

─────────────────────────────────────────
ÉTAPE 3 : TRANSFORMATION ET AGRÉGATIONS
─────────────────────────────────────────
[TRANSFORM] ✓ DIM_REVIEW créé : 9000 utilisateurs
...

─────────────────────────────────────────
ÉTAPE 4 : CHARGEMENT DANS POSTGRESQL
─────────────────────────────────────────
[LOAD] ✓ 10000 lignes écrites avec succès
```

## Optimisations Spark

Le script est optimisé avec :
- **Partitionnement** : 10 partitions pour les grandes tables
- **Adaptive Query Execution** : Activé pour optimiser les jointures
- **Shuffle Partitions** : 10 partitions pour réduire la mémoire
- **Left Joins** : Pour inclure tous les utilisateurs même sans reviews/elite/tips

## Formule du Score de Pertinence

```
pertinence_score = (total_useful × 3) + (total_cool × 2) + (total_funny × 1) + (nbr_elite_years × 10)
```

**Pondération :**
- Votes "useful" : × 3 (plus important)
- Votes "cool" : × 2 (moyennement important)
- Votes "funny" : × 1 (moins important)
- Années élite : × 10 (très important)

## Résolution de problèmes

### Erreur : SPARK_HOME non défini
```bash
export SPARK_HOME=/usr/local/spark
```

### Erreur : Driver JDBC non trouvé
Vérifiez que `postgresql-42.7.1.jar` est dans le répertoire racine.

### Erreur : Connexion PostgreSQL refusée
Vérifiez que PostgreSQL est démarré et accessible :
```bash
psql -U yelp_user -d yelp -h localhost
```

### Erreur : Out of Memory
Augmentez la mémoire dans les scripts :
```bash
--driver-memory 4g \
--executor-memory 4g
```

## Auteur

Data Engineering - Yelp Pertinence Analysis
