#!/bin/bash

# Script d'exécution pour DataMart02ETL
# Lance le job Spark avec spark-submit

echo "═══════════════════════════════════════"
echo "  EXÉCUTION DataMart02ETL"
echo "═══════════════════════════════════════"

# Vérifier si SPARK_HOME est défini
if [ -z "$SPARK_HOME" ]; then
    echo "[ERREUR] SPARK_HOME n'est pas défini"
    echo "Veuillez définir SPARK_HOME dans votre environnement"
    echo "Exemple : export SPARK_HOME=/path/to/spark"
    exit 1
fi

echo "[INFO] SPARK_HOME = $SPARK_HOME"
echo "[INFO] Lancement du job Spark..."
echo ""

# Lancer avec spark-submit
$SPARK_HOME/bin/spark-submit \
    --class DataMart02ETL \
    --master local[*] \
    --driver-memory 2g \
    --executor-memory 2g \
    --jars postgresql-42.7.1.jar \
    --conf spark.sql.shuffle.partitions=10 \
    --conf spark.sql.adaptive.enabled=true \
    bin/spark

echo ""
echo "═══════════════════════════════════════"
echo "  EXÉCUTION TERMINÉE"
echo "═══════════════════════════════════════"
