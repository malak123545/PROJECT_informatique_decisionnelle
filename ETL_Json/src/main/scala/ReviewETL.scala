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
        col("stars"),
        col("useful"),
        col("funny"),
        col("cool"),
        col("text"),
        col("date").cast("timestamp").as("review_date")
      )
      // Ne garder que les reviews pointant vers un business valide
      .join(
        validBizDF.select("business_id"),
        Seq("business_id"),
        "inner"
      )

    println(s"\n=== ReviewETL — Statistiques ===")
    println(s"Reviews brutes          : ${raw.count()}")
    println(s"Reviews après filtrage  : ${reviewDF.count()}")

    val statsRow = reviewDF.agg(
      avg("stars").as("avg_stars"),
      avg("useful").as("avg_useful")
    ).collect()(0)
    println(f"Moyenne stars  : ${statsRow.getDouble(0)}%.2f")
    println(f"Moyenne useful : ${statsRow.getDouble(1)}%.2f")

    reviewDF
  }
}