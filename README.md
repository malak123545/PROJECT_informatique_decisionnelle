# Pipeline ETL Yelp — Guide d'exécution

## Prérequis

- Java 17+
- SBT installé
- Accès réseau à `stendhal.iem:5432` (PostgreSQL)
- Les fichiers JSON Yelp dans `ETL_Json/src/Data/` :
  - `yelp_academic_dataset_business.json`
  - `yelp_academic_dataset_review.json`
  - `yelp_academic_dataset_user.json`
  - `yelp_academic_dataset_checkin.json`
  - `yelp_academic_dataset_tip.json`

---

## Vue d'ensemble

```
JSON bruts (Yelp)
      │
      ▼  [Etape 1 - ETL_Json/]
CSV filtres  ──────────────────────────────────────┐
      │                                             │
      ▼  [Etape 1 suite - ReconcileWithPostgres]    │
CSV enrichis (reviews + users reconcilies avec PG) │
      │                                             │
      ▼  [Etape 2 - postgresql/]                    │
DataMart02 CSV (schema en etoile)                  │
      │                                             │
      ▼  [Etape 3 - postgresql/]                    │
PostgreSQL datamart02.* (dim + fact + vues)        │
                                                    │
PostgreSQL yelp.* ──────────────────────────────────┘
  (source pour la reconciliation)
```

---

## Etape 1 — ETL JSON → CSV + Réconciliation PostgreSQL

Se place dans le dossier `ETL_Json/` :

```bash
cd ETL_Json
```

Compiler :

```bash
sbt clean compile
```

Lancer le pipeline (Phase 1 + Phase 2 réconciliation automatique) :

```bash
sbt -J-Xmx16g run
```

Les CSV produits se trouvent dans `ETL_Json/src/output/` :

| Fichier | Contenu |
|---------|---------|
| `business.csv` | Business filtrés par type (Restaurant, Bar, Bakery, Coffee, Hotel) |
| `reviews.csv` | Reviews filtrées sur business valides + reviews PG non présentes |
| `users.csv` | Users filtrés + users PG, stats recalculées sur reviews réelles |
| `user_elite.csv` | Statut elite par user/année |
| `user_friends.csv` | Paires d'amis (uniquement entre users valides) |
| `checkins.csv` | Check-ins filtrés sur business valides |
| `tips.csv` | Tips filtrés sur business et users valides |
| `hours.csv` | Horaires des business |
| `attributes.csv` | Attributs des business |
| `parking.csv` | Infos parking des business |
| `business_types.csv` | Référentiel des types de commerce |
| `categories.csv` | Catégories normalisées (1 ligne par business/catégorie) |

> **Note :** La réconciliation PostgreSQL s'exécute automatiquement à la fin.
> Elle nécessite la connexion à `stendhal.iem:5432`.
> Si PostgreSQL est inaccessible, le pipeline s'arrêtera à cette étape.

---

## Etape 2 — Construction du DataMart02 (schéma en étoile)

Se place dans `postgresql/` :

```bash
cd ../postgresql
```

Compiler :

```bash
sbt clean compile
```

Lancer le DataMart02 ETL (lit PostgreSQL + tip.csv → produit des CSV en étoile) :

```bash
sbt -J-Xmx16g "runMain DataMart02ETL"
```

CSV produits dans `/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_csv/` :

| Fichier | Contenu |
|---------|---------|
| `dim_user.csv` | Dimension utilisateur (user_id, name, fans, FK vers dim_review/elite/tip) |
| `dim_review.csv` | Agrégats de reviews par user (nbr, avg_stars, useful, funny, cool) |
| `dim_elite.csv` | Agrégat du statut élite par user (nbr années) |
| `dim_tip.csv` | Agrégat des tips par user (compliments) |
| `fact_user_pertinence.csv` | Table de faits avec score de pertinence |

**Formule du score de pertinence :**
```
score = (total_useful × 3) + (total_cool × 2) + (total_funny × 1) + (nbr_elite_years × 10)
```

---

## Etape 3 — Import du DataMart02 dans PostgreSQL

Créer les tables et vues dans PostgreSQL (à faire une seule fois) :

```bash
# Option A : schéma dédié datamart02
psql -h stendhal.iem -p 5432 -U tpid -d tpid2020 -f create_datamart_schema.sql

# Option B : dans le schéma yelp avec préfixe dm02_
psql -h stendhal.iem -p 5432 -U tpid -d tpid2020 -f create_datamart_tables.sql
```

Si besoin des droits (à faire avec un compte admin) :

```bash
psql -h stendhal.iem -p 5432 -U postgres -d tpid2020 -f GRANT_RIGHTS.sql
```

Lancer l'import des CSV vers PostgreSQL :

```bash
sbt -J-Xmx16g "runMain ImportToPostgres"
```

Vérifier le résultat :

```bash
# Vérification du datamart02 (schéma dédié)
psql -h stendhal.iem -p 5432 -U tpid -d tpid2020 -f verify_datamart_import.sql

# Vérification de la table de faits dans yelp.*
psql -h stendhal.iem -p 5432 -U tpid -d tpid2020 -f verify_datamart.sql
```

Tables disponibles après import dans PostgreSQL :

```
datamart02
  ├── dim_user
  ├── dim_review
  ├── dim_elite
  ├── fact_user_pertinence
  ├── v_top_users              (vue : classement par score)
  ├── v_user_activity_stats    (vue : répartition par volume de reviews)
  └── v_elite_stats            (vue : stats par années élite)
```

---

## Exploration rapide des données (optionnel)

Pour explorer le DataMart02 sans passer par PostgreSQL :

```bash
cd postgresql
sbt -J-Xmx16g "runMain ExploreDataMart02"
```

Affiche : statistiques générales, schémas, top 10 users, distribution du score, répartition élite.

---

## Résumé des commandes dans l'ordre

```bash
# 1. ETL complet (JSON -> CSV -> Reconciliation PG)
cd ETL_Json
sbt clean compile
sbt -J-Xmx16g run

# 2. DataMart en etoile
cd ../postgresql
sbt clean compile
sbt -J-Xmx16g "runMain DataMart02ETL"

# 3. Import dans PostgreSQL (une seule fois pour les tables)
psql -h stendhal.iem -p 5432 -U tpid -d tpid2020 -f create_datamart_schema.sql
sbt -J-Xmx16g "runMain ImportToPostgres"

# 4. Verification
psql -h stendhal.iem -p 5432 -U tpid -d tpid2020 -f verify_datamart_import.sql
```
