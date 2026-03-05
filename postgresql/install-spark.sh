#!/bin/bash

# Script d'installation automatique d'Apache Spark 3.5.0

echo "═══════════════════════════════════════════════════════════"
echo "  INSTALLATION AUTOMATIQUE D'APACHE SPARK 3.5.0"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Configuration
SPARK_VERSION="3.5.0"
HADOOP_VERSION="3"
SPARK_NAME="spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}"
SPARK_ARCHIVE="${SPARK_NAME}.tgz"
SPARK_URL="https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/${SPARK_ARCHIVE}"
INSTALL_DIR="$HOME/spark"

echo "[INFO] Version Spark : ${SPARK_VERSION}"
echo "[INFO] Répertoire d'installation : ${INSTALL_DIR}"
echo ""

# Vérifier si Java est installé
echo "─────────────────────────────────────────"
echo "ÉTAPE 1 : Vérification de Java"
echo "─────────────────────────────────────────"
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    echo "[✓] Java est installé : $JAVA_VERSION"
else
    echo "[✗] Java n'est pas installé"
    echo ""
    echo "Installation de Java..."
    sudo apt-get update
    sudo apt-get install -y openjdk-11-jdk

    if [ $? -eq 0 ]; then
        echo "[✓] Java installé avec succès"
    else
        echo "[✗] Échec de l'installation de Java"
        exit 1
    fi
fi
echo ""

# Vérifier si Scala est installé
echo "─────────────────────────────────────────"
echo "ÉTAPE 2 : Vérification de Scala"
echo "─────────────────────────────────────────"
if command -v scala &> /dev/null; then
    SCALA_VERSION=$(scala -version 2>&1)
    echo "[✓] Scala est installé : $SCALA_VERSION"
else
    echo "[!] Scala n'est pas installé (optionnel pour Spark)"
    echo "Installation de Scala..."
    sudo apt-get install -y scala
    echo "[✓] Scala installé"
fi
echo ""

# Télécharger Spark
echo "─────────────────────────────────────────"
echo "ÉTAPE 3 : Téléchargement de Spark"
echo "─────────────────────────────────────────"

if [ -d "${INSTALL_DIR}/${SPARK_NAME}" ]; then
    echo "[!] Spark semble déjà installé dans ${INSTALL_DIR}/${SPARK_NAME}"
    echo "Voulez-vous réinstaller ? (y/N)"
    read -r RESPONSE
    if [[ ! "$RESPONSE" =~ ^[Yy]$ ]]; then
        echo "[INFO] Installation annulée"
        SPARK_HOME="${INSTALL_DIR}/${SPARK_NAME}"
        echo "export SPARK_HOME=${SPARK_HOME}" >> ~/.bashrc
        echo "export PATH=\$SPARK_HOME/bin:\$PATH" >> ~/.bashrc
        source ~/.bashrc
        echo ""
        echo "[✓] SPARK_HOME configuré : ${SPARK_HOME}"
        exit 0
    fi
    rm -rf "${INSTALL_DIR}/${SPARK_NAME}"
fi

mkdir -p "$INSTALL_DIR"
cd "$INSTALL_DIR" || exit 1

echo "[INFO] Téléchargement depuis : $SPARK_URL"
echo "[INFO] Cela peut prendre quelques minutes..."

wget -q --show-progress "$SPARK_URL"

if [ $? -eq 0 ]; then
    echo "[✓] Téléchargement réussi"
else
    echo "[✗] Échec du téléchargement"
    echo ""
    echo "Essai avec une archive miroir..."
    MIRROR_URL="https://dlcdn.apache.org/spark/spark-${SPARK_VERSION}/${SPARK_ARCHIVE}"
    wget -q --show-progress "$MIRROR_URL"

    if [ $? -ne 0 ]; then
        echo "[✗] Échec du téléchargement depuis le miroir"
        exit 1
    fi
fi
echo ""

# Extraire l'archive
echo "─────────────────────────────────────────"
echo "ÉTAPE 4 : Extraction de l'archive"
echo "─────────────────────────────────────────"
echo "[INFO] Extraction en cours..."

tar -xzf "$SPARK_ARCHIVE"

if [ $? -eq 0 ]; then
    echo "[✓] Extraction réussie"
    rm "$SPARK_ARCHIVE"
    echo "[✓] Archive supprimée"
else
    echo "[✗] Échec de l'extraction"
    exit 1
fi
echo ""

# Configurer les variables d'environnement
echo "─────────────────────────────────────────"
echo "ÉTAPE 5 : Configuration de l'environnement"
echo "─────────────────────────────────────────"

SPARK_HOME="${INSTALL_DIR}/${SPARK_NAME}"

# Ajouter à .bashrc
if ! grep -q "SPARK_HOME" ~/.bashrc; then
    echo "" >> ~/.bashrc
    echo "# Apache Spark Configuration" >> ~/.bashrc
    echo "export SPARK_HOME=${SPARK_HOME}" >> ~/.bashrc
    echo "export PATH=\$SPARK_HOME/bin:\$PATH" >> ~/.bashrc
    echo "[✓] Variables ajoutées à ~/.bashrc"
else
    echo "[!] SPARK_HOME déjà présent dans ~/.bashrc"
fi

# Exporter pour la session actuelle
export SPARK_HOME="${SPARK_HOME}"
export PATH="$SPARK_HOME/bin:$PATH"

echo "[✓] SPARK_HOME configuré : ${SPARK_HOME}"
echo ""

# Vérifier l'installation
echo "─────────────────────────────────────────"
echo "ÉTAPE 6 : Vérification de l'installation"
echo "─────────────────────────────────────────"

"${SPARK_HOME}/bin/spark-submit" --version 2>&1 | head -n 5

if [ $? -eq 0 ]; then
    echo ""
    echo "[✓] Spark installé avec succès !"
else
    echo ""
    echo "[✗] Erreur lors de la vérification"
    exit 1
fi

echo ""
echo "═══════════════════════════════════════════════════════════"
echo "  ✅ INSTALLATION TERMINÉE AVEC SUCCÈS"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "SPARK_HOME : ${SPARK_HOME}"
echo ""
echo "Pour utiliser Spark dans cette session :"
echo "  export SPARK_HOME=${SPARK_HOME}"
echo "  export PATH=\$SPARK_HOME/bin:\$PATH"
echo ""
echo "Pour les prochaines sessions, redémarrez votre terminal ou :"
echo "  source ~/.bashrc"
echo ""
echo "Commandes disponibles :"
echo "  spark-shell    # Shell interactif Scala"
echo "  spark-submit   # Soumettre un job Spark"
echo "  pyspark        # Shell interactif Python"
echo ""
echo "Vous pouvez maintenant lancer l'ETL avec :"
echo "  ./run-spark-scala.sh"
echo ""
