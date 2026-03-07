import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import utils.SparkUtils

// ═══════════════════════════════════════════════════════════
// PHASE 2 : RÉCONCILIATION CSV (Phase 1) ↔ PostgreSQL
//
// Reviews :
//   - Charge reviews.csv (Phase 1, déjà filtré sur business valides par type)
//   - Lit yelp.review depuis PostgreSQL
//   - Filtre les reviews PG sur les business présents dans business.csv
//   - N'ajoute que les reviews PG absentes du CSV (anti-join sur review_id)
//   - Régénère id_review après merge pour garantir l'unicité
//   - Écrase reviews.csv avec le résultat enrichi
//
// Users :
//   - Charge users.csv (Phase 1)
//   - Lit yelp.user depuis PostgreSQL
//   - Conflit (user présent dans les deux) : garde celui qui a le plus de
//     review_count ; à égalité, le CSV gagne
//   - Ajoute les users PG-only
//   - Recalcule review_count / average_stars / useful / funny / cool
//     depuis les reviews finales (stats cohérentes avec nos données réelles)
//   - Écrase users.csv avec le résultat
// ═══════════════════════════════════════════════════════════

object ReconcileWithPostgres {

  val jdbcUrl      = "jdbc:postgresql://stendhal.iem:5432/tpid2020"
  val jdbcUser     = "tpid"
  val jdbcPassword = "tpid"
  val jdbcDriver   = "org.postgresql.Driver"

