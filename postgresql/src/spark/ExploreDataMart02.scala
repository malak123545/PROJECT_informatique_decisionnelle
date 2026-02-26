import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object ExploreDataMart02 {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Explore DataMart02")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val basePath = "/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_json"

    println("\n" + "="*60)
    println("   EXPLORATION DATA MART 02 - YELP PERTINENCE")
    println("="*60 + "\n")

    // Chargement des tables
    println("📂 Chargement des données...")
    val dimUser = spark.read.json(s"$basePath/dim_user")
    val dimReview = spark.read.json(s"$basePath/dim_review")
    val dimElite = spark.read.json(s"$basePath/dim_elite")
    val factPertinence = spark.read.json(s"$basePath/fact_user_pertinence")

    // Enregistrement des vues temporaires pour SQL
    dimUser.createOrReplaceTempView("dim_user")
    dimReview.createOrReplaceTempView("dim_review")
    dimElite.createOrReplaceTempView("dim_elite")
    factPertinence.createOrReplaceTempView("fact_user_pertinence")

    println("✓ Tables chargées\n")

    // 1. Statistiques générales
    println("-"*60)
    println("1️⃣  STATISTIQUES GÉNÉRALES")
    println("-"*60)
    println(s"Nombre d'utilisateurs: ${dimUser.count()}")
    println(s"Nombre d'utilisateurs élites: ${dimElite.count()}")
    println()

    // 2. Schémas
    println("-"*60)
    println("2️⃣  SCHÉMAS DES TABLES")
    println("-"*60)
    println("\n📋 dim_user:")
    dimUser.printSchema()

    println("\n📋 dim_review:")
    dimReview.printSchema()

    println("\n📋 dim_elite:")
    dimElite.printSchema()

    println("\n📋 fact_user_pertinence:")
    factPertinence.printSchema()

    // 3. Aperçu des données
    println("\n" + "-"*60)
    println("3️⃣  APERÇU DES DONNÉES (10 premières lignes)")
    println("-"*60)

    println("\n👤 dim_user:")
    dimUser.show(10, truncate = false)

    println("\n⭐ dim_review:")
    dimReview.show(10, truncate = false)

    println("\n🏆 dim_elite:")
    dimElite.show(10, truncate = false)

    println("\n📊 fact_user_pertinence:")
    factPertinence.show(10, truncate = false)

    // 4. Top 10 utilisateurs par score de pertinence
    println("\n" + "-"*60)
    println("4️⃣  TOP 10 UTILISATEURS PAR PERTINENCE")
    println("-"*60)

    val topUsers = spark.sql("""
      SELECT
        u.user_id,
        u.name,
        u.fans,
        f.nbr_reviews,
        f.avg_stars,
        f.total_useful,
        f.nbr_elite_years,
        f.pertinence_score
      FROM fact_user_pertinence f
      JOIN dim_user u ON f.user_id = u.user_id
      ORDER BY f.pertinence_score DESC
      LIMIT 10
    """)

    topUsers.show(truncate = false)

    // 5. Statistiques sur le score de pertinence
    println("\n" + "-"*60)
    println("5️⃣  DISTRIBUTION DU SCORE DE PERTINENCE")
    println("-"*60)

    factPertinence.select(
      round(mean("pertinence_score"), 2).as("moyenne"),
      round(stddev("pertinence_score"), 2).as("écart_type"),
      min("pertinence_score").as("min"),
      max("pertinence_score").as("max")
    ).show()

    // 6. Répartition par nombre d'années élite
    println("\n" + "-"*60)
    println("6️⃣  RÉPARTITION PAR ANNÉES ÉLITE")
    println("-"*60)

    spark.sql("""
      SELECT
        nbr_elite_years,
        COUNT(*) as nbr_users,
        ROUND(AVG(pertinence_score), 2) as avg_pertinence
      FROM fact_user_pertinence
      WHERE nbr_elite_years > 0
      GROUP BY nbr_elite_years
      ORDER BY nbr_elite_years DESC
    """).show(20)

    // 7. Répartition par nombre de reviews
    println("\n" + "-"*60)
    println("7️⃣  RÉPARTITION PAR VOLUME DE REVIEWS")
    println("-"*60)

    spark.sql("""
      SELECT
        CASE
          WHEN nbr_reviews = 1 THEN '1 review'
          WHEN nbr_reviews BETWEEN 2 AND 10 THEN '2-10 reviews'
          WHEN nbr_reviews BETWEEN 11 AND 50 THEN '11-50 reviews'
          WHEN nbr_reviews BETWEEN 51 AND 100 THEN '51-100 reviews'
          WHEN nbr_reviews > 100 THEN '100+ reviews'
        END as category,
        COUNT(*) as nbr_users,
        ROUND(AVG(pertinence_score), 2) as avg_pertinence
      FROM fact_user_pertinence
      GROUP BY
        CASE
          WHEN nbr_reviews = 1 THEN '1 review'
          WHEN nbr_reviews BETWEEN 2 AND 10 THEN '2-10 reviews'
          WHEN nbr_reviews BETWEEN 11 AND 50 THEN '11-50 reviews'
          WHEN nbr_reviews BETWEEN 51 AND 100 THEN '51-100 reviews'
          WHEN nbr_reviews > 100 THEN '100+ reviews'
        END
      ORDER BY avg_pertinence DESC
    """).show()

    println("\n" + "="*60)
    println("   EXPLORATION TERMINÉE")
    println("="*60)
    println("\n💡 Vous pouvez maintenant lancer vos propres requêtes SQL !")
    println("   Exemple: spark.sql(\"SELECT * FROM fact_user_pertinence WHERE pertinence_score > 100\").show()")
    println()

    spark.stop()
  }
}
