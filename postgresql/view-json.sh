#!/bin/bash

# ═══════════════════════════════════════════════════════════
# Script pour visualiser les données JSON du DataMart02
# ═══════════════════════════════════════════════════════════

BASE_PATH="/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_json"

# Fonction pour afficher l'aide
show_help() {
    echo "═══════════════════════════════════════════════════════════"
    echo "   VISUALISATION DES DONNÉES JSON - DATAMART02"
    echo "═══════════════════════════════════════════════════════════"
    echo ""
    echo "Usage: $0 <table> [nombre_lignes]"
    echo ""
    echo "Tables disponibles:"
    echo "  user      - Dimension utilisateur (user_id, name, fans, yelping_since)"
    echo "  review    - Dimension reviews (nbr_reviews, avg_stars, votes)"
    echo "  elite     - Dimension élite (nbr_elite_years, last_elite_year)"
    echo "  fact      - Table de faits (toutes les métriques + pertinence_score)"
    echo ""
    echo "Exemples:"
    echo "  $0 user 10          # Afficher 10 utilisateurs"
    echo "  $0 fact 5           # Afficher 5 lignes de la table de faits"
    echo "  $0 elite            # Afficher 10 élites (par défaut)"
    echo ""
    exit 0
}

# Vérifier les arguments
if [ $# -eq 0 ] || [ "$1" == "-h" ] || [ "$1" == "--help" ]; then
    show_help
fi

TABLE=$1
LINES=${2:-10}  # Par défaut 10 lignes

# Sélectionner le bon dossier
case $TABLE in
    user)
        DIR="$BASE_PATH/dim_user"
        TITLE="👤 DIMENSION USER"
        ;;
    review)
        DIR="$BASE_PATH/dim_review"
        TITLE="⭐ DIMENSION REVIEW"
        ;;
    elite)
        DIR="$BASE_PATH/dim_elite"
        TITLE="🏆 DIMENSION ELITE"
        ;;
    fact)
        DIR="$BASE_PATH/fact_user_pertinence"
        TITLE="📊 TABLE DE FAITS - PERTINENCE"
        ;;
    *)
        echo "❌ Erreur: Table inconnue '$TABLE'"
        echo ""
        show_help
        ;;
esac

# Vérifier que le dossier existe
if [ ! -d "$DIR" ]; then
    echo "❌ Erreur: Dossier '$DIR' introuvable"
    exit 1
fi

# Afficher les données
echo "═══════════════════════════════════════════════════════════"
echo "   $TITLE"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "📂 Source: $DIR"
echo "📄 Affichage: $LINES premières lignes"
echo ""

# Utiliser jq pour formatter si disponible, sinon affichage brut
if command -v jq &> /dev/null; then
    zcat "$DIR"/part-*.json.gz | head -n "$LINES" | jq .
else
    echo "⚠️  jq non installé, affichage brut (installez jq pour un meilleur formatage)"
    echo ""
    zcat "$DIR"/part-*.json.gz | head -n "$LINES"
fi

echo ""
echo "─────────────────────────────────────────"
echo "💡 Astuce: Installez jq pour un meilleur formatage"
echo "   sudo apt install jq"
echo ""
