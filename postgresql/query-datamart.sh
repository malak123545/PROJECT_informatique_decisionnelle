#!/bin/bash

# ═══════════════════════════════════════════════════════════
# SHELL INTERACTIF SPARK POUR INTERROGER LE DATAMART02
# Permet d'utiliser SQL directement sur les JSON
# ═══════════════════════════════════════════════════════════

SPARK_HOME="${SPARK_HOME:-$HOME/spark/spark-3.5.0-bin-hadoop3}"
JSON_PATH="/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_json"

echo "═══════════════════════════════════════════════════════════"
echo "   DATAMART02 - SHELL SQL INTERACTIF"
echo "   Interrogez vos données avec SQL!"
echo "═══════════════════════════════════════════════════════════"
echo ""
echo "📂 Source: $JSON_PATH"
echo ""
echo "Tables disponibles:"
echo "  • dim_user"
echo "  • dim_review"
echo "  • dim_elite"
echo "  • fact_user_pertinence"
echo ""
echo "Exemples de requêtes:"
echo "  spark.sql(\"SELECT * FROM fact_user_pertinence LIMIT 10\").show()"
echo "  spark.sql(\"SELECT name, pertinence_score FROM fact_user_pertinence f JOIN dim_user u ON f.user_id = u.user_id ORDER BY pertinence_score DESC LIMIT 10\").show(truncate=false)"
echo ""
echo "Chargement en cours..."
echo ""

# Créer un script init pour spark-shell
cat > /tmp/init_datamart.scala << 'EOF'
// Chargement des tables
val basePath = "/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_json"

println("📂 Chargement des données...")
val dimUser = spark.read.json(s"$basePath/dim_user")
val dimReview = spark.read.json(s"$basePath/dim_review")
val dimElite = spark.read.json(s"$basePath/dim_elite")
val factPertinence = spark.read.json(s"$basePath/fact_user_pertinence")

// Enregistrement comme tables SQL
dimUser.createOrReplaceTempView("dim_user")
dimReview.createOrReplaceTempView("dim_review")
dimElite.createOrReplaceTempView("dim_elite")
factPertinence.createOrReplaceTempView("fact_user_pertinence")

println("✓ Tables chargées et prêtes à être interrogées!\n")

// Fonction helper pour les top N
def topUsers(n: Int = 10) = {
  spark.sql(s"""
    SELECT
      u.user_id,
      u.name,
      u.fans,
      f.nbr_reviews,
      ROUND(f.avg_stars, 2) as avg_stars,
      f.total_useful,
      f.nbr_elite_years,
      ROUND(f.pertinence_score, 2) as score
    FROM fact_user_pertinence f
    JOIN dim_user u ON f.user_id = u.user_id
    ORDER BY f.pertinence_score DESC
    LIMIT $n
  """).show(truncate = false)
}

// Fonction pour les stats
def stats() = {
  spark.sql("""
    SELECT
      COUNT(*) as total_users,
      COUNT(CASE WHEN nbr_reviews > 0 THEN 1 END) as active_users,
      COUNT(CASE WHEN nbr_elite_years > 0 THEN 1 END) as elite_users,
      ROUND(AVG(pertinence_score), 2) as avg_score,
      ROUND(MAX(pertinence_score), 2) as max_score
    FROM fact_user_pertinence
  """).show()
}

println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
println("Fonctions disponibles:")
println("  • topUsers(n)  - Afficher le top N utilisateurs")
println("  • stats()      - Afficher les statistiques générales")
println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
println()
EOF

# Lancer spark-shell avec le script d'initialisation
"$SPARK_HOME/bin/spark-shell" \
  --driver-memory 2g \
  --conf spark.sql.shuffle.partitions=4 \
  -i /tmp/init_datamart.scala
