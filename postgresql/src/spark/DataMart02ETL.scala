import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

object DataMart02ETL {

  val jdbcUrl      = "jdbc:postgresql://stendhal.iem:5432/tpid2020"
  val jdbcUser     = "tpid"
  val jdbcPassword = "tpid"
  val jdbcDriver   = "org.postgresql.Driver"

  val outputBasePath = "/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_json"
  val compression    = "gzip"

  // ✅ Nombre de partitions cible (4 = 4 fichiers par table, adapté à ta machine)
  val nbPartitions = 4

  def main(args: Array[String]): Unit = {
    println("═══════════════════════════════════════")
    println("   DATA MART 02 ETL - YELP PERTINENCE")
    println("   Schéma en étoile -> JSON")
    println("═══════════════════════════════════════\n")

    val spark = SparkSession.getActiveSession.getOrElse(
      SparkSession.builder()
        .appName("DataMart02-YelpPertinence")
        .master("local[*]")
        .getOrCreate()
    )

    // ✅ Réduit les partitions du shuffle (groupBy, join) dès le départ
    spark.conf.set("spark.sql.shuffle.partitions", nbPartitions.toString)
    spark.sparkContext.setLogLevel("WARN")

    try {
      println("[INFO] Démarrage du pipeline ETL\n")

      println("─────────────────────────────────────────")
      println("ÉTAPE 1 : EXTRACTION (PostgreSQL)")
      println("─────────────────────────────────────────")
      val raw = extract(spark)

      println("\n─────────────────────────────────────────")
      println("ÉTAPE 2 : NETTOYAGE (lazy)")
      println("─────────────────────────────────────────")
      val cleaned = clean(raw)

      println("\n─────────────────────────────────────────")
      println("ÉTAPE 3 : TRANSFORMATION (schéma étoile)")
      println("─────────────────────────────────────────")
      val star = transform(cleaned)

      println("\n─────────────────────────────────────────")
      println("ÉTAPE 4 : ÉCRITURE JSON")
      println("─────────────────────────────────────────")
      writeStarToJson(star)

      println("\n═══════════════════════════════════════")
      println("   ETL TERMINÉ AVEC SUCCÈS !")
      println(s"   Sortie : $outputBasePath")
      println("   ├── dim_user/")
      println("   ├── dim_review/")
      println("   ├── dim_elite/")
      println("   └── fact_user_pertinence/")
      println("═══════════════════════════════════════")

    } catch {
      case e: Exception =>
        println(s"\n[ERREUR] Pipeline échoué : ${e.getMessage}")
        e.printStackTrace()
    }
  }

  def extract(spark: SparkSession): Map[String, DataFrame] = {
    def readTable(table: String): DataFrame =
      spark.read
        .format("jdbc")
        .option("url",       jdbcUrl)
        .option("dbtable",   table)
        .option("user",      jdbcUser)
        .option("password",  jdbcPassword)
        .option("driver",    jdbcDriver)
        .option("fetchsize", "10000")
        .load()

    println("[EXTRACT] Connexion à PostgreSQL...")
    val userDF   = readTable("yelp.user")
    val reviewDF = readTable("yelp.review")
    val eliteDF  = readTable("yelp.elite")
    println("[EXTRACT] ✓ Tables lues (lazy)")
    Map("user" -> userDF, "review" -> reviewDF, "elite" -> eliteDF)
  }

  def clean(raw: Map[String, DataFrame]): Map[String, DataFrame] = {
    val cleanUser = raw("user")
      .dropDuplicates("user_id")
      .na.fill(0,         Seq("review_count", "fans", "average_stars"))
      .na.fill("unknown", Seq("name"))
      .filter(col("yelping_since").isNotNull)
      .filter(col("review_count") >= 0)
      .filter(col("fans") >= 0)
      .filter(col("yelping_since") >= lit("2004-01-01"))
      .filter(col("average_stars").between(0.0, 5.0))
      .withColumn("user_id", lower(trim(col("user_id"))))
      .withColumn("name",    trim(col("name")))

    val cleanReview = raw("review")
      .dropDuplicates("review_id")
      .na.fill(0,         Seq("stars", "useful", "funny", "cool"))
      .na.fill("unknown", Seq("text"))
      .filter(col("date").isNotNull)
      .withColumn("stars", when(col("stars").between(1.0, 5.0), col("stars")).otherwise(lit(0)))
      .filter(col("useful") >= 0)
      .filter(col("funny")  >= 0)
      .filter(col("cool")   >= 0)
      .withColumn("review_id",   lower(trim(col("review_id"))))
      .withColumn("user_id",     lower(trim(col("user_id"))))
      .withColumn("business_id", lower(trim(col("business_id"))))

    val cleanElite = raw("elite")
      .dropDuplicates("user_id", "year")
      .filter(col("year").isNotNull && col("user_id").isNotNull)
      .filter(col("year") >= 2004)
      .withColumn("user_id", lower(trim(col("user_id"))))

    Map("user" -> cleanUser, "review" -> cleanReview, "elite" -> cleanElite)
  }

