# Scénarios d'analyse — Dashboard Yelp

## Pourquoi des vues ?

Les requêtes des scénarios impliquent des jointures multiples, des calculs de scores et des fonctions analytiques sur des tables volumineuses (millions de reviews, centaines de milliers d'utilisateurs et de businesses). Rejouer ces calculs à chaque chargement du dashboard serait coûteux en temps et en ressources.

**Les vues Oracle résolvent ce problème en :**

- **Pré-calculant** les résultats complexes une seule fois
- **Simplifiant** les requêtes Metabase : un simple `SELECT * FROM V_...` suffit, avec un filtre au besoin
- **Centralisant** la logique métier : une seule définition à maintenir, réutilisable dans plusieurs dashboards ou exports
- **Assurant la fraîcheur des données** grâce à une mise à jour journalière automatique la nuit, garantissant que les vues reflètent les dernières données sans intervention manuelle

---

## Scénario 1 — Récompenser les utilisateurs contributeurs

### Objectif

Identifier les **100 meilleurs utilisateurs par type de business**, pour permettre à Yelp de cibler des programmes de récompense (badges, avantages, mise en avant de profil) auprès des contributeurs les plus actifs et influents.

### Vue : `V_TOP_USERS_BY_BUSINESS_TYPE`

#### Calcul du score de pertinence

Le score est conçu pour combiner **l'activité brute** d'un utilisateur et **son influence sociale** :

```
SCORE_BASE = moyenne de (review_count + useful + funny + cool + 9 compliments)

SCORE_FINAL = SCORE_BASE × (1 + (2×√fans + √friend_count) / 10)
```

- Le **score de base** agrège toutes les contributions mesurables de l'utilisateur (avis écrits, votes reçus, compliments reçus), normalisé sur 13 métriques pour éviter qu'une seule domine.
- Le **multiplicateur social** amplifie le score selon le nombre de fans et d'amis : un utilisateur influent dans la communauté est valorisé davantage qu'un contributeur isolé. La racine carrée évite de sur-pondérer les très grands réseaux.

#### Construction de la vue (étapes internes)

| CTE | Rôle |
|---|---|
| `user_scores` | Calcule le score de pertinence pour chaque utilisateur depuis `FAIT_USER` |
| `best_review` | Identifie la review la plus utile par utilisateur (`ROW_NUMBER` sur `total_useful DESC`) |
| `user_business_type` | Relie chaque utilisateur aux types de business via ses reviews (`DIM_REVIEW` → `FAIT_BUSINESS` → `DIM_TYPE_BUSINESS`) |
| `ranked` | Classe les utilisateurs au sein de chaque type de business (`ROW_NUMBER OVER PARTITION BY type_name`) |

Le filtre `rang <= 100` limite le résultat aux 100 meilleurs par type.

#### Colonnes exposées

| Colonne | Description |
|---|---|
| `type_business` | Type de commerce (restaurant, bar, spa...) |
| `rang` | Classement au sein du type (1 = meilleur) |
| `nom_user` | Nom de l'utilisateur |
| `score_pertinence` | Score calculé (arrondi) |
| `review_count`, `useful`, `funny`, `cool` | Métriques d'activité |
| `nb_amis`, `fans` | Réseau social |
| `nb_annees_elite`, `derniere_annee_elite` | Statut élite |
| `average_stars` | Note moyenne donnée par l'utilisateur |
| `best_review_*` | Détails de sa review la plus utile |

#### Requête Metabase

```sql
SELECT *
FROM V_TOP_USERS_BY_BUSINESS_TYPE
ORDER BY type_business, rang;
```

Pour filtrer sur un type spécifique :
```sql
SELECT *
FROM V_TOP_USERS_BY_BUSINESS_TYPE
WHERE type_business = 'Restaurants'
ORDER BY rang;
```

---

## Scénario 2 — Cartographie des opportunités commerciales

### Objectif

Pour chaque état américain, identifier **les types de business les plus performants et les moins performants**, afin d'orienter les décisions d'expansion, de partenariats commerciaux ou de campagnes marketing ciblées.

### Vue : `V_PERFORMANCE_BY_STATE`

#### Métriques de performance

Pour chaque combinaison `(state, type_business)` :

- `avg_stars` : note moyenne des commerces du type dans cet état
- `total_reviews` : volume total d'avis (indicateur de popularité)
- `avg_reviews_par_business` : densité de reviews par commerce (indicateur d'engagement)

Le classement (`rang_meilleur`, `rang_pire`) est calculé **par état** via `RANK() OVER (PARTITION BY state)`, avec un label `statut` synthétique (MEILLEUR / PIRE / -).

#### Filtres qualité appliqués

Deux critères d'exclusion évitent les biais statistiques sur des échantillons trop petits :

- **Combinaisons (state, type) avec moins de 5 business** : une moyenne sur 1 ou 2 établissements n'est pas représentative.
- **États avec moins de 100 business au total** : les états sous-représentés dans le dataset produiraient des classements non significatifs.

#### Construction de la vue (étapes internes)

| CTE | Rôle |
|---|---|
| `perf_par_type` | Agrège les métriques par `(state, type_name)` avec le filtre `>= 5 business` |
| `states_valides` | Filtre les états ayant au moins 100 business au total |
| `ranked` | Calcule les rangs meilleur et pire par état via deux `RANK() OVER` |

#### Colonnes exposées

| Colonne | Description |
|---|---|
| `state` | Code de l'état (ex: CA, NY, TX) |
| `type_business` | Type de commerce |
| `nb_business` | Nombre d'établissements de ce type dans l'état |
| `avg_stars` | Note moyenne |
| `total_reviews` | Nombre total d'avis |
| `avg_reviews_par_business` | Moyenne de reviews par établissement |
| `rang_meilleur` | Rang dans l'état (1 = meilleur avg_stars) |
| `rang_pire` | Rang dans l'état (1 = pire avg_stars) |
| `statut` | MEILLEUR / PIRE / - |

#### Requête Metabase

```sql
SELECT *
FROM V_PERFORMANCE_BY_STATE
ORDER BY state, avg_stars DESC;
```

Pour un état spécifique :
```sql
SELECT *
FROM V_PERFORMANCE_BY_STATE
WHERE state = 'CA'
ORDER BY avg_stars DESC;
```

Pour voir uniquement les meilleurs et pires de chaque état :
```sql
SELECT *
FROM V_PERFORMANCE_BY_STATE
WHERE statut != '-'
ORDER BY state, statut;
```

---

## Mise à jour des vues

Les vues sont recréées automatiquement chaque nuit (`CREATE OR REPLACE VIEW`) afin d'intégrer les nouvelles reviews, les nouveaux utilisateurs et les éventuels changements de données sans interruption du service dashboard.
