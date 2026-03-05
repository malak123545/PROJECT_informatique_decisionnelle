# Guide de Démarrage Rapide - Data Mart 02 ETL

## Installation Rapide (5 minutes)

### 1. Prérequis à installer

```bash
# Vérifier les prérequis
java -version         # Java 8 ou 11 requis
scala -version        # Scala 2.12.x
psql --version        # PostgreSQL

# Si SPARK_HOME n'est pas défini
export SPARK_HOME=/path/to/spark
export PATH=$SPARK_HOME/bin:$PATH
```

### 2. Créer la table de destination

```bash
psql -U yelp_user -d yelp -f create_fact_table.sql
```

### 3. Exécuter l'ETL

**Option A : Spark-shell (plus rapide, recommandé pour tester)**
```bash
./run-spark-scala.sh
```

**Option B : Compilation + Spark-submit (production)**
```bash
./compile-spark.sh
./run-spark.sh
```

**Option C : Avec SBT (gestion de projet professionnelle)**
```bash
./build-sbt.sh
./run-sbt.sh
```

### 4. Vérifier les résultats

```bash
psql -U yelp_user -d yelp -f verify_datamart.sql
```

## Résultat Attendu

```
═══════════════════════════════════════
   DATA MART 02 ETL - YELP PERTINENCE
═══════════════════════════════════════

─────────────────────────────────────────
ÉTAPE 1 : EXTRACTION DES DONNÉES
─────────────────────────────────────────
[EXTRACT] ✓ yelp.user chargé : 10000 lignes
[EXTRACT] ✓ yelp.review chargé : 50000 lignes
[EXTRACT] ✓ yelp.elite chargé : 5000 lignes
[EXTRACT] ✓ yelp.tip chargé : 8000 lignes

─────────────────────────────────────────
ÉTAPE 2 : NETTOYAGE DES DONNÉES
─────────────────────────────────────────
[CLEANING] Table user : 5 doublons supprimés...
[CLEANING] Table review : 12 doublons supprimés...
[CLEANING] Table elite : 3 doublons supprimés...
[CLEANING] Table tip : 7 doublons supprimés...

─────────────────────────────────────────
ÉTAPE 3 : TRANSFORMATION ET AGRÉGATIONS
─────────────────────────────────────────
[TRANSFORM] ✓ DIM_REVIEW créé : 9000 utilisateurs
[TRANSFORM] ✓ DIM_ELITE créé : 4500 utilisateurs élite
[TRANSFORM] ✓ DIM_TIP créé : 7000 utilisateurs avec tips
[TRANSFORM] ✓ FACT_USER_PERTINENCE créé : 10000 utilisateurs

─────────────────────────────────────────
ÉTAPE 4 : CHARGEMENT DANS POSTGRESQL
─────────────────────────────────────────
[LOAD] ✓ 10000 lignes écrites avec succès

═══════════════════════════════════════
   ETL TERMINÉ AVEC SUCCÈS !
═══════════════════════════════════════
```

## Structure du Data Mart

### Table Finale : `yelp.fact_user_pertinence`

| Colonne | Type | Description |
|---------|------|-------------|
| user_id | VARCHAR(50) | Clé primaire |
| name | VARCHAR(255) | Nom de l'utilisateur |
| fans | INTEGER | Nombre de fans |
| yelping_since | DATE | Date d'inscription |
| nbr_reviews | BIGINT | Nombre de reviews |
| avg_stars | DOUBLE | Moyenne des étoiles données |
| total_useful | BIGINT | Total votes "useful" reçus |
| total_funny | BIGINT | Total votes "funny" reçus |
| total_cool | BIGINT | Total votes "cool" reçus |
| nbr_elite_years | BIGINT | Nombre d'années élite |
| last_elite_year | INTEGER | Dernière année élite |
| nbr_tips | BIGINT | Nombre de tips |
| total_compliments | BIGINT | Total compliments sur tips |
| **pertinence_score** | BIGINT | **Score calculé** |

### Formule du Score de Pertinence

```
pertinence_score = (total_useful × 3) + (total_cool × 2) +
                   (total_funny × 1) + (nbr_elite_years × 10)
```

## Requêtes Utiles

### Top 10 utilisateurs les plus pertinents
```sql
SELECT user_id, name, pertinence_score, nbr_reviews, nbr_elite_years
FROM yelp.fact_user_pertinence
ORDER BY pertinence_score DESC
LIMIT 10;
```

### Statistiques globales
```sql
SELECT
    COUNT(*) as total_utilisateurs,
    AVG(pertinence_score) as score_moyen,
    MAX(pertinence_score) as score_max,
    COUNT(CASE WHEN nbr_elite_years > 0 THEN 1 END) as nb_elites
FROM yelp.fact_user_pertinence;
```

### Utilisateurs élites avec le plus de reviews
```sql
SELECT user_id, name, nbr_reviews, nbr_elite_years, pertinence_score
FROM yelp.fact_user_pertinence
WHERE nbr_elite_years > 0
ORDER BY nbr_reviews DESC
LIMIT 10;
```

## Troubleshooting

### Problème : "SPARK_HOME not found"
```bash
# Trouver Spark
which spark-submit

# Définir SPARK_HOME
export SPARK_HOME=/usr/local/spark
```

### Problème : "Connection refused"
```bash
# Vérifier PostgreSQL
sudo systemctl status postgresql

# Démarrer si nécessaire
sudo systemctl start postgresql

# Tester la connexion
psql -U yelp_user -d yelp -h localhost
```

### Problème : "Driver not found"
Vérifier que `postgresql-42.7.1.jar` est dans le répertoire racine du projet.

### Problème : "Out of Memory"
Augmenter la mémoire dans les scripts :
```bash
--driver-memory 4g --executor-memory 4g
```

## Temps d'Exécution Estimé

| Volume de données | Temps d'exécution |
|-------------------|-------------------|
| < 100K lignes | 1-2 minutes |
| 100K - 1M lignes | 3-5 minutes |
| 1M - 10M lignes | 10-20 minutes |

## Fichiers Importants

```
📁 postgresql/
├── 📄 DataMart02ETL.scala           ← Script ETL principal
├── 📄 create_fact_table.sql         ← Création de la table
├── 📄 verify_datamart.sql           ← Vérification des résultats
├── 🔧 compile-spark.sh              ← Compilation scalac
├── 🔧 run-spark.sh                  ← Exécution spark-submit
├── 🔧 run-spark-scala.sh            ← Exécution spark-shell
├── 🔧 build-sbt.sh                  ← Build avec SBT
├── 🔧 run-sbt.sh                    ← Run JAR SBT
└── 📚 DATAMART02_README.md          ← Documentation complète
```

## Support

Pour plus de détails, consultez `DATAMART02_README.md`.

**Bon ETL ! 🚀**
