package etl

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.expressions.Window

case class BusinessETLResult(
  businessDF:      DataFrame,
  hoursDF:         DataFrame,
  attributesDF:    DataFrame,
  parkingDF:       DataFrame,
  businessTypesDF: DataFrame,
  categoriesDF:    DataFrame  // normalisé : (business_id, category)
)

object BusinessETL {

  def process(spark: SparkSession, inputPath: String): BusinessETLResult = {
    import spark.implicits._

    val raw = spark.read.json(inputPath)

    // ========================
    // 1. HOURS
    // ========================
    val hoursMapping = raw
      .filter(col("hours").isNotNull)
      .select("business_id")
      .withColumn("hours_id", monotonically_increasing_id())

    val hoursDF = raw
      .filter(col("hours").isNotNull)
      .select("business_id", "hours.*")
      .join(hoursMapping, Seq("business_id"))
      .select(
        col("hours_id"),
        col("Monday").as("monday"),    col("Tuesday").as("tuesday"),
        col("Wednesday").as("wednesday"), col("Thursday").as("thursday"),
        col("Friday").as("friday"),    col("Saturday").as("saturday"),
        col("Sunday").as("sunday")
      )

    // ========================
    // 2. ATTRIBUTES
    // ========================
    val attributesMapping = raw
      .filter(col("attributes").isNotNull)
      .select("business_id")
      .withColumn("attributes_id", monotonically_increasing_id())

    val attributesDF = raw
      .filter(col("attributes").isNotNull)
      .select("business_id", "attributes")
      .join(attributesMapping, Seq("business_id"))
      .select(
        col("attributes_id"),
        to_json(col("attributes")).as("attributes_json")
      )

    // ========================
    // 3. PARKING (extrait des attributes)
    // ========================
    val parkingSchema = StructType(Seq(
      StructField("garage",    BooleanType),
      StructField("street",    BooleanType),
      StructField("validated", BooleanType),
      StructField("lot",       BooleanType),
      StructField("valet",     BooleanType)
    ))

    // Le champ est un dict Python-style : {'garage': True, ...} → on normalise en JSON
    val toPythonJson = udf((s: String) =>
      if (s == null || s.trim == "None") null
      else s.replace("True", "true").replace("False", "false")
             .replace("None", "null").replace("'", "\"")
    )

    val parkingMapping = raw
      .filter(col("attributes.BusinessParking").isNotNull &&
              col("attributes.BusinessParking") =!= "None")
      .select("business_id")
      .withColumn("parking_id", monotonically_increasing_id())

    val parkingDF = raw
      .filter(col("attributes.BusinessParking").isNotNull &&
              col("attributes.BusinessParking") =!= "None")
      .select(col("business_id"), col("attributes.BusinessParking").as("parking_raw"))
      .withColumn("parking_parsed",
        from_json(toPythonJson(col("parking_raw")), parkingSchema))
      .join(parkingMapping, Seq("business_id"))
      .select(
        col("parking_id"),
        col("parking_parsed.garage").as("garage"),
        col("parking_parsed.street").as("street"),
        col("parking_parsed.validated").as("validated"),
        col("parking_parsed.lot").as("lot"),
        col("parking_parsed.valet").as("valet")
      )

    // ========================
    // 4. CATEGORIES (normalisées — 1 ligne par business/catégorie)
    // ========================
    val categoriesExploded = raw
      .filter(col("categories").isNotNull)
      .withColumn("category", explode(split(col("categories"), ", ")))
      .select("business_id", "category")

    // Statistiques sur les catégories
    val catCounts = categoriesExploded
      .groupBy("business_id").agg(count("*").as("cat_count"))
    val maxRow = catCounts.orderBy(desc("cat_count")).head()
    val maxCats = maxRow.getLong(1)
    val maxBizId = maxRow.getString(0)

    println(s"  Nombre max de catégories par business : $maxCats (business_id = $maxBizId)")

    val categoriesDF = categoriesExploded

    // ========================
    // 5. BUSINESS TYPES (type dominant par business)
    // ========================
    val typePatterns = Map(
      "Restaurant"  -> "(?i).*(restaurant|food|cuisine|diner|bistro|eatery).*",
      "Bar/Pub"     -> "(?i).*(bar|pub|tavern|lounge|brewery|gastropub).*",
      "Bakery"      -> "(?i).*(bakery|dessert|cake|pastry|donut|bread).*",
      "Coffee Shop" -> "(?i).*(coffee|cafe|espresso|tea).*",
      "Hotel"       -> "(?i).*(hotel|motel|lodging|hostel).*"
    )

    val classifyUDF = udf((cat: String) =>
      if (cat == null) "Other"
      else typePatterns.find { case (_, p) => cat.matches(p) }.map(_._1).getOrElse("Other")
    )

    val dominantType = categoriesExploded
      .withColumn("business_type", classifyUDF(col("category")))
      .filter(col("business_type") =!= "Other")
      .groupBy("business_id", "business_type")
      .agg(count("*").as("type_count"))
      .withColumn("rank", row_number().over(
        Window.partitionBy("business_id").orderBy(desc("type_count"), col("business_type"))
      ))
      .filter(col("rank") === 1)
      .select("business_id", "business_type")

    val businessTypesDF = dominantType
      .select("business_type").distinct()
      .withColumn("business_type_id", monotonically_increasing_id())
      .select("business_type_id", "business_type")

    val businessTypesMapped = dominantType
      .join(businessTypesDF, Seq("business_type"))
      .select("business_id", "business_type_id")

    // ========================
    // 6. BUSINESS PRINCIPAL (avec toutes les FK)
    // ========================
    val businessDF = raw
      .select(
        col("business_id"), col("name"), col("address"),
        col("city"), col("state"), col("postal_code"),
        col("latitude"), col("longitude"),
        col("stars"), col("review_count"), col("is_open"),
        when(col("attributes.RestaurantsReservations") === "True", lit(true))
          .otherwise(lit(false)).as("reservations")
      )
      .join(hoursMapping,      Seq("business_id"), "left")
      .join(attributesMapping, Seq("business_id"), "left")
      .join(parkingMapping,    Seq("business_id"), "left")
      .join(businessTypesMapped, Seq("business_id"), "inner") // inner = filtre les sans-type
      .select(
        col("business_id"), col("name"), col("address"),
        col("city"), col("state"), col("postal_code"),
        col("latitude"), col("longitude"),
        col("stars"), col("review_count"), col("is_open"), col("reservations"),
        col("hours_id"), col("attributes_id"), col("parking_id"), col("business_type_id")
      )

    // Filtrage en cascade (ne garder que les enregistrements référencés)
    val validIds        = businessDF.select("business_id")
    val validHoursIds   = businessDF.select("hours_id").filter(col("hours_id").isNotNull).distinct()
    val validAttrIds    = businessDF.select("attributes_id").filter(col("attributes_id").isNotNull).distinct()
    val validParkingIds = businessDF.select("parking_id").filter(col("parking_id").isNotNull).distinct()

    val hoursDFf      = hoursDF.join(validHoursIds,   Seq("hours_id"),      "inner")
    val attributesDFf = attributesDF.join(validAttrIds,  Seq("attributes_id"), "inner")
    val parkingDFf    = parkingDF.join(validParkingIds, Seq("parking_id"),    "inner")
    val categoriesDFf = categoriesDF.join(validIds,      Seq("business_id"),   "inner")

    println(s"\n=== BusinessETL — Statistiques ===")
    println(s"Business (avec type) : ${businessDF.count()}")
    println(s"Hours                : ${hoursDFf.count()}")
    println(s"Attributes           : ${attributesDFf.count()}")
    println(s"Parking              : ${parkingDFf.count()}")
    println(s"Types distincts      : ${businessTypesDF.count()}")

    println("\nDistribution des types :")
    businessDF.join(businessTypesDF, Seq("business_type_id"))
      .groupBy("business_type").count().orderBy(desc("count")).show()

    BusinessETLResult(businessDF, hoursDFf, attributesDFf, parkingDFf, businessTypesDF, categoriesDFf)
  }
}