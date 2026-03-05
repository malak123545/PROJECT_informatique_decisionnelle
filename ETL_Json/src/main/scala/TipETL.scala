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
      .withColumn("id_tip", monotonically_increasing_id())
      .select(
        col("id_tip"),                                          // PK Oracle DIM_TIP
        col("user_id"),
        col("date").cast("timestamp").as("date_tip"),
        col("compliment_count"),
        // colonnes hors schema Oracle conservées pour usage interne/DataMart
        col("business_id"),
        col("text")
      )
      // Filtrer sur les business valides
      .join(validBizDF.select("business_id"),  Seq("business_id"), "inner")
      // Filtrer sur les users valides
      .join(validUserDF.select("user_id"),     Seq("user_id"),     "inner")
      // Ne garder que les tips avec au moins 1 compliment
      .filter(col("compliment_count") > 0)

    println(s"\n=== TipETL — Statistiques ===")
    println(s"Tips bruts          : ${raw.count()}")
    println(s"Tips après filtrage : ${tipDF.count()}")

    val avgCompliments = tipDF.agg(avg("compliment_count")).collect()(0).getDouble(0)
    println(f"Avg compliments     : $avgCompliments%.2f")

    tipDF
  }
}