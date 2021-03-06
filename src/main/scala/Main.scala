package main

import org.apache.spark.{SparkConf, SparkFiles}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.streaming.OutputMode

object Main {

  def main(args: Array[String]) {

    val conf = new SparkConf()
      .setAppName("Structured Streaming from Message Hub to Elasticsearch")

    val spark = SparkSession
      .builder()
      .config(conf)
      .getOrCreate()

    import spark.implicits._

    val sc = spark.sparkContext

    val schema = new StructType()
      .add("InvoiceNo", LongType)
      .add("StockCode", LongType)
      .add("Description", StringType)
      .add("Quantity", ShortType)
      .add("InvoiceDate", LongType)
      .add("UnitPrice", DoubleType)
      .add("CustomerID", IntegerType)
      .add("Country", StringType)
      .add("LineNo", ShortType)
      .add("InvoiceTime", StringType)
      .add("StoreID", ShortType)
      .add("TransactionID", StringType)

    val df = spark.readStream.
      format("kafka").
      option("kafka.bootstrap.servers", conf.get("spark.kafka_bootstrap_servers")).
      option("subscribe", "transactions_load").
      option("kafka.security.protocol", "SASL_SSL").
      option("kafka.sasl.mechanism", "PLAIN").
      option("kafka.ssl.protocol", "TLSv1.2").
      option("kafka.ssl.enabled.protocols", "TLSv1.2").
      load()

    val dataDf = df.selectExpr("CAST(value AS STRING) as json").
      select( from_json($"json", schema=schema).as("data")).
      select("data.*").
      filter($"InvoiceNo".isNotNull).
      withColumn("InvoiceDateString", from_unixtime($"InvoiceDate" / 1000)) //.

    dataDf   //Append output mode not supported when there are streaming aggregations on streaming DataFrames/DataSets without watermark
      .writeStream
      .outputMode(OutputMode.Append) //Only mode for ES
      .format("org.elasticsearch.spark.sql") //es
      .queryName("ElasticSink")
      .option("checkpointLocation", "checkpoint_elasticsearch")
      .option("es.nodes", conf.get("spark.es_nodes"))
      .option("es.port", conf.get("spark.es_port"))
      .option("es.net.http.auth.user", conf.get("spark.es_user"))
      .option("es.net.http.auth.pass", conf.get("spark.es_pass"))
      .option("es.net.ssl", true)
      .option("es.nodes.wan.only", true)
      .option("es.net.ssl.truststore.location", "my.jks")
      .option("es.net.ssl.truststore.pass", "changeit")
      .start("pos-transactions/logs") //ES index

    //Wait for all streams to finish
    spark.streams.awaitAnyTermination()
  }
}
