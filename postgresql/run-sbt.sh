#!/bin/bash

# Script d'exécution du JAR généré par SBT

echo "═══════════════════════════════════════"
echo "  EXÉCUTION JAR SBT"
echo "═══════════════════════════════════════"

JAR_FILE="target/scala-2.12/datamart02-etl.jar"

# Vérifier si le JAR existe
if [ ! -f "$JAR_FILE" ]; then
    echo "[ERREUR] JAR non trouvé : $JAR_FILE"
    echo ""
    echo "Veuillez d'abord compiler le projet avec :"
    echo "  ./build-sbt.sh"
    exit 1
fi

# Vérifier si SPARK_HOME est défini
if [ -z "$SPARK_HOME" ]; then
    echo "[ERREUR] SPARK_HOME n'est pas défini"
    echo "Veuillez définir SPARK_HOME dans votre environnement"
    echo "Exemple : export SPARK_HOME=/path/to/spark"
    exit 1
fi

echo "[INFO] SPARK_HOME = $SPARK_HOME"
echo "[INFO] JAR = $JAR_FILE"
echo "[INFO] Lancement du job Spark..."
echo ""

# Lancer avec spark-submit
$SPARK_HOME/bin/spark-submit \
    --class DataMart02ETL \
    --master local[*] \
    --driver-memory 2g \
    --executor-memory 2g \
    --conf spark.sql.shuffle.partitions=10 \
    --conf spark.sql.adaptive.enabled=true \
    $JAR_FILE

echo ""
echo "═══════════════════════════════════════"
echo "  EXÉCUTION TERMINÉE"
echo "═══════════════════════════════════════"
