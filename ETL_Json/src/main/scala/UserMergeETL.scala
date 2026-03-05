package etl

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.Window

/**
 * Fusionne users.csv (ETL principal) et dim_user.csv (modèle dimensionnel)
 * en appliquant 3 règles :
 *   1. Supprimer les users n'ayant QUE des reviews sur des business invalides
 *   2. Ajouter les users présents dans les reviews valides mais absents des deux sources
 *   3. En cas de doublon, conserver les données du user ayant le plus de useful
 *
 * Schéma de sortie : union des colonnes des deux sources
 *   - users.csv    : user_id, name, review_count, yelping_since, useful, funny, cool,
 *                    fans, average_stars, compliment_*, friend_count
 *   - dim_user.csv : id_review, id_elite, id_tip
 */
object UserMergeETL {

  def process(
    spark:         SparkSession,
    userDF:        DataFrame,  // sortie UserETL (déjà filtrée sur reviews valides)
    reviewDF:      DataFrame,  // reviews filtrées sur business valides
    validBizDF:    DataFrame,  // business_id valides
    dimUserPath:   String,     // chemin vers dim_user.csv
    dimReviewPath: String      // chemin vers dim_review.csv
  ): DataFrame = {

    import spark.implicits._

    // ── 1. Users ayant au moins 1 review sur un business valide ──────────────
    val validUserIds = reviewDF
      .join(validBizDF, Seq("business_id"), "inner")
      .select(lower(col("user_id")).as("uid_lower"))
      .distinct()

    // ── 2. Préparer users.csv ─────────────────────────────────────────────────
    // Colonnes dim absentes → null + clé de jointure lowercase
    val usersNorm = userDF
      .withColumn("uid_lower",     lower(col("user_id")))
      .withColumn("id_review",     lit(null).cast("string"))
      .withColumn("id_elite",      lit(null).cast("string"))
      .withColumn("id_tip",        lit(null).cast("string"))
      .withColumn("source_useful", col("useful").cast("double"))

    // ── 3. Préparer dim_user.csv ──────────────────────────────────────────────
    val dimUser   = spark.read.option("header", "true").csv(dimUserPath)
    val dimReview = spark.read.option("header", "true").csv(dimReviewPath)

    val dimUserNorm = dimUser
      .join(dimReview, Seq("id_review"), "left")
      .select(
        col("user_id").as("uid_lower"),
        col("user_id"),
        col("name"),
        col("nbr_reviews").cast("long").as("review_count"),
        lit(null).cast("string").as("yelping_since"),
        col("total_useful").cast("double").as("useful"),
        col("total_funny").cast("double").as("funny"),
        col("total_cool").cast("double").as("cool"),
        col("fans").cast("long"),
        col("avg_stars").cast("double").as("average_stars"),
        lit(null).cast("long").as("compliment_hot"),
        lit(null).cast("long").as("compliment_more"),
        lit(null).cast("long").as("compliment_profile"),
        lit(null).cast("long").as("compliment_cute"),
        lit(null).cast("long").as("compliment_list"),
        lit(null).cast("long").as("compliment_note"),
        lit(null).cast("long").as("compliment_plain"),
        lit(null).cast("long").as("compliment_cool"),
        lit(null).cast("long").as("compliment_funny"),
        lit(null).cast("long").as("compliment_writer"),
        lit(null).cast("long").as("compliment_photos"),
        lit(null).cast("long").as("friend_count"),
        col("id_review"),
        // id_elite=0 et id_tip=0 → NULL (clé inexistante dans les tables dim)
        when(col("id_elite") === "0", lit(null)).otherwise(col("id_elite")).as("id_elite"),
        when(col("id_tip")   === "0", lit(null)).otherwise(col("id_tip")).as("id_tip"),
        coalesce(col("total_useful").cast("double"), lit(0.0)).as("source_useful")
      )

    // ── 4. Union des deux sources (full outer = union + filtre) ───────────────
    val allUsers = usersNorm.unionByName(dimUserNorm, allowMissingColumns = true)

    // ── 5. Règle 1 : garder uniquement les users avec ≥1 review valide ────────
    val filtered = allUsers.join(validUserIds, Seq("uid_lower"), "inner")

    // ── 6. Règle 3 : doublon → conserver la ligne avec le plus de useful ──────
    val w = Window.partitionBy("uid_lower").orderBy(col("source_useful").desc_nulls_last)

    val deduped = filtered
      .withColumn("rn", row_number().over(w))
      .filter(col("rn") === 1)
      .drop("rn", "uid_lower", "source_useful")

    println(s"\n=== UserMergeETL — Statistiques ===")
    println(s"Users fusionnés (user_filtered) : ${deduped.count()}")

    deduped
  }
}
