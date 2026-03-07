import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import java.io.File
import java.nio.file.{Files, StandardCopyOption}

object DataFix {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().appName("DataFix").master("local[*]")
      .config("spark.driver.memory", "6g").getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    val out = "/home/preconys/Images/Captures d’écran/PROJECT_informatique_decisionnelle/ETL_Json/output"

    val bizIds  = spark.read.option("header","true").csv(s"${out}/business.csv").select("business_id").distinct()
    val userIds = spark.read.option("header","true").csv(s"${out}/users.csv").select("user_id").distinct()

    val rawReviews = spark.read.option("header","true").csv(s"${out}/reviews.csv")

    // Ne garder que les reviews avec business_id ET user_id valides
    val cleanReviews = rawReviews
      .join(bizIds,  Seq("business_id"), "inner")
      .join(userIds, Seq("user_id"),     "inner")

    val totalBefore = rawReviews.count()
    val totalAfter  = cleanReviews.count()
    println(s"Reviews avant nettoyage : $${totalBefore}")
    println(s"Reviews après nettoyage : $${totalAfter}")
    println(s"Lignes supprimées       : $${totalBefore - totalAfter}")

    // Réécriture en un seul CSV propre
    val tmpDir = s"${out}/.tmp_reviews_clean"
    cleanReviews.coalesce(1)
      .write.option("header","true").mode("overwrite").csv(tmpDir)

    val partFile = new File(tmpDir).listFiles()
      .find(f => f.getName.startsWith("part-") && f.getName.endsWith(".csv")).get
    val outFile = new File(s"${out}/reviews.csv")
    outFile.delete()
    Files.move(partFile.toPath, outFile.toPath, StandardCopyOption.REPLACE_EXISTING)
    new File(tmpDir).listFiles().foreach(_.delete())
    new File(tmpDir).delete()

    println(s"reviews.csv réécrit avec $${totalAfter} lignes propres.")
    spark.stop()
  }
}
