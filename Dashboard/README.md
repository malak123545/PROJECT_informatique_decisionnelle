# Dashboard Yelp — Informatique Décisionnelle

Ce projet contient un dashboard interactif pour analyser les données Yelp, basé sur une architecture avec API FastAPI et interface React.

## Architecture

- **API FastAPI** (`api/`) : Connecte à la base Oracle et expose les endpoints pour les scénarios d'analyse.
- **Dashboard React** (`yelp_dashboard/`) : Interface utilisateur qui consomme l'API pour afficher les analyses.

## Prérequis

- Docker et Docker Compose installés.
- Base de données Oracle accessible avec les données Yelp chargées (schémas et vues créés via `BD_oracle/main.py` et les scripts SQL).

## Démarrage

1. **Naviguez vers le dossier Dashboard** :
   ```bash
   cd Dashboard
   ```

2. **Lancez les services** :
   ```bash
   docker-compose up --build -d
   ```
   - Cela construit et démarre l'API FastAPI (port 8000) et le dashboard React (port 5173).

3. **Vérifiez les logs** (optionnel) :
   ```bash
   docker-compose logs -f api          # Logs de l'API
   docker-compose logs -f yelp-dashboard  # Logs du dashboard
   ```

## Accès au Dashboard

- **Interface principale** : Ouvrez `http://localhost:5173` dans votre navigateur.
  - Affiche les analyses des utilisateurs contributeurs Yelp, avec filtres par type de business et KPIs.

- **API directe** (pour débogage) : `http://localhost:8000`
  - Endpoints :
    - `/api/scenario1/top-users` : Liste des top utilisateurs.
    - `/api/scenario1/kpis` : Indicateurs clés.
    - `/health` : Vérification de l'état.

## Configuration

- **API** : Modifiez `api/main.py` pour les identifiants Oracle (variables d'environnement dans `docker-compose.yml`).
- **Dashboard** : L'URL de l'API est configurée dans `yelp_dashboard/src/App.jsx` (`API_URL = "http://api:8000"`).

## Arrêt

```bash
docker-compose down
```

## Dépannage

- **Build échoue** : Vérifiez les logs de build avec `docker-compose build --no-cache`.
- **Données vides** : Assurez-vous que les vues SQL sont créées dans Oracle (exécutez `BD_oracle/main.py`).
- **Connexion Oracle** : Vérifiez les variables dans `docker-compose.yml` et `api/main.py`.

Pour plus de détails, consultez les fichiers dans `BD_oracle/` pour la configuration de la base de données.