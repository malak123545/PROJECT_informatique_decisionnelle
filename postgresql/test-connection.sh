#!/bin/bash

# Script de test de connexion PostgreSQL et vérification des tables sources

echo "═══════════════════════════════════════"
echo "  TEST DE CONNEXION POSTGRESQL"
echo "═══════════════════════════════════════"
echo ""

# Configuration
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="yelp"
DB_USER="yelp_user"

echo "[INFO] Configuration :"
echo "  Host: $DB_HOST"
echo "  Port: $DB_PORT"
echo "  Database: $DB_NAME"
echo "  User: $DB_USER"
echo ""

# Test 1 : Connexion PostgreSQL
echo "─────────────────────────────────────────"
echo "TEST 1 : Connexion à PostgreSQL"
echo "─────────────────────────────────────────"

PGPASSWORD="yelp_pass" psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "\conninfo" 2>&1

if [ $? -eq 0 ]; then
    echo "[✓] Connexion réussie"
else
    echo "[✗] Échec de la connexion"
    echo ""
    echo "Vérifiez que :"
    echo "  1. PostgreSQL est démarré : sudo systemctl status postgresql"
    echo "  2. L'utilisateur existe : psql -U postgres -c \"\\du\""
    echo "  3. La base de données existe : psql -U postgres -c \"\\l\""
    exit 1
fi

echo ""

# Test 2 : Vérification du schéma yelp
echo "─────────────────────────────────────────"
echo "TEST 2 : Schéma 'yelp' existe"
echo "─────────────────────────────────────────"

SCHEMA_EXISTS=$(PGPASSWORD="yelp_pass" psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'yelp');" 2>/dev/null | tr -d ' ')

if [ "$SCHEMA_EXISTS" = "t" ]; then
    echo "[✓] Schéma 'yelp' existe"
else
    echo "[✗] Schéma 'yelp' n'existe pas"
    echo "Créer le schéma avec : CREATE SCHEMA yelp;"
    exit 1
fi

echo ""

# Test 3 : Vérification des tables sources
echo "─────────────────────────────────────────"
echo "TEST 3 : Tables sources"
echo "─────────────────────────────────────────"

TABLES=("user" "review" "elite" "tip")
ALL_EXIST=true

for TABLE in "${TABLES[@]}"; do
    COUNT=$(PGPASSWORD="yelp_pass" psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM yelp.$TABLE;" 2>/dev/null | tr -d ' ')

    if [ -n "$COUNT" ] && [ "$COUNT" -ge 0 ]; then
        echo "[✓] yelp.$TABLE existe ($COUNT lignes)"
    else
        echo "[✗] yelp.$TABLE n'existe pas ou est inaccessible"
        ALL_EXIST=false
    fi
done

echo ""

if [ "$ALL_EXIST" = false ]; then
    echo "[AVERTISSEMENT] Certaines tables sources sont manquantes"
    echo "L'ETL pourrait échouer si les tables ne sont pas créées"
    exit 1
fi

# Test 4 : Vérification de la table de destination
echo "─────────────────────────────────────────"
echo "TEST 4 : Table de destination"
echo "─────────────────────────────────────────"

FACT_EXISTS=$(PGPASSWORD="yelp_pass" psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = 'yelp' AND table_name = 'fact_user_pertinence');" 2>/dev/null | tr -d ' ')

if [ "$FACT_EXISTS" = "t" ]; then
    COUNT=$(PGPASSWORD="yelp_pass" psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -t -c "SELECT COUNT(*) FROM yelp.fact_user_pertinence;" 2>/dev/null | tr -d ' ')
    echo "[✓] yelp.fact_user_pertinence existe ($COUNT lignes)"
    echo "[INFO] La table sera écrasée en mode overwrite lors de l'ETL"
else
    echo "[!] yelp.fact_user_pertinence n'existe pas"
    echo "[INFO] Elle sera créée automatiquement lors de l'ETL"
    echo "[RECOMMANDATION] Ou créer manuellement avec : psql -U yelp_user -d yelp -f create_fact_table.sql"
fi

echo ""

# Test 5 : Structure de la table user
echo "─────────────────────────────────────────"
echo "TEST 5 : Structure de yelp.user"
echo "─────────────────────────────────────────"

PGPASSWORD="yelp_pass" psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT column_name, data_type FROM information_schema.columns WHERE table_schema = 'yelp' AND table_name = 'user' ORDER BY ordinal_position LIMIT 10;" 2>/dev/null

echo ""

# Test 6 : Échantillon de données
echo "─────────────────────────────────────────"
echo "TEST 6 : Échantillon de données (yelp.user)"
echo "─────────────────────────────────────────"

PGPASSWORD="yelp_pass" psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT user_id, name, review_count, fans FROM yelp.user LIMIT 3;" 2>/dev/null

echo ""
echo "═══════════════════════════════════════"
echo "  TESTS TERMINÉS"
echo "═══════════════════════════════════════"
echo ""
echo "Statut : Prêt pour l'ETL !"
echo ""
echo "Pour lancer l'ETL :"
echo "  ./run-spark-scala.sh      (rapide, recommandé)"
echo "  ./compile-spark.sh && ./run-spark.sh"
echo "  ./build-sbt.sh && ./run-sbt.sh"
echo ""
