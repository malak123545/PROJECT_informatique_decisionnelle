import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object DataCheck {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("DataCheck")
      .master("local[*]")
      .config("spark.driver.memory", "4g")
      .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    val out = "/home/preconys/Images/Captures d’écran/PROJECT_informatique_decisionnelle/ETL_Json/output"

    val bizDF     = spark.read.option("header","true").csv(s"${out}/business.csv")
    val usersDF   = spark.read.option("header","true").csv(s"${out}/users.csv")
    val reviewsDF = spark.read.option("header","true").csv(s"${out}/reviews.csv")

    val bizIds  = bizDF.select("business_id").distinct()
    val userIds = usersDF.select("user_id").distinct()

    val revTotal        = reviewsDF.count()
    val orphanBiz       = reviewsDF.join(bizIds,  Seq("business_id"), "left_anti").count()
    val orphanUser      = reviewsDF.join(userIds, Seq("user_id"),     "left_anti").count()
    val usersNoReview   = usersDF.join(
                            reviewsDF.select("user_id").distinct(),
                            Seq("user_id"), "left_anti").count()

    println("\n=== VÉRIFICATION INTÉGRITÉ DES DONNÉES ===")
    println(f"Total business       : ${bizDF.count()}%,d")
    println(f"Total users          : ${usersDF.count()}%,d")
    println(f"Total reviews        : ${revTotal}%,d")
    println("")
    println(f"[Reviews] business_id invalide    : ${orphanBiz}%,d  ${if (orphanBiz == 0) "✓ OK" else "✗ PROBLÈME"}")
    println(f"[Reviews] user_id invalide        : ${orphanUser}%,d  ${if (orphanUser == 0) "✓ OK" else "✗ PROBLÈME"}")
    println(f"[Users]   sans aucune review      : ${usersNoReview}%,d  ${if (usersNoReview == 0) "✓ OK" else "✗ PROBLÈME"}")

    spark.stop()
  }
}
