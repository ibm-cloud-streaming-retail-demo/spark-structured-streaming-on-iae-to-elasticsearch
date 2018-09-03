resolvers += "Spark Packages Repo" at "https://dl.bintray.com/spark-packages/maven"

name := "spark-structured-streaming-on-iae-to-cos"

version := "1.0"
scalaVersion := "2.11.12"
val sparkVersion = "2.3.0"

libraryDependencies += "log4j" % "log4j" % "1.2.14"

libraryDependencies += "org.apache.spark" %% "spark-core" % sparkVersion
libraryDependencies += "org.apache.spark" %% "spark-sql" % sparkVersion
libraryDependencies += "org.apache.spark" % "spark-sql-kafka-0-10_2.11" % sparkVersion

fork in run := true


libraryDependencies += "org.elasticsearch" % "elasticsearch-hadoop" % "6.3.0"
