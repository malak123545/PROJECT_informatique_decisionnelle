#!/bin/bash

# Script de compilation pour DataMart02ETL.scala
# Nécessite Scala et Spark installés

echo "═══════════════════════════════════════"
echo "  COMPILATION DataMart02ETL.scala"
echo "═══════════════════════════════════════"

# Vérifier si SPARK_HOME est défini
if [ -z "$SPARK_HOME" ]; then
    echo "[ERREUR] SPARK_HOME n'est pas défini"
    echo "Veuillez définir SPARK_HOME dans votre environnement"
    echo "Exemple : export SPARK_HOME=/path/to/spark"
    exit 1
fi

echo "[INFO] SPARK_HOME = $SPARK_HOME"

# Créer le répertoire de sortie
mkdir -p bin/spark

# Compiler avec scalac
echo "[INFO] Compilation en cours..."

scalac \
    -classpath "$SPARK_HOME/jars/*:postgresql-42.7.1.jar" \
    -d bin/spark \
    src/spark/DataMart02ETL.scala

if [ $? -eq 0 ]; then
    echo "[SUCCESS] Compilation réussie !"
    echo "[INFO] Classes générées dans : bin/spark/"
else
    echo "[ERREUR] Échec de la compilation"
    exit 1
fi

echo "═══════════════════════════════════════"
