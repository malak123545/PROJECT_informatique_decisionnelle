import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.spark.sql.functions._

object ImportToPostgres {

  // Configuration PostgreSQL
  val jdbcUrl      = "jdbc:postgresql://stendhal.iem:5432/tpid2020"
  val jdbcUser     = "tpid"
  val jdbcPassword = "tpid"
  val jdbcDriver   = "org.postgresql.Driver"

  // Chemin vers les données JSON
  val jsonBasePath = "/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_json"

  def main(args: Array[String]): Unit = {
    println("═══════════════════════════════════════════════════════════")
    println("   IMPORT DATAMART02 VERS POSTGRESQL")
    println("   JSON → Spark → PostgreSQL")
    println("═══════════════════════════════════════════════════════════\n")

    val spark = SparkSession.builder()
      .appName("ImportDataMart02ToPostgres")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    // Configuration JDBC
    val connectionProperties = new java.util.Properties()
    connectionProperties.setProperty("user", jdbcUser)
    connectionProperties.setProperty("password", jdbcPassword)
    connectionProperties.setProperty("driver", jdbcDriver)
    connectionProperties.setProperty("batchsize", "10000")

    try {
      println("[INFO] Lecture des données JSON...\n")

      // ─────────────────────────────────────────────────────────
      // 1. DIMENSION USER
      // ─────────────────────────────────────────────────────────
      println("─────────────────────────────────────────")
      println("1/4 - Import dim_user")
      println("─────────────────────────────────────────")

      val dimUser = spark.read.json(s"$jsonBasePath/dim_user")
        .select(
          col("user_id"),
          col("name"),
          col("fans"),
          col("yelping_since").cast("date").as("yelping_since")
        )

      val userCount = dimUser.count()
      println(s"[INFO] Lignes à importer: $userCount")

      dimUser.write
        .mode(SaveMode.Overwrite)
        .jdbc(jdbcUrl, "datamart02.dim_user", connectionProperties)

      println("[✓] dim_user importée\n")

      // ─────────────────────────────────────────────────────────
      // 2. DIMENSION REVIEW
      // ─────────────────────────────────────────────────────────
      println("─────────────────────────────────────────")
      println("2/4 - Import dim_review")
      println("─────────────────────────────────────────")

      val dimReview = spark.read.json(s"$jsonBasePath/dim_review")
        .select(
          col("user_id"),
          col("nbr_reviews"),
          col("avg_stars"),
          col("total_useful"),
          col("total_funny"),
          col("total_cool")
        )

      val reviewCount = dimReview.count()
      println(s"[INFO] Lignes à importer: $reviewCount")

      dimReview.write
        .mode(SaveMode.Overwrite)
        .jdbc(jdbcUrl, "datamart02.dim_review", connectionProperties)

      println("[✓] dim_review importée\n")

      // ─────────────────────────────────────────────────────────
      // 3. DIMENSION ELITE
      // ─────────────────────────────────────────────────────────
      println("─────────────────────────────────────────")
      println("3/4 - Import dim_elite")
      println("─────────────────────────────────────────")

      val dimElite = spark.read.json(s"$jsonBasePath/dim_elite")
        .select(
          col("user_id"),
          col("nbr_elite_years"),
          col("last_elite_year")
        )

      val eliteCount = dimElite.count()
      println(s"[INFO] Lignes à importer: $eliteCount")

      dimElite.write
        .mode(SaveMode.Overwrite)
        .jdbc(jdbcUrl, "datamart02.dim_elite", connectionProperties)

      println("[✓] dim_elite importée\n")

      // ─────────────────────────────────────────────────────────
      // 4. TABLE DE FAITS: FACT_USER_PERTINENCE
      // ─────────────────────────────────────────────────────────
      println("─────────────────────────────────────────")
      println("4/4 - Import fact_user_pertinence")
      println("─────────────────────────────────────────")

      val factPertinence = spark.read.json(s"$jsonBasePath/fact_user_pertinence")
        .select(
          col("user_id"),
          col("nbr_reviews"),
          col("avg_stars"),
          col("total_useful"),
          col("total_funny"),
          col("total_cool"),
          col("nbr_elite_years"),
          col("last_elite_year"),
          col("nbr_tips"),
          col("total_compliments"),
          col("pertinence_score")
        )

      val factCount = factPertinence.count()
      println(s"[INFO] Lignes à importer: $factCount")

      factPertinence.write
        .mode(SaveMode.Overwrite)
        .jdbc(jdbcUrl, "datamart02.fact_user_pertinence", connectionProperties)

      println("[✓] fact_user_pertinence importée\n")

      // ═════════════════════════════════════════════════════════
      // RÉSUMÉ
      // ═════════════════════════════════════════════════════════
      println("═══════════════════════════════════════════════════════════")
      println("   ✅ IMPORT TERMINÉ AVEC SUCCÈS")
      println("═══════════════════════════════════════════════════════════")
      println()
      println("Statistiques d'import:")
      println(s"  • dim_user:              ${"%,d".format(userCount)} lignes")
      println(s"  • dim_review:            ${"%,d".format(reviewCount)} lignes")
      println(s"  • dim_elite:             ${"%,d".format(eliteCount)} lignes")
      println(s"  • fact_user_pertinence:  ${"%,d".format(factCount)} lignes")
      println()
      println("Schéma PostgreSQL: datamart02")
      println("  ├── dim_user")
      println("  ├── dim_review")
      println("  ├── dim_elite")
      println("  └── fact_user_pertinence")
      println()
      println("Vues disponibles:")
      println("  ├── v_top_users")
      println("  ├── v_user_activity_stats")
      println("  └── v_elite_stats")
      println("═══════════════════════════════════════════════════════════")

    } catch {
      case e: Exception =>
        println(s"\n[ERREUR] Import échoué: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    } finally {
      spark.stop()
    }
  }
}
