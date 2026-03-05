package etl

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.expressions.Window

case class BusinessETLResult(
  businessDF:      DataFrame,  // FAIT_BUSINESS
  localisationDF:  DataFrame,  // DIM_LOCALISATION (new)
  hoursDF:         DataFrame,  // DIM_HORAIRE (business_id key, split opening/closing)
  attributesDF:    DataFrame,  // extra (hors schema Oracle)
  parkingDF:       DataFrame,  // DIM_PARKING (business_id key, paid ajouté)
  businessTypesDF: DataFrame,  // DIM_TYPE_BUSINESS (type_id, type_name)
  categoriesDF:    DataFrame   // DIM_CATEGORIE (categorie_id, business_id, categorie_name)
)

object BusinessETL {

  def process(spark: SparkSession, inputPath: String): BusinessETLResult = {
    import spark.implicits._

    val raw = spark.read.json(inputPath)

    // ========================
    // 1. DIM_LOCALISATION — 1 ligne par business
    // Colonnes Oracle : localisation_id, city, state, postal_code, address, latitude, longitude
    // ========================
    val localisationWithBiz = raw
      .select(
        col("business_id"),
        col("city"),
        col("state"),
        col("postal_code"),
        col("address"),
        col("latitude"),
        col("longitude")
      )
      .withColumn("localisation_id", monotonically_increasing_id())

    val localisationMapping = localisationWithBiz.select("business_id", "localisation_id")

    val localisationDF = localisationWithBiz.select(
      col("localisation_id"),
      col("city"),
      col("state"),
      col("postal_code"),
      col("address"),
      col("latitude"),
      col("longitude")
    )

    // ========================
    // 2. DIM_HORAIRE — business_id comme clé, split opening/closing
    // Colonnes Oracle : business_id, {day}_opening, {day}_closing pour chaque jour
    // ========================
    val days = Seq("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val hoursBase = raw
      .filter(col("hours").isNotNull)
      .select(Seq(col("business_id")) ++ days.map(d => col(s"hours.$d").as(d.toLowerCase)): _*)

    val hoursDF = days.foldLeft(hoursBase) { (df, day) =>
      val d = day.toLowerCase
      df.withColumn(s"${d}_opening", split(col(d), "-").getItem(0))
        .withColumn(s"${d}_closing", split(col(d), "-").getItem(1))
        .drop(d)
    }.select(
      col("business_id"),
      col("monday_opening"),    col("monday_closing"),
      col("tuesday_opening"),   col("tuesday_closing"),
      col("wednesday_opening"), col("wednesday_closing"),
      col("thursday_opening"),  col("thursday_closing"),
      col("friday_opening"),    col("friday_closing"),
      col("saturday_opening"),  col("saturday_closing"),
      col("sunday_opening"),    col("sunday_closing")
    )

    // ========================
    // 3. ATTRIBUTES (extra, hors schema Oracle, conservé pour info)
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
    // 4. DIM_PARKING — business_id comme clé PK, paid ajouté à 0
    // Colonnes Oracle : business_id, garage, street, lot, paid, validated, valet
    // ========================
    val parkingSchema = StructType(Seq(
      StructField("garage",    BooleanType),
      StructField("street",    BooleanType),
      StructField("validated", BooleanType),
      StructField("lot",       BooleanType),
      StructField("valet",     BooleanType)
    ))

    val toPythonJson = udf((s: String) =>
      if (s == null || s.trim == "None") null
      else s.replace("True", "true").replace("False", "false")
             .replace("None", "null").replace("'", "\"")
    )

    val parkingDF = raw
      .filter(col("attributes.BusinessParking").isNotNull &&
              col("attributes.BusinessParking") =!= "None")
      .select(col("business_id"), col("attributes.BusinessParking").as("parking_raw"))
      .withColumn("parking_parsed", from_json(toPythonJson(col("parking_raw")), parkingSchema))
      .select(
        col("business_id"),
        col("parking_parsed.garage").cast("int").as("garage"),
        col("parking_parsed.street").cast("int").as("street"),
        col("parking_parsed.lot").cast("int").as("lot"),
        lit(0).as("paid"),          // non disponible dans Yelp, défaut 0
        col("parking_parsed.validated").cast("int").as("validated"),
        col("parking_parsed.valet").cast("int").as("valet")
      )

    // ========================
    // 5. DIM_CATEGORIE — categorie_id (PK), business_id (FK), categorie_name
    // Colonnes Oracle : categorie_id, business_id, categorie_name
    // ========================
    val categoriesExploded = raw
      .filter(col("categories").isNotNull)
      .withColumn("categorie_name", explode(split(col("categories"), ", ")))
      .select("business_id", "categorie_name")

    val catCounts = categoriesExploded.groupBy("business_id").agg(count("*").as("cat_count"))
    val maxRow    = catCounts.orderBy(desc("cat_count")).head()
    println(s"  Nombre max de catégories par business : ${maxRow.getLong(1)} (business_id = ${maxRow.getString(0)})")

    val categoriesDF = categoriesExploded
      .withColumn("categorie_id", monotonically_increasing_id())
      .select("categorie_id", "business_id", "categorie_name")

    // ========================
    // 6. DIM_TYPE_BUSINESS — type_id (PK), type_name
    // Colonnes Oracle : type_id, type_name
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
      .withColumn("business_type", classifyUDF(col("categorie_name")))
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
      .withColumn("type_id", monotonically_increasing_id())
      .select(col("type_id"), col("business_type").as("type_name"))

    val businessTypesMapped = dominantType
      .join(businessTypesDF.withColumnRenamed("type_name", "business_type"), Seq("business_type"))
      .select("business_id", "type_id")

    // ========================
    // 7. FAIT_BUSINESS — localisation_id et type_id comme FKs
    // Colonnes Oracle : business_id, name, is_open, localisation_id, type_id, stars, review_count
    // ========================
    val businessDF = raw
      .select(
        col("business_id"),
        col("name"),
        col("is_open"),
        col("stars"),
        col("review_count")
      )
      .join(localisationMapping, Seq("business_id"), "left")
      .join(businessTypesMapped, Seq("business_id"), "inner") // inner = filtre les sans-type
      .select(
        col("business_id"),
        col("name"),
        col("is_open"),
        col("localisation_id"),
        col("type_id"),
        col("stars"),
        col("review_count")
      )

    // Filtrage en cascade
    val validBizIds = businessDF.select("business_id")
    val validLocIds = businessDF.select("localisation_id").filter(col("localisation_id").isNotNull).distinct()

    val localisationDFf = localisationDF.join(validLocIds, Seq("localisation_id"), "inner")
    val hoursDFf        = hoursDF.join(validBizIds,        Seq("business_id"),     "inner")
    val parkingDFf      = parkingDF.join(validBizIds,      Seq("business_id"),     "inner")
    val categoriesDFf   = categoriesDF.join(validBizIds,   Seq("business_id"),     "inner")

    println(s"\n=== BusinessETL — Statistiques ===")
    println(s"Business (avec type)  : ${businessDF.count()}")
    println(s"Localisations         : ${localisationDFf.count()}")
    println(s"Horaires              : ${hoursDFf.count()}")
    println(s"Parking               : ${parkingDFf.count()}")
    println(s"Types distincts       : ${businessTypesDF.count()}")

    println("\nDistribution des types :")
    businessDF.join(businessTypesDF, Seq("type_id"))
      .groupBy("type_name").count().orderBy(desc("count")).show()

    BusinessETLResult(businessDF, localisationDFf, hoursDFf, attributesDF, parkingDFf, businessTypesDF, categoriesDFf)
  }
}
