#!/bin/bash

# Script de build avec SBT
# Compile le projet et crée un JAR exécutable avec toutes les dépendances

echo "═══════════════════════════════════════"
echo "  BUILD AVEC SBT (sbt-assembly)"
echo "═══════════════════════════════════════"

# Vérifier si SBT est installé
if ! command -v sbt &> /dev/null; then
    echo "[ERREUR] SBT n'est pas installé"
    echo "Installation recommandée :"
    echo "  # Ubuntu/Debian"
    echo "  sudo apt-get install sbt"
    echo ""
    echo "  # MacOS"
    echo "  brew install sbt"
    exit 1
fi

echo "[INFO] Version de SBT :"
sbt --version

echo ""
echo "[INFO] Compilation et création du JAR avec assembly..."
echo "[INFO] Cela peut prendre quelques minutes au premier lancement..."
echo ""

# Compiler et créer le JAR avec toutes les dépendances
sbt clean assembly

if [ $? -eq 0 ]; then
    echo ""
    echo "═══════════════════════════════════════"
    echo "[SUCCESS] Build réussi !"
    echo "═══════════════════════════════════════"
    echo ""
    echo "JAR créé : target/scala-2.12/datamart02-etl.jar"
    echo ""
    echo "Pour exécuter :"
    echo "  ./run-sbt.sh"
    echo ""
    echo "Ou directement :"
    echo "  spark-submit --class DataMart02ETL \\"
    echo "               --master local[*] \\"
    echo "               target/scala-2.12/datamart02-etl.jar"
else
    echo ""
    echo "[ERREUR] Échec du build"
    exit 1
fi
