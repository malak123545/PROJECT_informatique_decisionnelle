# Index des Fichiers - Data Mart 02 ETL

## 📋 Documentation

| Fichier | Description |
|---------|-------------|
| **QUICKSTART.md** | Guide de démarrage rapide (5 min) |
| **DATAMART02_README.md** | Documentation complète du projet |
| **INDEX.md** | Ce fichier - index de tous les fichiers |

## 🔧 Scripts de Build & Exécution

### Compilation

| Fichier | Description | Usage |
|---------|-------------|-------|
| **compile-spark.sh** | Compilation avec `scalac` | `./compile-spark.sh` |
| **build-sbt.sh** | Build avec SBT (crée un JAR) | `./build-sbt.sh` |

### Exécution

| Fichier | Description | Usage |
|---------|-------------|-------|
| **run-spark-scala.sh** | Exécution directe avec spark-shell | `./run-spark-scala.sh` ⭐ RECOMMANDÉ |
| **run-spark.sh** | Exécution avec spark-submit | `./run-spark.sh` |
| **run-sbt.sh** | Exécution du JAR SBT | `./run-sbt.sh` |

### Test & Vérification

| Fichier | Description | Usage |
|---------|-------------|-------|
| **test-connection.sh** | Test connexion PostgreSQL + tables | `./test-connection.sh` |

## 💾 Scripts SQL

| Fichier | Description | Usage |
|---------|-------------|-------|
| **create_fact_table.sql** | Création de la table de faits | `psql -U yelp_user -d yelp -f create_fact_table.sql` |
| **verify_datamart.sql** | Vérification et analyse des résultats | `psql -U yelp_user -d yelp -f verify_datamart.sql` |

## 📦 Code Source

| Fichier | Description | Localisation |
|---------|-------------|--------------|
| **DataMart02ETL.scala** | Script ETL principal (Extract, Transform, Load) | `src/spark/DataMart02ETL.scala` |

## ⚙️ Configuration

### Configuration Projet

| Fichier | Description |
|---------|-------------|
| **build.sbt** | Configuration SBT (dépendances, version Scala) |
| **project/build.properties** | Version de SBT |
| **project/plugins.sbt** | Plugins SBT (sbt-assembly) |
| **config.properties.example** | Exemple de configuration JDBC |
| **.gitignore** | Fichiers à ignorer par Git |

### Configuration PostgreSQL

Modifiable dans `DataMart02ETL.scala` :
```scala
val jdbcUrl = "jdbc:postgresql://localhost:5432/yelp"
val jdbcUser = "yelp_user"
val jdbcPassword = "yelp_pass"
```

## 📊 Architecture du Pipeline ETL

### Étape 1 : Extract (Extraction)
- Lecture depuis PostgreSQL via JDBC
- Partitionnement automatique (10 partitions)
- Tables sources : `yelp.user`, `yelp.review`, `yelp.elite`, `yelp.tip`

### Étape 2 : Transform - Cleaning (Nettoyage)
- Suppression des doublons
- Gestion des valeurs nulles
- Filtrage des valeurs aberrantes
- Normalisation des formats (trim, lowercase)

### Étape 3 : Transform - Aggregations
- **DIM_REVIEW** : Agrégation des reviews par utilisateur
- **DIM_ELITE** : Agrégation des années élite par utilisateur
- **DIM_TIP** : Agrégation des tips par utilisateur
- **FACT_USER_PERTINENCE** : Jointure de toutes les dimensions + calcul du score

### Étape 4 : Load (Chargement)
- Écriture dans PostgreSQL : `yelp.fact_user_pertinence`
- Mode : overwrite
- Affichage du top 5 avant écriture

## 🎯 Score de Pertinence

```
pertinence_score = (total_useful × 3) + (total_cool × 2) +
                   (total_funny × 1) + (nbr_elite_years × 10)
```

## 📁 Structure des Répertoires

```
postgresql/
├── 📚 Documentation
│   ├── QUICKSTART.md
│   ├── DATAMART02_README.md
│   └── INDEX.md
│
├── 🔧 Scripts Shell
│   ├── compile-spark.sh
│   ├── run-spark.sh
│   ├── run-spark-scala.sh
│   ├── build-sbt.sh
│   ├── run-sbt.sh
│   └── test-connection.sh
│
├── 💾 Scripts SQL
│   ├── create_fact_table.sql
│   └── verify_datamart.sql
│
├── 📦 Code Source
│   └── src/spark/
│       └── DataMart02ETL.scala
│
├── ⚙️ Configuration
│   ├── build.sbt
│   ├── project/
│   │   ├── build.properties
│   │   └── plugins.sbt
│   ├── config.properties.example
│   └── .gitignore
│
├── 📚 Dépendances
│   └── postgresql-42.7.1.jar
│
└── 🏗️ Build (généré)
    ├── bin/spark/           (scalac)
    └── target/scala-2.12/   (sbt)
```

## 🚀 Workflow Recommandé

### 1️⃣ Installation Initiale
```bash
# Tester la connexion
./test-connection.sh

# Créer la table de destination
psql -U yelp_user -d yelp -f create_fact_table.sql
```

### 2️⃣ Développement / Test
```bash
# Exécution rapide pour tester
./run-spark-scala.sh
```

### 3️⃣ Production
```bash
# Compilation
./compile-spark.sh

# Exécution
./run-spark.sh
```

### 4️⃣ Vérification
```bash
# Analyser les résultats
psql -U yelp_user -d yelp -f verify_datamart.sql
```

## 🔍 Fichiers Clés par Usage

### Pour démarrer rapidement
1. **QUICKSTART.md** - Lire en premier
2. **test-connection.sh** - Vérifier la connexion
3. **create_fact_table.sql** - Créer la table
4. **run-spark-scala.sh** - Lancer l'ETL

### Pour comprendre le code
1. **DATAMART02_README.md** - Documentation complète
2. **DataMart02ETL.scala** - Code source commenté

### Pour analyser les résultats
1. **verify_datamart.sql** - Requêtes d'analyse
2. Interface PostgreSQL : `psql -U yelp_user -d yelp`

### Pour la production
1. **build-sbt.sh** - Build avec SBT
2. **run-sbt.sh** - Exécution du JAR
3. **build.sbt** - Configuration du projet

## 📊 Métriques du Projet

- **Lignes de code Scala** : ~500 lignes
- **Scripts shell** : 6 fichiers
- **Scripts SQL** : 2 fichiers
- **Documentation** : 3 fichiers markdown
- **Configuration** : 4 fichiers

## 🔗 Dépendances

### Runtime
- Apache Spark 3.5.0+
- Scala 2.12.x
- PostgreSQL 12+
- Driver JDBC PostgreSQL 42.7.1

### Optionnel
- SBT 1.9.7+ (pour build avec SBT)

## 📞 Support

Pour des questions ou des problèmes :
1. Consulter **QUICKSTART.md** pour les erreurs courantes
2. Consulter **DATAMART02_README.md** section "Résolution de problèmes"
3. Exécuter **test-connection.sh** pour diagnostiquer les problèmes de connexion

---

**Version** : 1.0.0
**Dernière mise à jour** : 2026-02-21
**Auteur** : Data Engineering Team
