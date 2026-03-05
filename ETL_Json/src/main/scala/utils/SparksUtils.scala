package utils

import org.apache.spark.sql.DataFrame
import java.io.{File, PrintWriter}

object SparkUtils {
 
  def ensureDir(path: String): Unit = new File(path).mkdirs()

  def saveAsCsv(df: DataFrame, outputPath: String, filename: String): Unit = {
    val headers = df.columns
    val writer = new PrintWriter(new File(s"$outputPath/$filename.csv"))

    writer.println(headers.mkString(","))

    var count = 0L
    val iter = df.toLocalIterator()
    while (iter.hasNext) {
      val row = iter.next()
      val values = headers.map { col =>
        val v = row.getAs[Any](col)
        if (v == null) ""
        else {
          val s = v.toString
          if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            "\"" + s.replace("\"", "\"\"") + "\""
          else s
        }
      }
      writer.println(values.mkString(","))
      count += 1
    }

    writer.close()
    println(s"  -> $filename.csv sauvegardé ($count lignes, ${headers.length} colonnes)")
  }
}
