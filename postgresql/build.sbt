// ═══════════════════════════════════════════════════════════
// Configuration SBT pour DataMart02ETL
// Alternative à la compilation manuelle avec scalac
// ═══════════════════════════════════════════════════════════

name := "DataMart02-YelpPertinence"

version := "1.0.0"

scalaVersion := "2.12.18"

// Dépendances Spark
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.0",
  "org.apache.spark" %% "spark-sql" % "3.5.0",
  "org.postgresql" % "postgresql" % "42.7.1"
)

// Options de compilation
scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked"
)

// Configuration pour l'assembly (création d'un JAR exécutable)
assembly / assemblyJarName := "datamart02-etl.jar"

// Stratégie de merge pour éviter les conflits lors de l'assembly
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

// Classe principale
Compile / mainClass := Some("DataMart02ETL")

// Désactiver les tests lors de l'assembly
assembly / test := {}
