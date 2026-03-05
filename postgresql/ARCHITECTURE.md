# Architecture Technique - DataMart02ETL

## Structure du Code Scala

### Vue d'ensemble

```scala
object DataMart02ETL {
  // Configuration JDBC
  val jdbcUrl = "jdbc:postgresql://localhost:5432/yelp"
  val jdbcUser = "yelp_user"
  val jdbcPassword = "yelp_pass"
  
  // Pipeline ETL
  def main(args: Array[String]): Unit
  def extract(spark: SparkSession): Map[String, DataFrame]
  def clean(spark: SparkSession, rawTables: Map[String, DataFrame]): Map[String, DataFrame]
  def transform(spark: SparkSession, cleanedTables: Map[String, DataFrame]): DataFrame
  def load(factTable: DataFrame): Unit
}
```

## Fonctions Principales

### 1. main() - Orchestration du Pipeline

```
┌──────────────────────────────────────┐
│  Initialisation SparkSession         │
└────────────────┬─────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────┐
│  ÉTAPE 1 : extract()                 │
│  Lecture PostgreSQL + Partitionnement│
└────────────────┬─────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────┐
│  ÉTAPE 2 : clean()                   │
│  Nettoyage de toutes les tables      │
└────────────────┬─────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────┐
│  ÉTAPE 3 : transform()               │
│  Agrégations + Calcul du score       │
└────────────────┬─────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────┐
│  ÉTAPE 4 : load()                    │
│  Écriture dans PostgreSQL            │
└────────────────┬─────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────┐
│  spark.stop()                        │
└──────────────────────────────────────┘
```

### 2. extract() - Extraction des Données

**Retour** : `Map[String, DataFrame]`

```scala
Map(
  "user"   -> DataFrame avec partitionnement sur user_id (10 partitions)
  "review" -> DataFrame avec partitionnement sur user_id (10 partitions)
  "elite"  -> DataFrame sans partitionnement
  "tip"    -> DataFrame avec partitionnement sur user_id (10 partitions)
)
```

**Stratégie de partitionnement** :
- Tables volumineuses (user, review, tip) : 10 partitions sur user_id
- Table de référence (elite) : Sans partitionnement

### 3. clean() - Nettoyage des Données

**Retour** : `Map[String, DataFrame]`

Appelle 4 fonctions spécialisées :

```
clean()
├── cleanUser()    → yelp.user nettoyé
├── cleanReview()  → yelp.review nettoyé
├── cleanElite()   → yelp.elite nettoyé
└── cleanTip()     → yelp.tip nettoyé
```

#### 3.1 cleanUser()

```scala
Pipeline de nettoyage :
1. dropDuplicates("user_id")
2. na.fill(0) pour review_count, fans, average_stars
3. na.fill("unknown") pour name
4. filter(yelping_since isNotNull)
5. filter(review_count >= 0, fans >= 0)
6. filter(yelping_since >= "2004-01-01")
7. filter(average_stars entre 1.0 et 5.0)
8. lower(trim(user_id)), trim(name)
```

#### 3.2 cleanReview()

```scala
Pipeline de nettoyage :
1. dropDuplicates("review_id")
2. na.fill(0) pour stars, useful, funny, cool
3. na.fill("unknown") pour text
4. filter(date isNotNull)
5. when(stars entre 1.0 et 5.0).otherwise(0)
6. filter(useful >= 0, funny >= 0, cool >= 0)
7. lower(trim()) pour review_id, user_id, business_id
```

#### 3.3 cleanElite()

```scala
Pipeline de nettoyage :
1. dropDuplicates("user_id", "year")
2. filter(year isNotNull AND user_id isNotNull)
3. filter(year >= 2004)
4. lower(trim(user_id))
```

#### 3.4 cleanTip()

```scala
Pipeline de nettoyage :
1. dropDuplicates("user_id", "date", "business_id")
2. na.fill(0) pour compliment_count
3. na.fill("unknown") pour text
4. filter(date isNotNull)
5. filter(compliment_count >= 0)
6. lower(trim()) pour user_id, business_id
```

### 4. transform() - Transformations et Agrégations

**Retour** : `DataFrame` (FACT_USER_PERTINENCE)

```
┌─────────────────────┐
│  userDF (cleaned)   │
└──────────┬──────────┘
           │
           ├─── reviewDF ──► groupBy(user_id) ──► DIM_REVIEW
           │                  ├─ count(*) → nbr_reviews
           │                  ├─ avg(stars) → avg_stars
           │                  ├─ sum(useful) → total_useful
           │                  ├─ sum(funny) → total_funny
           │                  └─ sum(cool) → total_cool
           │
           ├─── eliteDF ──► groupBy(user_id) ──► DIM_ELITE
           │                 ├─ count(*) → nbr_elite_years
           │                 └─ max(year) → last_elite_year
           │
           └─── tipDF ──► groupBy(user_id) ──► DIM_TIP
                          ├─ count(*) → nbr_tips
                          └─ sum(compliment_count) → total_compliments
                          
                          ▼
┌────────────────────────────────────────────────────────┐
│  LEFT JOIN sur user_id                                 │
│  userDF ⟕ DIM_REVIEW ⟕ DIM_ELITE ⟕ DIM_TIP           │
└────────────────────┬───────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────┐
│  na.fill(0) pour les métriques NULL                    │
└────────────────────┬───────────────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────────────┐
│  withColumn("pertinence_score",                        │
│    (total_useful × 3) + (total_cool × 2) +            │
│    (total_funny × 1) + (nbr_elite_years × 10)         │
│  )                                                      │
└────────────────────┬───────────────────────────────────┘
                     │
                     ▼
              FACT_USER_PERTINENCE
```

