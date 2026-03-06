package etl

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._

object ReviewETL {

  def process(
    spark:      SparkSession,
    inputPath:  String,
    validBizDF: DataFrame   // ← business_id valides issus de BusinessETL
  ): DataFrame = {

    import spark.implicits._

    val raw = spark.read.json(inputPath)

    val reviewDF = raw
      .select(
        col("review_id"),
        col("user_id"),
        col("business_id"),
        col("useful").as("total_useful"),
        col("funny").as("total_funny"),
        col("cool").as("total_cool"),
        col("stars"),
        col("date").cast("timestamp").as("date_review")
      )
      // Ne garder que les reviews pointant vers un business valide
      .join(
        validBizDF.select("business_id"),
        Seq("business_id"),
        "inner"
      )
      // Ne garder que les reviews avec au moins 1 vote utile
      .filter(col("total_useful") > 0)
      // id_review : clé surrogate Oracle (DIM_REVIEW PK)
      .withColumn("id_review", monotonically_increasing_id())
      .select(
        col("id_review"),
        col("review_id"),
        col("user_id"),
        col("business_id"),
        col("total_useful"),
        col("total_funny"),
        col("total_cool"),
        col("stars"),
        col("date_review")
      )
      .cache()  // évite de relire le JSON à chaque action

    println(s"\n=== ReviewETL — Statistiques ===")
    println(s"Reviews après filtrage  : ${reviewDF.count()}")

    val statsRow = reviewDF.agg(
      avg("stars").as("avg_stars"),
      avg("total_useful").as("avg_useful")
    ).collect()(0)
    println(f"Moyenne stars  : ${statsRow.getDouble(0)}%.2f")
    println(f"Moyenne useful : ${statsRow.getDouble(1)}%.2f")

    reviewDF
  }
}
