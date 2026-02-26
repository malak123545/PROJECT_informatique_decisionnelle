#!/bin/bash

# ═══════════════════════════════════════════════════════════
# Script d'export JSON → CSV pour le DataMart02
# Crée un fichier CSV par dimension
# ═══════════════════════════════════════════════════════════

set -e

JSON_BASE="/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_json"
CSV_OUTPUT="/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_csv"

echo "═══════════════════════════════════════════════════════════"
echo "   EXPORT JSON → CSV"
echo "   DataMart02 - Yelp Pertinence"
echo "═══════════════════════════════════════════════════════════"
echo ""

# Créer le dossier de sortie
mkdir -p "$CSV_OUTPUT"
echo "📁 Dossier de sortie: $CSV_OUTPUT"
echo ""

# ───────────────────────────────────────────────────────────
# 1. DIMENSION USER (avec clés étrangères id_review et id_elite)
# ───────────────────────────────────────────────────────────
echo "─────────────────────────────────────────"
echo "1/4 - Export dim_user.csv"
echo "─────────────────────────────────────────"

echo "user_id,name,fans,id_review,id_elite" > "$CSV_OUTPUT/dim_user.csv"

zcat "$JSON_BASE/dim_user"/*.json.gz | \
  jq -r '[.user_id, .name, .fans, .id_review, .id_elite] | @csv' \
  >> "$CSV_OUTPUT/dim_user.csv"

LINES_USER=$(wc -l < "$CSV_OUTPUT/dim_user.csv")
SIZE_USER=$(du -h "$CSV_OUTPUT/dim_user.csv" | cut -f1)
echo "✓ dim_user.csv créé: $((LINES_USER-1)) lignes, $SIZE_USER"
echo ""

# ───────────────────────────────────────────────────────────
# 2. DIMENSION REVIEW (sans user_id)
# ───────────────────────────────────────────────────────────
echo "─────────────────────────────────────────"
echo "2/4 - Export dim_review.csv"
echo "─────────────────────────────────────────"

echo "id_review,nbr_reviews,avg_stars,total_useful,total_funny,total_cool" > "$CSV_OUTPUT/dim_review.csv"

zcat "$JSON_BASE/dim_review"/*.json.gz | \
  jq -r '[.id_review, .nbr_reviews, .avg_stars, .total_useful, .total_funny, .total_cool] | @csv' \
  >> "$CSV_OUTPUT/dim_review.csv"

LINES_REVIEW=$(wc -l < "$CSV_OUTPUT/dim_review.csv")
SIZE_REVIEW=$(du -h "$CSV_OUTPUT/dim_review.csv" | cut -f1)
echo "✓ dim_review.csv créé: $((LINES_REVIEW-1)) lignes, $SIZE_REVIEW"
echo ""

# ───────────────────────────────────────────────────────────
# 3. DIMENSION ELITE (sans user_id)
# ───────────────────────────────────────────────────────────
echo "─────────────────────────────────────────"
echo "3/4 - Export dim_elite.csv"
echo "─────────────────────────────────────────"

echo "id_elite,nbr_elite_years" > "$CSV_OUTPUT/dim_elite.csv"

zcat "$JSON_BASE/dim_elite"/*.json.gz | \
  jq -r '[.id_elite, .nbr_elite_years] | @csv' \
  >> "$CSV_OUTPUT/dim_elite.csv"

LINES_ELITE=$(wc -l < "$CSV_OUTPUT/dim_elite.csv")
SIZE_ELITE=$(du -h "$CSV_OUTPUT/dim_elite.csv" | cut -f1)
echo "✓ dim_elite.csv créé: $((LINES_ELITE-1)) lignes, $SIZE_ELITE"
echo ""

# ───────────────────────────────────────────────────────────
# 4. TABLE DE FAITS: FACT_USER_PERTINENCE (sans id_review, id_elite)
# ───────────────────────────────────────────────────────────
echo "─────────────────────────────────────────"
echo "4/4 - Export fact_user_pertinence.csv"
echo "─────────────────────────────────────────"

echo "user_id,nbr_reviews,avg_stars,total_useful,total_funny,total_cool,nbr_elite_years,nbr_tips,total_compliments,pertinence_score" > "$CSV_OUTPUT/fact_user_pertinence.csv"

zcat "$JSON_BASE/fact_user_pertinence"/*.json.gz | \
  jq -r '[.user_id, .nbr_reviews, .avg_stars, .total_useful, .total_funny, .total_cool, .nbr_elite_years, .nbr_tips, .total_compliments, .pertinence_score] | @csv' \
  >> "$CSV_OUTPUT/fact_user_pertinence.csv"

LINES_FACT=$(wc -l < "$CSV_OUTPUT/fact_user_pertinence.csv")
SIZE_FACT=$(du -h "$CSV_OUTPUT/fact_user_pertinence.csv" | cut -f1)
echo "✓ fact_user_pertinence.csv créé: $((LINES_FACT-1)) lignes, $SIZE_FACT"
echo ""

# ═══════════════════════════════════════════════════════════
# RÉSUMÉ
# ═══════════════════════════════════════════════════════════
echo "═══════════════════════════════════════════════════════════"
echo "   ✅ EXPORT TERMINÉ AVEC SUCCÈS"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "📂 Fichiers créés dans: $CSV_OUTPUT"
echo ""
echo "  Fichier                      | Lignes         | Taille"
echo "  ─────────────────────────────┼────────────────┼────────"
printf "  %-28s | %'14d | %s\n" "dim_user.csv" "$((LINES_USER-1))" "$SIZE_USER"
printf "  %-28s | %'14d | %s\n" "dim_review.csv" "$((LINES_REVIEW-1))" "$SIZE_REVIEW"
printf "  %-28s | %'14d | %s\n" "dim_elite.csv" "$((LINES_ELITE-1))" "$SIZE_ELITE"
printf "  %-28s | %'14d | %s\n" "fact_user_pertinence.csv" "$((LINES_FACT-1))" "$SIZE_FACT"
echo ""
echo "📊 Utilisation:"
echo "  • LibreOffice: libreoffice $CSV_OUTPUT/dim_user.csv"
echo "  • Excel:       xdg-open $CSV_OUTPUT/dim_user.csv"
echo "  • Python:      pandas.read_csv('$CSV_OUTPUT/dim_user.csv')"
echo ""
echo "═══════════════════════════════════════════════════════════"
