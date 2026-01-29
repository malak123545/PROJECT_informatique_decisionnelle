import org.apache.spark.sql.{SparkSession, DataFrame, Row}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.log4j.{Level, Logger}

object YelpETL {
  
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Yelp ETL")
      .master("local[*]")
      .getOrCreate()
    
    import spark.implicits._
    Logger.getLogger("org").setLevel(Level.WARN)
    Logger.getLogger("akka").setLevel(Level.WARN)

    // Réduire le niveau de log de Spark
    spark.sparkContext.setLogLevel("WARN")
    
    import spark.implicits._

    // Chemin du fichier JSON
    val businessJsonPath = if (args.length > 0) args(0) else "src/jsonData/business_sample.json"
    
    println(s"=== Chargement du fichier: $businessJsonPath ===\n")
    
    // Charger les données business
    val businessRaw = spark.read.json(businessJsonPath)
    
    // ===========================
    // 1. EXTRACTION DES HOURS
    // ===========================
    println("=== Extraction et normalisation des Hours ===")
    
    // Créer d'abord une correspondance business_id -> hours_id
    val businessHoursMapping = businessRaw
      .filter(col("hours").isNotNull)
      .select("business_id")
      .withColumn("hours_id", monotonically_increasing_id())
    
    // Table hours sans business_id
    val hoursDF = businessRaw
      .filter(col("hours").isNotNull)
      .select("business_id", "hours.*")
      .join(businessHoursMapping, Seq("business_id"))
      .select(
        col("hours_id"),
        col("Monday").as("monday"),
        col("Tuesday").as("tuesday"),
        col("Wednesday").as("wednesday"),
        col("Thursday").as("thursday"),
        col("Friday").as("friday"),
        col("Saturday").as("saturday"),
        col("Sunday").as("sunday")
      )
    
    println("\n5 premiers Hours:")
    hoursDF.show(5, truncate = false)
    
    // ===========================
    // 2. EXTRACTION DES ATTRIBUTES
    // ===========================
    println("\n=== Extraction et normalisation des Attributes ===")
    
    // Créer une correspondance business_id -> attributes_id
    val businessAttributesMapping = businessRaw
      .filter(col("attributes").isNotNull)
      .select("business_id")
      .withColumn("attributes_id", monotonically_increasing_id())
    
    // Table attributes sans business_id
    val attributesDF = businessRaw
      .filter(col("attributes").isNotNull)
      .select("business_id", "attributes")
      .join(businessAttributesMapping, Seq("business_id"))
      .select(
        col("attributes_id"),
        to_json(col("attributes")).as("attributes_json")
      )
    
    println("\n5 premiers Attributes:")
    attributesDF.show(5, truncate = false)
    
    // ===========================
    // 3. EXTRACTION ET CLASSIFICATION DES CATEGORIES
    // ===========================
    println("\n=== Extraction et classification des Catégories ===")
    
    // Définir les patterns pour les types de business
    val businessTypePatterns = Map(
      "Restaurant" -> "(?i).*(restaurant|food|cuisine|diner|cafe|bistro|eatery).*",
      "Bar/Pub" -> "(?i).*(bar|pub|tavern|lounge|brewery|gastropub).*",
      "Shopping" -> "(?i).*(shop|store|boutique|market|retail).*",
      "Service" -> "(?i).*(salon|spa|repair|service|cleaning).*",
      "Entertainment" -> "(?i).*(cinema|theater|club|entertainment|arcade).*",
      "Health" -> "(?i).*(doctor|hospital|clinic|medical|health|pharmacy).*",
      "Hotel" -> "(?i).*(hotel|motel|lodging|hostel).*"
    )
    
    // Exploser les catégories
    val categoriesExploded = businessRaw
      .filter(col("categories").isNotNull)
      .select(
        col("business_id"),
        explode(col("categories")).as("category")
      )
    
    // Fonction pour classifier les catégories
    val classifyCategory = udf((category: String) => {
      if (category == null) "Other"
      else {
        businessTypePatterns.find { case (_, pattern) =>
          category.matches(pattern)
        }.map(_._1).getOrElse("Other")
      }
    })
    
    // === BUSINESS TYPES ===
    // Table business_types (référence) - sans business_id
    val businessTypesTemp = categoriesExploded
      .withColumn("business_type", classifyCategory(col("category")))
      .filter(col("business_type") =!= "Other")
      .select("business_id", "business_type")
      .dropDuplicates()
    
