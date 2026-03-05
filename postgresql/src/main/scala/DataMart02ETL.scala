import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

object DataMart02ETL {

  val jdbcUrl      = "jdbc:postgresql://stendhal.iem:5432/tpid2020"
  val jdbcUser     = "tpid"
  val jdbcPassword = "tpid"
  val jdbcDriver   = "org.postgresql.Driver"

  val outputCsvBasePath = "/home/preconys/Musique/PROJECT_informatique_decisionnelle/datamart02_csv"
  val tipCsvPath   = "/home/preconys/yelp_academic_dataset_tip.csv"

  val nbPartitions = 4

  def main(args: Array[String]): Unit = {
    println("═══════════════════════════════════════")
    println("   DATA MART 02 ETL - YELP PERTINENCE")
    println("   Schéma en étoile -> CSV")
    println("═══════════════════════════════════════\n")

    val spark = SparkSession.getActiveSession.getOrElse(
      SparkSession.builder()
        .appName("DataMart02-YelpPertinence")
        .master("local[*]")
        .getOrCreate()
    )

    spark.conf.set("spark.sql.shuffle.partitions", nbPartitions.toString)
    spark.sparkContext.setLogLevel("WARN")

    try {
      println("[INFO] Démarrage du pipeline ETL\n")

      println("─────────────────────────────────────────")
      println("ÉTAPE 1 : EXTRACTION (PostgreSQL + CSV)")
      println("─────────────────────────────────────────")
      val raw = extract(spark)

      println("\n─────────────────────────────────────────")
      println("ÉTAPE 2 : NETTOYAGE (lazy)")
      println("─────────────────────────────────────────")
      val cleaned = clean(raw)

      println("\n─────────────────────────────────────────")
      println("ÉTAPE 3 : TRANSFORMATION (schéma étoile)")
      println("─────────────────────────────────────────")
      val star = transform(cleaned, spark)

      println("\n─────────────────────────────────────────")
      println("ÉTAPE 4 : ÉCRITURE CSV")
      println("─────────────────────────────────────────")
      writeStarToCsv(star)

      println("\n═══════════════════════════════════════")
      println("   ETL TERMINÉ AVEC SUCCÈS !")
      println(s"   CSV  : $outputCsvBasePath")
      println("   ├── dim_user/")
      println("   ├── dim_review/")
      println("   ├── dim_elite/")
      println("   ├── dim_tip/")
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
        .option("url",           jdbcUrl)
        .option("dbtable",       table)
        .option("user",          jdbcUser)
        .option("password",      jdbcPassword)
        .option("driver",        jdbcDriver)
        .option("fetchsize",     "1000")
        .option("socketTimeout", "0")
        .option("tcpKeepAlive",  "true")
        .load()

    println("[EXTRACT] Connexion à PostgreSQL...")
    val userDF   = readTable("yelp.user")
    val reviewDF = readTable("yelp.review")
    val eliteDF  = readTable("yelp.elite")
    println("[EXTRACT] ✓ Tables PostgreSQL lues (lazy)")

    println(s"[EXTRACT] Lecture CSV tip : $tipCsvPath")
    val tipDF = spark.read
      .option("header", "true")
      .option("inferSchema", "true")
      .csv(tipCsvPath)
    println("[EXTRACT] ✓ tip.csv lu (lazy)")

    Map("user" -> userDF, "review" -> reviewDF, "elite" -> eliteDF, "tip" -> tipDF)
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

    val cleanTip = raw("tip")
      .dropDuplicates()
      .filter(col("user_id").isNotNull)
      .filter(col("date").isNotNull)
      .withColumn("user_id", lower(trim(col("user_id"))))

    Map("user" -> cleanUser, "review" -> cleanReview, "elite" -> cleanElite, "tip" -> cleanTip)
  }

  def transform(cleaned: Map[String, DataFrame], spark: SparkSession): Map[String, DataFrame] = {
    import spark.implicits._

    val userDF   = cleaned("user")
    val reviewDF = cleaned("review")
    val eliteDF  = cleaned("elite")
    val tipDF    = cleaned("tip")

    // Dimension Review (agrégée par user) — IDs à partir de 0
    val dimReview = reviewDF.groupBy("user_id").agg(
      count("*").alias("nbr_reviews"),
      avg("stars").alias("avg_stars"),
      sum("useful").alias("total_useful"),
      sum("funny").alias("total_funny"),
      sum("cool").alias("total_cool")
    )
    .withColumn("id_review", monotonically_increasing_id())
    .select("id_review", "user_id", "nbr_reviews", "avg_stars", "total_useful", "total_funny", "total_cool")
    .cache()

    // Dimension Elite (agrégée par user) — IDs à partir de 1 (0 réservé pour "Not Elite")
    val dimElite = eliteDF.groupBy("user_id").agg(
      count("*").alias("nbr_elite_years")
    )
    .withColumn("id_elite", monotonically_increasing_id() + 1)
    .select("id_elite", "user_id", "nbr_elite_years")
    .cache()

    // Dimension Tip (agrégée par user) — IDs à partir de 1 (0 réservé pour "No Tip")
    val dimTip = tipDF.groupBy("user_id").agg(
      max("date").alias("date"),
      coalesce(sum(col("compliment_count").cast("long")), lit(0L)).alias("compliment_count")
    )
    .withColumn("id_tip", monotonically_increasing_id() + 1)
    .select("id_tip", "user_id", "date", "compliment_count")
    .cache()

    // Dimension User avec FK vers dim_review, dim_elite, dim_tip
    // id_elite et id_tip à 0 si pas de correspondance (ligne par défaut)
    val dimUser = userDF.select("user_id", "name", "fans")
      .join(dimReview.select("user_id", "id_review"), Seq("user_id"), "left")
      .join(dimElite.select("user_id",  "id_elite"),  Seq("user_id"), "left")
      .join(dimTip.select("user_id",    "id_tip"),    Seq("user_id"), "left")
      .withColumn("id_elite", coalesce(col("id_elite"), lit(0L)))
      .withColumn("id_tip",   coalesce(col("id_tip"),   lit(0L)))
      .select("user_id", "name", "fans", "id_review", "id_elite", "id_tip")

    // Dimensions finales sans user_id
    val dimReviewFinal = dimReview.select("id_review", "nbr_reviews", "avg_stars", "total_useful", "total_funny", "total_cool")
    val dimEliteFinal  = dimElite.select("id_elite", "nbr_elite_years")
    val dimTipFinal    = dimTip.select("id_tip", "compliment_count")

    // Agrégation tips pour nbr_tips dans la table de faits
    val tipByUser = tipDF.groupBy("user_id").agg(
      count("*").alias("nbr_tips")
    )

    // Table de faits
    val fact = dimUser
      .join(dimReview.select("user_id", "nbr_reviews", "avg_stars", "total_useful", "total_funny", "total_cool"), Seq("user_id"), "left")
      .join(dimElite.select("user_id", "nbr_elite_years"), Seq("user_id"), "left")
      .join(tipByUser, Seq("user_id"), "left")
      .join(dimTip.select("user_id", "compliment_count"), Seq("user_id"), "left")
      .withColumn("nbr_tips",          coalesce(col("nbr_tips"), lit(0L)))
      .withColumn("total_compliments", coalesce(col("compliment_count"), lit(0L)))
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

    Map(
      "dim_user"             -> dimUser,
      "dim_review"           -> dimReviewFinal,
      "dim_elite"            -> dimEliteFinal,
      "dim_tip"              -> dimTipFinal,
      "fact_user_pertinence" -> fact
    )
  }

  def writeStarToCsv(star: Map[String, DataFrame]): Unit = {
    def writeCsv(df: DataFrame, name: String): Unit = {
      val path = s"$outputCsvBasePath/$name.csv"
      println(s"[CSV] $name -> $path")
      df.coalesce(1)
        .write
        .mode("overwrite")
        .option("header", "true")
        .csv(path)
      println(s"[CSV] ✓ $name écrit")
    }

    writeCsv(star("dim_user"),             "dim_user")
    writeCsv(star("dim_review"),           "dim_review")
    writeCsv(star("dim_elite"),            "dim_elite")
    writeCsv(star("dim_tip"),              "dim_tip")
    writeCsv(star("fact_user_pertinence"), "fact_user_pertinence")
  }
}
