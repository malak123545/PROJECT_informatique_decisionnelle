#!/bin/bash
export SPARK_HOME=/home/preconys/spark/spark-3.5.0-bin-hadoop3
# Script d'exécution directe du fichier Scala avec spark-shell
# Alternative à la compilation + spark-submit

echo "═══════════════════════════════════════"
echo "  EXÉCUTION DIRECTE (spark-shell)"
echo "═══════════════════════════════════════"

# Vérifier si SPARK_HOME est défini
if [ -z "$SPARK_HOME" ]; then
    echo "[ERREUR] SPARK_HOME n'est pas défini"
    echo "Veuillez définir SPARK_HOME dans votre environnement"
    echo "Exemple : export SPARK_HOME=/path/to/spark"
    exit 1
fi

echo "[INFO] SPARK_HOME = $SPARK_HOME"
echo "[INFO] Exécution avec spark-shell..."
echo ""

# Exécuter avec spark-shell (pipe : définition + appel main + quit)
{
  cat src/main/scala/DataMart02ETL.scala
  echo ""
  echo "DataMart02ETL.main(Array())"
  echo ":quit"
} | $SPARK_HOME/bin/spark-shell \
    --jars postgresql-42.7.1.jar \
    --driver-memory 2g \
    --executor-memory 2g \
    --conf spark.sql.shuffle.partitions=10 \
    --conf spark.sql.adaptive.enabled=true

echo ""
echo "═══════════════════════════════════════"
echo "  EXÉCUTION TERMINÉE"
echo "═══════════════════════════════════════"
