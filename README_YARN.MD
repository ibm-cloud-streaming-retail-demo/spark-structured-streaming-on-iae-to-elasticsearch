Email support@compose.com and request Kibana to be enabled

Use Developer Tools in Kibana to add a mapping:

```
PUT _template/pos-transactions
{
  "index_patterns": "pos-transactions*",
  "mappings": {
    "logs" : {
        "dynamic": "strict",
        "properties" : {
            "InvoiceDateString": {
              "type": "date",
              "format": "yyyy-MM-dd HH:mm:ss"
            },
            "Description":   { "type": "text" },
            "InvoiceNo":     { "type": "long" },
            "CustomerID":    { "type": "long" },
            "TransactionID": { "type": "long" },
            "Quantity":      { "type": "long" },
            "UnitPrice":     { "type": "double" },
            "InvoiceTime":   { "type": "text" },
            "StoreID":       { "type": "long" },
            "Country":       { "type": "text" },
            "InvoiceDate":   { "type": "long" },
            "StockCode":     { "type": "text" },
            "LineNo":        { "type": "long" }
            
        }
    }
  }
}
```

Build the scala class and package to jar and copy it to IAE!

```
sbt package
scp target/scala-2.11/spark-structured-streaming-on-iae-to-cos_2.11-1.0.jar clsadmin@yourcluster:./
```

Create a truststore on IAE for Elasticsearch certificate

```
keytool --importkeystore -noprompt \
        -srckeystore /etc/pki/java/cacerts \
        -srcstorepass changeit \
        -destkeystore my.jks \
        -deststorepass changeit
        
wget https://letsencrypt.org/certs/lets-encrypt-x3-cross-signed.der  

keytool -trustcacerts \
    -keystore my.jks \
    -storepass changeit \
    -noprompt -importcert \
    -alias letsencryptauthorityx3 \
    -file lets-encrypt-x3-cross-signed.der  

```

Create a jaas.conf for spark to authenticate to Message Hub (paste this script into your IAE SSH session and edit the file to chnage the CHANGEME values):

```
cat << EOF > jaas.conf
KafkaClient {
    org.apache.kafka.common.security.plain.PlainLoginModule required
    serviceName="kafka"
    username="CHANGEME"
    password="CHANGEME";
};
EOF
```

Create a script with some variables needed by spark (paste this script into your IAE SSH session and edit the file to chnage the CHANGEME values):

```
cat << EOF > config_es_kafka_vars.sh

# ELASTICSEARCH #

ES_USER=CHANGEME
ES_PASS=CHANGEME
ES_NODES=HOST1,HOST2, ... # CHANGEME
ES_PORT=CHANGEME

# KAFKA #
KAFKA_BOOTSTRAP_SERVERS=HOST1:PORT1,HOST2:PORT2,... # E.g. kafka03-prod01.messagehub.services.eu-de.bluemix.net:9093,kafka04-prod01.messagehub.services.eu-de.bluemix.net:9093,kafka01-prod01.messagehub.services.eu-de.bluemix.net:9093,kafka02-prod01.messagehub.services.eu-de.bluemix.net:9093,kafka05-prod01.messagehub.services.eu-de.bluemix.net:9093

EOF
```

Create a script to start spark interactively via yarn (paste this script into your IAE SSH session):

```
cat << 'EOF' > start_yarn_client_elasticsearch.sh
source config_es_kafka_vars.sh

spark-submit --class main.Main \
       --master yarn \
       --deploy-mode client \
       --files jaas.conf \
       --packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.3.0 \
       --conf "spark.driver.extraJavaOptions=-Djava.security.auth.login.config=jaas.conf" \
       --conf "spark.executor.extraJavaOptions=-Djava.security.auth.login.config=jaas.conf" \
       --conf spark.kafka_bootstrap_servers=$KAFKA_BOOTSTRAP_SERVERS \
       --conf spark.es_user=$ES_USER \
       --conf spark.es_pass=$ES_PASS \
       --conf spark.es_nodes=$ES_NODES \
       --conf spark.es_port=$ES_PORT \
       --num-executors 1 \
       --executor-cores 1 \
       spark-structured-streaming-on-iae-to-elasticsearch_2.11-1.0.jar
EOF
chmod +x start_yarn_client_elasticsearch.sh
```

Run the interactive script, you should see spark saving to COS and output sent to the terminal:

```
bash -x start_yarn_client_elasticsearch.sh
```

Create a script to start spark in the background via yarn (paste this script into your IAE SSH session):

```
cat << 'EOF' > start_yarn_cluster_elasticsearch.sh
source config_es_kafka_vars.sh

spark-submit --class main.Main \
       --master yarn \
       --deploy-mode cluster \
       --files jaas.conf \
       --packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.3.0 \
       --conf "spark.driver.extraJavaOptions=-Djava.security.auth.login.config=jaas.conf" \
       --conf "spark.executor.extraJavaOptions=-Djava.security.auth.login.config=jaas.conf" \
       --conf spark.kafka_bootstrap_servers=$KAFKA_BOOTSTRAP_SERVERS \
       --conf spark.es_user=$ES_USER \
       --conf spark.es_pass=$ES_PASS \
       --conf spark.es_nodes=$ES_NODES \
       --conf spark.es_port=$ES_PORT \
       --conf spark.yarn.submit.waitAppCompletion=false \
       --num-executors 1 \
       --executor-cores 1 \
       spark-structured-streaming-on-iae-to-elasticsearch_2.11-1.0.jar
EOF
chmod +x start_yarn_cluster_elasticsearch.sh
```

Run the script. It will terminate after submitting the job to yarn.

```
bash -x start_yarn_cluster_elasticsearch.sh
```

Verify that the spark job is running on yarn:

```
yarn application -list
```
