import org.apache.spark.sql.SparkSession
import org.apache.spark.storage.StorageLevel
import org.apache.log4j.{Level, Logger}
import etl._
import utils.SparkUtils

object YelpETL {

  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("akka").setLevel(Level.WARN)

    val spark = SparkSession.builder()
      .appName("Yelp ETL Pipeline")
      .master("local[2]")
      .config("spark.sql.shuffle.partitions", "50")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    val dataDir   = if (args.length > 0) args(0) else "src/Data"
    val outputDir = if (args.length > 1) args(1) else "src/output"

    SparkUtils.ensureDir(outputDir)

    println("=" * 50)
    println("        YELP ETL PIPELINE")
    println("=" * 50)

    // ── ÉTAPE 1 : BUSINESS ────────────────────────────
    println("\n[1/5] BusinessETL...")
    val biz = BusinessETL.process(spark, s"$dataDir/yelp_academic_dataset_business.json")

    // Sous-tables en CSV (pas concernées par le filtrage user)
    SparkUtils.saveAsCsv(biz.hoursDF,         outputDir, "hours")
    SparkUtils.saveAsCsv(biz.attributesDF,    outputDir, "attributes")
    SparkUtils.saveAsCsv(biz.parkingDF,       outputDir, "parking")
    SparkUtils.saveAsCsv(biz.businessTypesDF, outputDir, "business_types")
    SparkUtils.saveAsCsv(biz.categoriesDF,    outputDir, "categories")

    val validBusinessDF = biz.businessDF.select("business_id").cache()
    println(s"  -> ${validBusinessDF.count()} business valides en cache")

    // ── ÉTAPE 2 : REVIEWS (filtrées sur business) ─────
    // ⚠️ On ne sauvegarde PAS encore — on attend le filtre user
    println("\n[2/5] ReviewETL...")
    val reviewDF = ReviewETL.process(
      spark,
      s"$dataDir/yelp_academic_dataset_review.json",
      validBusinessDF
    ).persist(StorageLevel.MEMORY_AND_DISK)

    val validReviewDF = reviewDF.select("user_id", "business_id").persist(StorageLevel.MEMORY_AND_DISK)
    println(s"  -> ${validReviewDF.select("user_id").distinct().count()} users référencés en cache")

    // ── ÉTAPE 3 : USERS (filtrés sur users des reviews) ──
    println("\n[3/5] UserETL...")
    val users = UserETL.process(
      spark,
      s"$dataDir/yelp_academic_dataset_user.json",
      validReviewDF
    )

    val validUserDF = users.userDF.select("user_id").cache()
    println(s"  -> ${validUserDF.count()} users valides en cache")

    // ── SAUVEGARDE CSV : business, reviews, users ─────
    // Les 3 tables sont maintenant toutes filtrées de bout en bout :
    //   business  : filtrés par type dans BusinessETL
    //   reviews   : filtrés sur business valides ET users valides
    //   users     : filtrés sur ceux ayant une review sur un business valide
    println("\n=== Sauvegarde CSV (après filtrage complet) ===")

    SparkUtils.saveAsCsv(biz.businessDF, outputDir, "business")

    val reviewsFinalDF = reviewDF
      .join(validUserDF, Seq("user_id"), "inner") // filtre final : exclut les reviews d'users invalides
    SparkUtils.saveAsCsv(reviewsFinalDF,    outputDir, "reviews")

    SparkUtils.saveAsCsv(users.userDF,        outputDir, "users")
    SparkUtils.saveAsCsv(users.userEliteDF,   outputDir, "user_elite")
    SparkUtils.saveAsCsv(users.userFriendsDF, outputDir, "user_friends")

    // Libérer le cache reviews (plus nécessaire)
    reviewDF.unpersist()
    validReviewDF.unpersist()

    // ── ÉTAPE 4 : CHECKINS ────────────────────────────
    println("\n[4/5] CheckinETL...")
    val checkinDF = CheckinETL.process(
      spark,
      s"$dataDir/yelp_academic_dataset_checkin.json",
      validBusinessDF
    )
    SparkUtils.saveAsCsv(checkinDF, outputDir, "checkins")

    // ── ÉTAPE 5 : TIPS ────────────────────────────────
    println("\n[5/5] TipETL...")
    val tipDF = TipETL.process(
      spark,
      s"$dataDir/yelp_academic_dataset_tip.json",
      validBusinessDF,
      validUserDF
    )
    SparkUtils.saveAsCsv(tipDF, outputDir, "tips")

    validBusinessDF.unpersist()
    validUserDF.unpersist()

    println("\n" + "=" * 50)
    println("  Phase 1 terminée avec succès !")
    println("=" * 50)

    // ── PHASE 2 : RÉCONCILIATION CSV ↔ POSTGRESQL ─────────
    ReconcileWithPostgres.run(spark, outputDir)

    println("\n" + "=" * 50)
    println("  Pipeline complet terminé avec succès !")
    println("=" * 50)

    spark.stop()
  }
}
