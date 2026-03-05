#!/bin/bash

# ═══════════════════════════════════════════════════════════
# Script d'import du DataMart02 vers PostgreSQL
# JSON → Spark → PostgreSQL (schéma datamart02)
# ═══════════════════════════════════════════════════════════

set -e  # Arrêter en cas d'erreur

# Configuration
PGHOST="stendhal.iem"
PGPORT="5432"
PGDATABASE="tpid2020"
PGUSER="tpid"
PGPASSWORD="tpid"

SPARK_HOME="${SPARK_HOME:-$HOME/spark/spark-3.5.0-bin-hadoop3}"
SPARK_SUBMIT="$SPARK_HOME/bin/spark-submit"
SCALA_FILE="src/spark/ImportToPostgres.scala"
TMP_JAR="/tmp/ImportToPostgres.jar"

echo "═══════════════════════════════════════════════════════════"
echo "   IMPORT DATAMART02 VERS POSTGRESQL"
echo "   JSON → Spark → PostgreSQL"
echo "═══════════════════════════════════════════════════════════"
echo ""

# ───────────────────────────────────────────────────────────
# ÉTAPE 1: Vérifier la connexion PostgreSQL
# ───────────────────────────────────────────────────────────
echo "─────────────────────────────────────────"
echo "ÉTAPE 1/4: Test de connexion PostgreSQL"
echo "─────────────────────────────────────────"

export PGPASSWORD="$PGPASSWORD"

if psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c "SELECT 1;" > /dev/null 2>&1; then
    echo "[✓] Connexion PostgreSQL OK"
else
    echo "[✗] ERREUR: Impossible de se connecter à PostgreSQL"
    echo "    Host: $PGHOST:$PGPORT"
    echo "    Database: $PGDATABASE"
    echo "    User: $PGUSER"
    exit 1
fi

echo ""

# ───────────────────────────────────────────────────────────
# ÉTAPE 2: Créer le schéma datamart02
# ───────────────────────────────────────────────────────────
echo "─────────────────────────────────────────"
echo "ÉTAPE 2/4: Création du schéma datamart02"
echo "─────────────────────────────────────────"

if psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -f create_datamart_schema.sql > /dev/null 2>&1; then
    echo "[✓] Schéma datamart02 créé"
    echo "    • dim_user"
    echo "    • dim_review"
    echo "    • dim_elite"
    echo "    • fact_user_pertinence"
    echo "    • Vues analytiques"
else
    echo "[!] Avertissement: Erreur lors de la création du schéma (peut-être existe déjà)"
fi

echo ""

# ───────────────────────────────────────────────────────────
# ÉTAPE 3: Vérifier Spark
# ───────────────────────────────────────────────────────────
echo "─────────────────────────────────────────"
echo "ÉTAPE 3/4: Vérification de Spark"
echo "─────────────────────────────────────────"

if [ ! -f "$SPARK_SUBMIT" ]; then
    echo "[✗] ERREUR: Spark non trouvé à $SPARK_HOME"
    echo "    Définissez SPARK_HOME ou installez Spark"
    exit 1
fi

echo "[✓] Spark trouvé: $SPARK_HOME"
echo ""

# ───────────────────────────────────────────────────────────
# ÉTAPE 4: Import des données avec Spark
# ───────────────────────────────────────────────────────────
echo "─────────────────────────────────────────"
echo "ÉTAPE 4/4: Import des données JSON → PostgreSQL"
echo "─────────────────────────────────────────"
echo ""

# Vérifier si le driver PostgreSQL est disponible
POSTGRES_JAR=$(find "$SPARK_HOME/jars" -name "postgresql-*.jar" 2>/dev/null | head -1)

if [ -z "$POSTGRES_JAR" ]; then
    echo "[!] Driver PostgreSQL non trouvé dans $SPARK_HOME/jars"
    echo "    Tentative de téléchargement..."

    POSTGRES_VERSION="42.5.1"
    POSTGRES_JAR="$SPARK_HOME/jars/postgresql-$POSTGRES_VERSION.jar"

    wget -q "https://jdbc.postgresql.org/download/postgresql-$POSTGRES_VERSION.jar" -O "$POSTGRES_JAR"

    if [ $? -eq 0 ]; then
        echo "[✓] Driver PostgreSQL téléchargé"
    else
        echo "[✗] ERREUR: Impossible de télécharger le driver PostgreSQL"
        echo "    Téléchargez manuellement depuis: https://jdbc.postgresql.org/download/"
        exit 1
    fi
fi

echo "[INFO] Utilisation du driver JDBC: $POSTGRES_JAR"
echo ""

# Lancer l'import avec spark-shell
echo "[INFO] Lancement de l'import Spark..."
echo ""

"$SPARK_HOME/bin/spark-shell" \
    --driver-memory 4g \
    --executor-memory 4g \
    --conf spark.driver.extraClassPath="$POSTGRES_JAR" \
    --conf spark.executor.extraClassPath="$POSTGRES_JAR" \
    -i "$SCALA_FILE" \
    2>&1 | grep -E "(INFO|✓|✗|ÉTAPE|Import|═══)"

if [ ${PIPESTATUS[0]} -eq 0 ]; then
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "   ✅ IMPORT TERMINÉ AVEC SUCCÈS"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "Pour vérifier les données:"
    echo "  psql -h stendhal.iem -U tpid -d tpid2020"
    echo "  \\c tpid2020"
    echo "  SET search_path TO datamart02;"
    echo "  SELECT * FROM v_top_users LIMIT 10;"
    echo ""
else
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "   ✗ L'IMPORT A ÉCHOUÉ"
    echo "═══════════════════════════════════════════════════════════"
    exit 1
fi
