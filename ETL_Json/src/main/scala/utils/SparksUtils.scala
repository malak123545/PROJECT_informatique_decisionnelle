package utils

import org.apache.spark.sql.DataFrame
import java.io.File
import java.nio.file.{Files, StandardCopyOption}

object SparkUtils {

  def ensureDir(path: String): Unit = new File(path).mkdirs()

  /**
   * Sauvegarde un DataFrame en un seul fichier CSV dans outputPath/filename.csv.
   * Utilise coalesce(1) pour forcer un seul fichier de sortie, puis renomme
   * le part-*.csv en filename.csv (pas de chargement en mémoire driver).
   */
  def saveAsCsv(df: DataFrame, outputPath: String, filename: String): Unit = {
    val tmpDir  = s"$outputPath/.tmp_$filename"
    val outFile = new File(s"$outputPath/$filename.csv")

    // Écriture distribuée en 1 partition → 1 seul fichier
    df.coalesce(1)
      .write
      .option("header", "true")
      .mode("overwrite")
      .csv(tmpDir)

    // Retrouver le part-*.csv généré et le renommer
    val partFile = new File(tmpDir)
      .listFiles()
      .find(f => f.getName.startsWith("part-") && f.getName.endsWith(".csv"))
      .getOrElse(throw new RuntimeException(s"Aucun part file trouvé dans $tmpDir"))

    if (outFile.exists()) outFile.delete()
    Files.move(partFile.toPath, outFile.toPath, StandardCopyOption.REPLACE_EXISTING)

    // Supprimer le répertoire temporaire
    new File(tmpDir).listFiles().foreach(_.delete())
    new File(tmpDir).delete()

    println(s"  -> $filename.csv sauvegardé (${df.columns.length} colonnes)")
  }
}
