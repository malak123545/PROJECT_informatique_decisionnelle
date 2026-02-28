package etl

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._

object TipETL {

  def process(
    spark:      SparkSession,
    inputPath:  String,
    validBizDF: DataFrame,
    validUserDF: DataFrame  // users valides issus de UserETL
  ): DataFrame = {

    import spark.implicits._

    val raw = spark.read.json(inputPath)

    val tipDF = raw
      .withColumn("tip_id", monotonically_increasing_id())
      .select(
        col("tip_id"),
        col("user_id"),
        col("business_id"),
        col("text"),
        col("date").cast("timestamp").as("tip_date"),
        col("compliment_count")
      )
      // Filtrer sur les business valides
      .join(validBizDF.select("business_id"),  Seq("business_id"), "inner")
      // Filtrer sur les users valides
      .join(validUserDF.select("user_id"),     Seq("user_id"),     "inner")

    println(s"\n=== TipETL — Statistiques ===")
    println(s"Tips bruts          : ${raw.count()}")
    println(s"Tips après filtrage : ${tipDF.count()}")

    val avgCompliments = tipDF.agg(avg("compliment_count")).collect()(0).getDouble(0)
    println(f"Avg compliments     : $avgCompliments%.2f")

    tipDF
  }
}