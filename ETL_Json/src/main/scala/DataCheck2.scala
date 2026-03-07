
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object DataCheck2 {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName("DataCheck2").master("local[*]")
      .config("spark.driver.memory", "4g").getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    val out = "/home/preconys/Images/Captures d’écran/PROJECT_informatique_decisionnelle/ETL_Json/output"
    val bizDF     = spark.read.option("header","true").csv(s"${out}/business.csv")
    val reviewsDF = spark.read.option("header","true").csv(s"${out}/reviews.csv")

    val bizIds = bizDF.select("business_id").distinct()

    // Reviews avec business_id absent
    val orphans = reviewsDF.join(bizIds, Seq("business_id"), "left_anti")

    println("\n--- Exemples de reviews avec business_id invalide ---")
    orphans.select("business_id", "user_id", "review_id", "nbr_useful").show(5, truncate=false)

    println("--- Distribution nbr_useful parmi les orphelins ---")
    orphans.groupBy("nbr_useful").count().orderBy("nbr_useful").show(10)

    // Comparer useful=0 vs useful>0 dans les reviews finales
    println("--- Distribution useful dans reviews.csv ---")
    reviewsDF.groupBy(
      when(col("nbr_useful").cast("int") > 0, ">0").otherwise("<=0").as("useful_cat")
    ).count().show()

    spark.stop()
  }
}