  def transform(cleaned: Map[String, DataFrame]): Map[String, DataFrame] = {
    val userDF   = cleaned("user")
    val reviewDF = cleaned("review")
    val eliteDF  = cleaned("elite")

    // Dimension Review avec id_review (SANS user_id)
    val dimReview = reviewDF.groupBy("user_id").agg(
      count("*").alias("nbr_reviews"),
      avg("stars").alias("avg_stars"),
      sum("useful").alias("total_useful"),
      sum("funny").alias("total_funny"),
      sum("cool").alias("total_cool")
    )
    .withColumn("id_review", monotonically_increasing_id())
    .select("id_review", "user_id", "nbr_reviews", "avg_stars", "total_useful", "total_funny", "total_cool")

    // Dimension Elite avec id_elite (SANS user_id, sans last_elite_year)
    val dimElite = eliteDF.groupBy("user_id").agg(
      count("*").alias("nbr_elite_years")
    )
    .withColumn("id_elite", monotonically_increasing_id())
    .select("id_elite", "user_id", "nbr_elite_years")

    // Dimension User avec clés étrangères id_review et id_elite
    val dimUser = userDF.select("user_id", "name", "fans")
      .join(dimReview.select("user_id", "id_review"), Seq("user_id"), "left")
      .join(dimElite.select("user_id", "id_elite"), Seq("user_id"), "left")
      .select("user_id", "name", "fans", "id_review", "id_elite")

    // Dimensions finales sans user_id
    val dimReviewFinal = dimReview.select("id_review", "nbr_reviews", "avg_stars", "total_useful", "total_funny", "total_cool")
    val dimEliteFinal = dimElite.select("id_elite", "nbr_elite_years")

    // Table de faits
    val fact = dimUser
      .join(dimReview.select("user_id", "nbr_reviews", "avg_stars", "total_useful", "total_funny", "total_cool"), Seq("user_id"), "left")
      .join(dimElite.select("user_id", "nbr_elite_years"), Seq("user_id"), "left")
      .withColumn("nbr_tips",          lit(0L))
      .withColumn("total_compliments", lit(0L))
      .na.fill(0, Seq(
        "nbr_reviews", "avg_stars", "total_useful", "total_funny", "total_cool",
        "nbr_elite_years"
      ))
      .withColumn("pertinence_score",
        (col("total_useful") * 3 + col("total_cool") * 2 + col("total_funny") + col("nbr_elite_years") * 10).cast("double")
      )
      .select(
        col("user_id"),
        col("nbr_reviews"), col("avg_stars"),
        col("total_useful"), col("total_funny"), col("total_cool"),
        col("nbr_elite_years"),
        col("nbr_tips"), col("total_compliments"),
        col("pertinence_score")
      )

    Map("dim_user" -> dimUser, "dim_review" -> dimReviewFinal, "dim_elite" -> dimEliteFinal, "fact" -> fact)
  }

  def writeStarToJson(star: Map[String, DataFrame]): Unit = {
    def writeJson(df: DataFrame, name: String): Unit = {
      val path = s"$outputBasePath/$name"
      println(s"[WRITE] $name -> $path")

      // ✅ coalesce(nbPartitions) : réduit à N fichiers sans shuffle complet [web:97][web:101]
      df.coalesce(nbPartitions)
        .write
        .mode("overwrite")
        .option("compression", compression)
        .json(path)

      println(s"[WRITE] ✓ $name écrit ($nbPartitions fichiers)")
    }

    writeJson(star("dim_user"),   "dim_user")
    writeJson(star("dim_review"), "dim_review")
    writeJson(star("dim_elite"),  "dim_elite")
    writeJson(star("fact"),       "fact_user_pertinence")
  }
}