    val businessTypesDF = businessTypesTemp
      .select("business_type")
      .distinct()
      .withColumn("business_type_id", monotonically_increasing_id())
      .select("business_type_id", "business_type")
    
    println("\n5 premiers Business Types:")
    businessTypesDF.show(5, truncate = false)
    
    // Table de mapping business -> business_types (relation N-N)
    val businessBusinessTypesDF = businessTypesTemp
      .join(businessTypesDF, Seq("business_type"))
      .select("business_id", "business_type_id")
      .dropDuplicates()
    
    println("\n5 premières relations Business-BusinessTypes:")
    businessBusinessTypesDF.show(5, truncate = false)
    
    // === CATEGORIES ===
    // Table categories (référence) - sans business_id
    val categoriesDF = categoriesExploded
      .select("category")
      .distinct()
      .withColumn("category_id", monotonically_increasing_id())
      .select("category_id", "category")
    
    println("\n5 premières Catégories:")
    categoriesDF.show(5, truncate = false)
    
    // Table de mapping business -> categories (relation N-N)
    val businessCategoriesDF = categoriesExploded
      .join(categoriesDF, Seq("category"))
      .select("business_id", "category_id")
      .dropDuplicates()
    
    println("\n5 premières relations Business-Categories:")
    businessCategoriesDF.show(5, truncate = false)
    
    // ===========================
    // 4. BUSINESS PRINCIPAL (normalisé)
    // ===========================
    println("\n=== Table Business principale (normalisée) ===")
    
    // Table business avec références aux hours et attributes (1-1)
    val businessDF = businessRaw
      .select(
        col("business_id"),
        col("name"),
        col("address"),
        col("city"),
        col("state"),
        col("postal_code"),
        col("latitude"),
        col("longitude"),
        col("stars"),
        col("review_count"),
        col("is_open")
      )
      .join(businessHoursMapping, Seq("business_id"), "left")
      .join(businessAttributesMapping, Seq("business_id"), "left")
      .select(
        col("business_id"),
        col("name"),
        col("address"),
        col("city"),
        col("state"),
        col("postal_code"),
        col("latitude"),
        col("longitude"),
        col("stars"),
        col("review_count"),
        col("is_open"),
        col("hours_id"),
        col("attributes_id")
      )
    
    println("\n5 premiers Business (normalisés):")
    businessDF.show(5, truncate = false)
    
    // ===========================
    // 5. STATISTIQUES
    // ===========================
    println("\n=== Statistiques ===")
    println(s"Nombre total de business: ${businessDF.count()}")
    println(s"Nombre de hours (table de référence): ${hoursDF.count()}")
    println(s"Nombre de attributes (table de référence): ${attributesDF.count()}")
    println(s"Nombre de types de business distincts: ${businessTypesDF.count()}")
    println(s"Nombre de catégories distinctes: ${categoriesDF.count()}")
    println(s"Nombre de relations business-types: ${businessBusinessTypesDF.count()}")
    println(s"Nombre de relations business-catégories: ${businessCategoriesDF.count()}")
    
    // Distribution des types de business
    println("\nDistribution des types de business:")
    businessBusinessTypesDF
      .join(businessTypesDF, Seq("business_type_id"))
      .groupBy("business_type")
      .count()
      .orderBy(desc("count"))
      .show()
    
    // Top catégories
    println("\nTop 10 des catégories:")
    businessCategoriesDF
      .join(categoriesDF, Seq("category_id"))
      .groupBy("category")
      .count()
      .orderBy(desc("count"))
      .show(10)
    
    // ===========================
    // 6. SAUVEGARDE (optionnel)
    // ===========================
    if (args.length > 1) {
      val outputPath = args(1)
      println(s"\n=== Sauvegarde dans: $outputPath ===")
      
      businessDF.write.mode("overwrite").parquet(s"$outputPath/business")
      hoursDF.write.mode("overwrite").parquet(s"$outputPath/hours")
      attributesDF.write.mode("overwrite").parquet(s"$outputPath/attributes")
      businessTypesDF.write.mode("overwrite").parquet(s"$outputPath/business_types")
      categoriesDF.write.mode("overwrite").parquet(s"$outputPath/categories")
      businessBusinessTypesDF.write.mode("overwrite").parquet(s"$outputPath/business_business_types")
      businessCategoriesDF.write.mode("overwrite").parquet(s"$outputPath/business_categories")
      
      println("Sauvegarde terminée!")
    }
    
    spark.stop()
  }
}