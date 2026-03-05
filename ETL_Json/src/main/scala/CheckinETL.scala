package etl

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._

object CheckinETL {

  def process(
    spark:      SparkSession,
    inputPath:  String,
    validBizDF: DataFrame
  ): DataFrame = {

    import spark.implicits._

    val raw = spark.read.json(inputPath)

    val checkinDF = raw
      // Filtrer sur les business valides avant d'exploser les dates
      .join(validBizDF.select("business_id"), Seq("business_id"), "inner")
      .withColumn("checkin_datetime", explode(split(col("date"), ", ")))
      .withColumn("checkin_datetime", trim(col("checkin_datetime")).cast("timestamp"))
      .withColumn("checkin_id", monotonically_increasing_id())
      .select(
        col("checkin_id"),
        col("business_id"),
        col("checkin_datetime"),
        hour(col("checkin_datetime")).as("hour_of_day"),
        dayofweek(col("checkin_datetime")).as("day_of_week"),
        month(col("checkin_datetime")).as("month"),
        year(col("checkin_datetime")).as("year")
      )

    println(s"\n=== CheckinETL — Statistiques ===")
    println(s"Checkins bruts          : ${raw.count()}")
    println(s"Checkins après filtrage : ${checkinDF.count()}")
    println(s"Business avec checkins  : ${checkinDF.select("business_id").distinct().count()}")

    println("\nDistribution par heure (top 5) :")
    checkinDF.groupBy("hour_of_day").count().orderBy(desc("count")).show(5)

    checkinDF
  }
}