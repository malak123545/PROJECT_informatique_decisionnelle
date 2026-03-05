#!/bin/bash

# Script tout-en-un pour installer Spark et exécuter l'ETL

echo "═══════════════════════════════════════════════════════════"
echo "  LANCEMENT AUTOMATIQUE DE L'ETL DATA MART 02"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Vérifier si Spark est installé
if [ -z "$SPARK_HOME" ]; then
    echo "[INFO] SPARK_HOME n'est pas défini"
    echo ""

    # Chercher Spark dans les emplacements communs
    POSSIBLE_SPARK_HOMES=(
        "$HOME/spark/spark-3.5.0-bin-hadoop3"
        "/opt/spark"
        "/usr/local/spark"
        "$HOME/spark"
    )

    for SPARK_DIR in "${POSSIBLE_SPARK_HOMES[@]}"; do
        if [ -d "$SPARK_DIR" ] && [ -f "$SPARK_DIR/bin/spark-submit" ]; then
            echo "[✓] Spark trouvé dans : $SPARK_DIR"
            export SPARK_HOME="$SPARK_DIR"
            export PATH="$SPARK_HOME/bin:$PATH"
            break
        fi
    done

    if [ -z "$SPARK_HOME" ]; then
        echo "[!] Spark n'est pas installé sur votre système"
        echo ""
        echo "Voulez-vous installer Spark automatiquement ? (Y/n)"
        read -r RESPONSE

        if [[ ! "$RESPONSE" =~ ^[Nn]$ ]]; then
            echo ""
            echo "Lancement de l'installation de Spark..."
            ./install-spark.sh

            if [ $? -eq 0 ]; then
                # Recharger l'environnement
                source ~/.bashrc
                echo ""
                echo "[✓] Spark installé avec succès"
            else
                echo "[✗] Échec de l'installation de Spark"
                exit 1
            fi
        else
            echo ""
            echo "Installation annulée. Veuillez installer Spark manuellement."
            exit 1
        fi
    fi
fi

echo ""
echo "[INFO] SPARK_HOME = $SPARK_HOME"
echo ""

# Vérifier la connexion PostgreSQL
echo "─────────────────────────────────────────"
echo "ÉTAPE 1 : Test de connexion PostgreSQL"
echo "─────────────────────────────────────────"
./test-connection.sh

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERREUR] Problème de connexion PostgreSQL"
    echo "Veuillez vérifier que :"
    echo "  1. PostgreSQL est démarré"
    echo "  2. La base 'yelp' existe"
    echo "  3. L'utilisateur 'yelp_user' a les bons accès"
    exit 1
fi

echo ""
echo "─────────────────────────────────────────"
echo "ÉTAPE 2 : Création de la table de faits"
echo "─────────────────────────────────────────"

TABLE_EXISTS=$(PGPASSWORD="yelp_pass" psql -h localhost -U yelp_user -d yelp -t -c "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema = 'yelp' AND table_name = 'fact_user_pertinence');" 2>/dev/null | tr -d ' ')

if [ "$TABLE_EXISTS" = "t" ]; then
    echo "[!] La table fact_user_pertinence existe déjà"
    echo "Elle sera écrasée (mode overwrite)"
else
    echo "[INFO] Création de la table fact_user_pertinence..."
    PGPASSWORD="yelp_pass" psql -h localhost -U yelp_user -d yelp -f create_fact_table.sql > /dev/null 2>&1

    if [ $? -eq 0 ]; then
        echo "[✓] Table créée avec succès"
    else
        echo "[!] La table sera créée automatiquement par Spark"
    fi
fi

echo ""
echo "─────────────────────────────────────────"
echo "ÉTAPE 3 : Lancement de l'ETL Spark"
echo "─────────────────────────────────────────"
echo ""

./run-spark-scala.sh

if [ $? -eq 0 ]; then
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  ✅ ETL TERMINÉ AVEC SUCCÈS"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "Voulez-vous afficher les statistiques ? (Y/n)"
    read -r RESPONSE

    if [[ ! "$RESPONSE" =~ ^[Nn]$ ]]; then
        echo ""
        PGPASSWORD="yelp_pass" psql -h localhost -U yelp_user -d yelp -f verify_datamart.sql
    fi
else
    echo ""
    echo "═══════════════════════════════════════════════════════════"
    echo "  ✗ L'ETL A ÉCHOUÉ"
    echo "═══════════════════════════════════════════════════════════"
    exit 1
fi
