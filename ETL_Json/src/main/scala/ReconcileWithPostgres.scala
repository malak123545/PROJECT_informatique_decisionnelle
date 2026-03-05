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
    // ══════════════════════════════════════════════════════
    println("\n[2/3] Reconciliation reviews...")

    // Colonnes cibles (schéma du CSV Phase 1) :
    //   review_id, user_id, business_id, stars, useful, funny, cool, text, review_date
    val pgReviewsRaw = readPg("yelp.review")

    // Aligner sur le schema Oracle : date → date_review, useful/funny/cool → nbr_useful/nbr_funny/nbr_cool
    val dateCol = if (pgReviewsRaw.columns.contains("date_review")) "date_review" else "date"

    val pgReviews = pgReviewsRaw
      .select(
        col("review_id"),
        col("user_id"),
        col("business_id"),
        col("stars"),
        col("useful").as("nbr_useful"),
        col("funny").as("nbr_funny"),
        col("cool").as("nbr_cool"),
        col("text"),
        col(dateCol).cast("timestamp").as("date_review")
      )
      // Filtrer sur les business présents dans business.csv
      .join(validBizIds, Seq("business_id"), "inner")
      // Ne garder que les reviews avec au moins 1 vote utile
      .filter(col("nbr_useful") > 0)

    // Garder uniquement les reviews PG absentes du CSV (par review_id)
    val pgReviewsNew = pgReviews.join(
      csvReviews.select("review_id"),
      Seq("review_id"),
      "left_anti"
    )

    val addedReviewCount = pgReviewsNew.count()
    println(s"  -> Reviews CSV                  : ${csvReviews.count()}")
    println(s"  -> Reviews PG (apres filtre biz): ${pgReviews.count()}")
    println(s"  -> Reviews PG ajoutees (new)    : $addedReviewCount")

    // Union : schema CSV + nouvelles PG
    val mergedReviews = csvReviews.union(
      pgReviewsNew.select(csvReviews.columns.map(col): _*)
    ).cache()

    println(s"  -> Total reviews fusionnees     : ${mergedReviews.count()}")
    SparkUtils.saveAsCsv(mergedReviews, outputDir, "reviews")

    // ══════════════════════════════════════════════════════
    // USERS
    // ══════════════════════════════════════════════════════
    println("\n[3/3] Reconciliation users...")

    val pgUsersRaw = readPg("yelp.user")

    // Calcul du friend_count depuis yelp.friend (1 ligne par relation user_id -> friend_id)
    val pgFriendCount = readPg("yelp.friend")
      .groupBy("user_id")
      .agg(count("*").cast("long").as("friend_count"))

    // Joindre le friend_count calculé aux users PG avant d'aligner le schema
    val pgUsersWithFriends = pgUsersRaw
      .join(pgFriendCount, Seq("user_id"), "left")
      .withColumn("friend_count", coalesce(col("friend_count"), lit(0L)))
      .filter(col("review_count") > 0)

    // Aligner le schema PG sur le schema CSV
    // Les colonnes encore manquantes (ex: last_elite_year si absente de PG) sont remplies a 0
    val csvUserCols = csvUsers.columns
    val pgUsersAligned = csvUserCols.foldLeft(pgUsersWithFriends) { (df, colName) =>
      if (df.columns.contains(colName)) df
      else df.withColumn(colName, lit(0))
    }.select(csvUserCols.map(c => col(c)): _*)

    // Users presents uniquement dans le CSV → conserves tels quels
    val csvOnlyUsers = csvUsers.join(
      pgUsersAligned.select("user_id"),
      Seq("user_id"),
      "left_anti"
    )

    // Users presents uniquement dans PG → ajoutes
    val pgOnlyUsers = pgUsersAligned.join(
      csvUsers.select("user_id"),
      Seq("user_id"),
      "left_anti"
    )

    // Users en CONFLIT (presents dans les deux)
    // Regle : si review_count PG > review_count CSV → PG gagne, sinon CSV gagne
    val pgReviewCountRef = pgUsersAligned
      .select(col("user_id"), col("review_count").as("pg_review_count"))

    val csvConflict = csvUsers
      .join(pgReviewCountRef, Seq("user_id"), "inner")
      .filter(col("review_count") >= col("pg_review_count")) // CSV >= PG → CSV
      .drop("pg_review_count")

    val csvReviewCountRef = csvUsers
      .select(col("user_id"), col("review_count").as("csv_review_count"))

    val pgConflict = pgUsersAligned
      .join(csvReviewCountRef, Seq("user_id"), "inner")
      .filter(col("review_count") > col("csv_review_count")) // PG strictement > → PG
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

    // ── Recalcul des stats depuis les reviews finales ─────
    // Les review_count / average_stars / useful / funny / cool
    // sont recalcules a partir des reviews reelles qu'on possede
    // (les valeurs originales Yelp etaient sur l'ensemble non filtre)
    println("\n  Recalcul des stats users depuis les reviews finales...")
    val updatedUsers = updateUserStats(mergedUsers, mergedReviews)
      .filter(col("review_count") > 0)
    SparkUtils.saveAsCsv(updatedUsers, outputDir, "users")

    // Nettoyage
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
        sum("nbr_useful").cast("long").as("useful"),
        sum("nbr_funny").cast("long").as("funny"),
        sum("nbr_cool").cast("long").as("cool")
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