#### Colonnes de FACT_USER_PERTINENCE

```scala
Colonnes finales (14 colonnes) :
├─ user_id: String (PK)
├─ name: String
├─ fans: Long
├─ yelping_since: Date
├─ nbr_reviews: Long
├─ avg_stars: Double
├─ total_useful: Long
├─ total_funny: Long
├─ total_cool: Long
├─ nbr_elite_years: Long
├─ last_elite_year: Integer
├─ nbr_tips: Long
├─ total_compliments: Long
└─ pertinence_score: Long (calculé)
```

### 5. load() - Chargement dans PostgreSQL

```scala
Pipeline de chargement :
1. count() → totalRows
2. orderBy(pertinence_score DESC).show(5)
3. write.mode("overwrite").jdbc(...)
4. Log du nombre de lignes écrites
```

**Configuration JDBC** :
```scala
connectionProperties:
├─ user: "yelp_user"
├─ password: "yelp_pass"
└─ driver: "org.postgresql.Driver"

jdbc URL: "jdbc:postgresql://localhost:5432/yelp"
Table: "yelp.fact_user_pertinence"
Mode: "overwrite"
```

## Optimisations Spark

### Configuration de la SparkSession

```scala
SparkSession.builder()
  .appName("DataMart02-YelpPertinence")
  .master("local[*]")                          // Tous les cœurs disponibles
  .config("spark.sql.shuffle.partitions", "10") // Réduire le nombre de shuffles
  .config("spark.sql.adaptive.enabled", "true") // Optimisation adaptative
  .getOrCreate()
```

### Stratégie de Partitionnement

```scala
// Lecture avec partitionnement automatique
spark.read.jdbc(
  url = jdbcUrl,
  table = "yelp.review",
  columnName = "user_id",     // Colonne de partitionnement
  lowerBound = 1,
  upperBound = 100,
  numPartitions = 10,         // 10 partitions
  connectionProperties
)
```

### Jointures Optimisées

```scala
// LEFT JOIN pour inclure tous les utilisateurs
userDF
  .join(dimReview, Seq("user_id"), "left")
  .join(dimElite, Seq("user_id"), "left")
  .join(dimTip, Seq("user_id"), "left")
  .na.fill(0)  // Remplacer les NULL après les left joins
```

## Gestion des Erreurs

```scala
try {
  // Pipeline ETL
  val rawTables = extract(spark)
  val cleanedTables = clean(spark, rawTables)
  val factTable = transform(spark, cleanedTables)
  load(factTable)
} catch {
  case e: Exception =>
    println(s"[ERREUR] ${e.getMessage}")
    e.printStackTrace()
} finally {
  spark.stop()  // Toujours fermer Spark
}
```

## Logs et Monitoring

Chaque étape affiche des logs détaillés :

```scala
[EXTRACT] ✓ yelp.user chargé : 10000 lignes
[CLEANING] Table user : 5 doublons supprimés, 2 nulls filtrés
[TRANSFORM] ✓ DIM_REVIEW créé : 9000 utilisateurs
[LOAD] ✓ 10000 lignes écrites avec succès
```

## Performance

### Métriques Attendues

| Volume de données | Temps d'exécution | Mémoire utilisée |
|-------------------|-------------------|------------------|
| 100K lignes | 1-2 min | < 1 GB |
| 1M lignes | 3-5 min | 1-2 GB |
| 10M lignes | 10-20 min | 2-4 GB |

### Goulots d'Étranglement

1. **Lecture JDBC** : Partitionnement sur user_id pour paralléliser
2. **Jointures** : Utilisation de left joins optimisés
3. **Shuffles** : Limité à 10 partitions pour réduire l'overhead
4. **Écriture** : Mode overwrite avec batch writes

## Extensibilité

Le code est conçu pour être facilement extensible :

```scala
// Ajouter une nouvelle dimension
val dimNewMetric = newDF
  .groupBy("user_id")
  .agg(...)

// L'intégrer dans la fact table
factUserPertinence
  .join(dimNewMetric, Seq("user_id"), "left")
  .withColumn("new_score", ...)
```

## Tests Recommandés

1. **Test unitaire des fonctions de nettoyage**
   ```scala
   assert(cleanUser(testDF).count() == expectedCount)
   ```

2. **Test d'intégration du pipeline complet**
   ```bash
   ./test-connection.sh
   ./run-spark-scala.sh
   ```

3. **Validation des résultats**
   ```sql
   -- verify_datamart.sql
   SELECT COUNT(*) FROM yelp.fact_user_pertinence;
   ```

---

**Pour plus de détails** : Consultez le code source dans `src/spark/DataMart02ETL.scala`
