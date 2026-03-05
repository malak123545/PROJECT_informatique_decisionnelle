package etl

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

case class UserETLResult(
  userDF:        DataFrame,
  userEliteDF:   DataFrame,
  userFriendsDF: DataFrame  // paires (user_id, friend_id)
)

object UserETL {

  def process(
    spark:     SparkSession,
    inputPath: String,
    reviewDF:  DataFrame
  ): UserETLResult = {

    import spark.implicits._

    val raw = spark.read.json(inputPath)

    val validUserIds = reviewDF.select("user_id").distinct()
    println(s"  Users référencés dans les reviews filtrées : ${validUserIds.count()}")

    val rawFiltered = raw.join(validUserIds, Seq("user_id"), "inner")

    // ========================
    // 1. ELITE (dénormalisé)
    // ========================
    val eliteExploded = rawFiltered
      .filter(col("elite").isNotNull && col("elite") =!= "")
      .select("user_id", "elite")
      .withColumn("elite_year", explode(split(col("elite"), ",")))
      .withColumn("elite_year", trim(col("elite_year")))
      .filter(col("elite_year") =!= "")

    val eliteYears = eliteExploded
      .select("elite_year").distinct()
      .collect().map(_.getString(0)).sorted

    println(s"  Années élite trouvées : ${eliteYears.mkString(", ")}")

    val userEliteDF = if (eliteYears.nonEmpty) {
      val eliteCols = eliteYears.map(year =>
        max(when(col("elite_year") === year, lit(true)).otherwise(lit(false)))
          .as(s"elite_$year")
      )
      eliteExploded.groupBy("user_id").agg(eliteCols.head, eliteCols.tail: _*)
    } else {
      spark.createDataFrame(
        spark.sparkContext.emptyRDD[org.apache.spark.sql.Row],
        StructType(Seq(StructField("user_id", StringType)))
      )
    }

    // ========================
    // 2. FRIENDS — paires (user_id, friend_id)
    // Une ligne par relation, les deux colonnes sont des user_ids valides.
    // Les index sur user_id et friend_id suffiront pour retrouver toutes
    // les relations d'un utilisateur donné dans les deux sens.
    // ========================
    val userFriendsDF = rawFiltered
      .filter(col("friends").isNotNull &&
              col("friends") =!= "None" &&
              col("friends") =!= "")
      .select("user_id", "friends")
      .withColumn("friend_id", explode(split(trim(col("friends")), ",")))
      .withColumn("friend_id", trim(col("friend_id")))
      // Ne garder que les amis qui sont eux-mêmes des users valides
      .join(
        validUserIds.withColumnRenamed("user_id", "friend_id"),
        Seq("friend_id"),
        "inner"
      )
      .select("user_id", "friend_id")
      .dropDuplicates()

    // ========================
    // 3. USER PRINCIPAL (avec friend_count et last_elite_year calculés)
    // ========================
    val friendCountDF = userFriendsDF
      .groupBy("user_id")
      .agg(count("friend_id").as("friend_count"))

    val lastEliteYearDF = eliteExploded
      .groupBy("user_id")
      .agg(max(col("elite_year").cast("int")).as("last_elite_year"))

    val userDF = rawFiltered
      .select(
        col("user_id"),
        col("name"),
        col("review_count"),
        col("yelping_since").cast("timestamp").as("yelping_since"),
        col("useful"),
        col("funny"),
        col("cool"),
        col("fans"),
        col("average_stars"),
        col("compliment_hot"),
        col("compliment_more"),
        col("compliment_profile"),
        col("compliment_cute"),
        col("compliment_list"),
        col("compliment_note"),
        col("compliment_plain"),
        col("compliment_cool"),
        col("compliment_funny"),
        col("compliment_writer"),
        col("compliment_photos")
      )
      .join(friendCountDF,    Seq("user_id"), "left")
      .join(lastEliteYearDF,  Seq("user_id"), "left")
      .withColumn("friend_count",
        when(col("friend_count").isNull, lit(0)).otherwise(col("friend_count")))
      .withColumn("last_elite_year",
        when(col("last_elite_year").isNull, lit(0)).otherwise(col("last_elite_year")))

    println(s"\n=== UserETL — Statistiques ===")
    println(s"Users bruts             : ${raw.count()}")
    println(s"Users après filtrage    : ${userDF.count()}")
    println(s"Users élite             : ${userEliteDF.count()}")
    println(s"Paires d'amis           : ${userFriendsDF.count()}")

    UserETLResult(userDF, userEliteDF, userFriendsDF)
  }
}