package utils

import org.apache.spark.sql.SparkSession
import org.apache.log4j.{Level, Logger}

object CountUsers {

  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("akka").setLevel(Level.WARN)

    val userFile = if (args.length > 0) args(0)
                   else "../../../Data/yelp_academic_dataset_user.json"

    val spark = SparkSession.builder()
      .appName("CountUsers")
      .master("local[*]")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    val df = spark.read.json(userFile)
    val count = df.count()

    println(s"Nombre de users dans le fichier : $count")

    spark.stop()
  }
}