  def run(spark: SparkSession, outputDir: String): Unit = {

    println("\n" + "=" * 55)
    println("  PHASE 2 : RECONCILIATION CSV <-> POSTGRESQL")
    println("=" * 55)

    val connProps = new java.util.Properties()
    connProps.setProperty("user",     jdbcUser)
    connProps.setProperty("password", jdbcPassword)
    connProps.setProperty("driver",   jdbcDriver)

    def readPg(table: String): DataFrame =
      spark.read
        .format("jdbc")
        .option("url",           jdbcUrl)
        .option("dbtable",       table)
        .option("user",          jdbcUser)
        .option("password",      jdbcPassword)
        .option("driver",        jdbcDriver)
        .option("fetchsize",     "5000")
        .option("socketTimeout", "0")
        .load()

    // ── Lecture des CSV produits en Phase 1 ──────────────
    println("\n[1/3] Lecture des CSV Phase 1...")
    val csvReviews = spark.read
      .option("header",      "true")
      .option("inferSchema", "true")
      .csv(s"$outputDir/reviews.csv")
      .cache()

    val csvUsers = spark.read
      .option("header",      "true")
      .option("inferSchema", "true")
      .csv(s"$outputDir/users.csv")
      .cache()

    val validBizIds = spark.read
      .option("header",      "true")
      .option("inferSchema", "true")
      .csv(s"$outputDir/business.csv")
      .select("business_id")
      .cache()

    println(s"  -> ${csvReviews.count()} reviews CSV")
    println(s"  -> ${csvUsers.count()} users CSV")
    println(s"  -> ${validBizIds.count()} business valides")

    // ══════════════════════════════════════════════════════
    // REVIEWS
    // Schéma cible DIM_REVIEW :
    //   id_review, review_id, user_id, business_id,
    //   total_useful, total_funny, total_cool, stars, date_review
    // ══════════════════════════════════════════════════════
    println("\n[2/3] Reconciliation reviews...")

    val pgReviewsRaw = readPg("yelp.review")

    val dateCol = if (pgReviewsRaw.columns.contains("date_review")) "date_review" else "date"

    // Aligner le schéma PG sur DIM_REVIEW (sans id_review — regénéré après merge)
    val pgReviews = pgReviewsRaw
      .select(
        col("review_id"),
        col("user_id"),
        col("business_id"),
        col("useful").as("total_useful"),
        col("funny").as("total_funny"),
        col("cool").as("total_cool"),
        col("stars"),
        col(dateCol).cast("timestamp").as("date_review")
      )
      .join(validBizIds, Seq("business_id"), "inner")
      .filter(col("total_useful") > 0)

    // Nouvelles reviews PG absentes du CSV
    val pgReviewsNew = pgReviews.join(
      csvReviews.select("review_id"),
      Seq("review_id"),
      "left_anti"
    )

    val addedReviewCount = pgReviewsNew.count()
    println(s"  -> Reviews CSV                  : ${csvReviews.count()}")
    println(s"  -> Reviews PG (apres filtre biz): ${pgReviews.count()}")
    println(s"  -> Reviews PG ajoutees (new)    : $addedReviewCount")

    // Colonnes sans id_review pour l'union
    val reviewColsNoPk = csvReviews.columns.filter(_ != "id_review")

    // Ajouter null id_review aux nouvelles reviews PG avant union
    val pgReviewsNewAligned = pgReviewsNew
      .withColumn("id_review", lit(null).cast("long"))
      .select(csvReviews.columns.map(col): _*)

    // Union puis régénération de id_review pour garantir l'unicité
    val mergedReviews = csvReviews
      .union(pgReviewsNewAligned)
      .drop("id_review")
      .withColumn("id_review", monotonically_increasing_id())
      .select(csvReviews.columns.map(col): _*)
      .cache()

    println(s"  -> Total reviews fusionnees     : ${mergedReviews.count()}")
    SparkUtils.saveAsCsv(mergedReviews, outputDir, "reviews")

    // ══════════════════════════════════════════════════════
    // USERS
    // ══════════════════════════════════════════════════════
    println("\n[3/3] Reconciliation users...")

    val pgUsersRaw = readPg("yelp.user")

    val pgFriendCount = readPg("yelp.friend")
      .groupBy("user_id")
      .agg(count("*").cast("long").as("friend_count"))

    val pgUsersWithFriends = pgUsersRaw
      .join(pgFriendCount, Seq("user_id"), "left")
      .withColumn("friend_count", coalesce(col("friend_count"), lit(0L)))
      .filter(col("review_count") > 0)

    val csvUserCols = csvUsers.columns
    val pgUsersAligned = csvUserCols.foldLeft(pgUsersWithFriends) { (df, colName) =>
      if (df.columns.contains(colName)) df
      else df.withColumn(colName, lit(0))
    }.select(csvUserCols.map(c => col(c)): _*)

    val csvOnlyUsers = csvUsers.join(
      pgUsersAligned.select("user_id"),
      Seq("user_id"),
      "left_anti"
    )

    val pgOnlyUsers = pgUsersAligned.join(
      csvUsers.select("user_id"),
      Seq("user_id"),
      "left_anti"
    )

    val pgReviewCountRef = pgUsersAligned
      .select(col("user_id"), col("review_count").as("pg_review_count"))

    val csvConflict = csvUsers
      .join(pgReviewCountRef, Seq("user_id"), "inner")
      .filter(col("review_count") >= col("pg_review_count"))
      .drop("pg_review_count")

    val csvReviewCountRef = csvUsers
      .select(col("user_id"), col("review_count").as("csv_review_count"))

    val pgConflict = pgUsersAligned
      .join(csvReviewCountRef, Seq("user_id"), "inner")
      .filter(col("review_count") > col("csv_review_count"))
      .drop("csv_review_count")

    val pgOnlyCount      = pgOnlyUsers.count()
    val csvConflictCount = csvConflict.count()
    val pgConflictCount  = pgConflict.count()

    println(s"  -> Users CSV-only            : ${csvOnlyUsers.count()}")
    println(s"  -> Users PG-only ajoutes     : $pgOnlyCount")
    println(s"  -> Conflits resolus → CSV    : $csvConflictCount")
    println(s"  -> Conflits resolus → PG     : $pgConflictCount")

    val mergedUsers = csvOnlyUsers
      .union(csvConflict)
      .union(pgOnlyUsers)
      .union(pgConflict)

    println(s"  -> Total users fusionnes     : ${mergedUsers.count()}")

    println("\n  Recalcul des stats users depuis les reviews finales...")
    val updatedUsers = updateUserStats(mergedUsers, mergedReviews)
      .filter(col("review_count") > 0)
    SparkUtils.saveAsCsv(updatedUsers, outputDir, "users")

    csvReviews.unpersist()
    csvUsers.unpersist()
    validBizIds.unpersist()
    mergedReviews.unpersist()

    println("\n" + "=" * 55)
    println("  Reconciliation terminee avec succes !")
    println("=" * 55)
  }

  // Recalcule review_count, average_stars, useful, funny, cool
  // depuis les reviews finales (cohérence avec les données réelles filtrées)
  def updateUserStats(usersDF: DataFrame, reviewsDF: DataFrame): DataFrame = {
    val statsFromReviews = reviewsDF
      .groupBy("user_id")
      .agg(
        count("*").cast("int").as("review_count"),
        round(avg("stars"), 2).as("average_stars"),
        sum("total_useful").cast("long").as("useful"),
        sum("total_funny").cast("long").as("funny"),
        sum("total_cool").cast("long").as("cool")
      )

    usersDF
      .drop("review_count", "average_stars", "useful", "funny", "cool")
      .join(statsFromReviews, Seq("user_id"), "left")
      .withColumn("review_count",  coalesce(col("review_count"),  lit(0).cast("int")))
      .withColumn("average_stars", coalesce(col("average_stars"), lit(0.0)))
      .withColumn("useful",        coalesce(col("useful"),        lit(0L)))
      .withColumn("funny",         coalesce(col("funny"),         lit(0L)))
      .withColumn("cool",          coalesce(col("cool"),          lit(0L)))
  }
}
