#!/bin/bash

VALIDATION_DATA="/opt/grakn/data/readwrite_neo4j--validation_set.tar.gz"
SF1_DATA="/opt/grakn/data/snb-data-sf1.tar.gz"
CSV_DATA="social_network"
KEYSPACE="snb"
ENGINE="localhost:4567"
ACTIVE_TASKS="1000"
LDBC_JAR="/Users/sheldon/Repos/ldbc-snb/target/ldbc_snb_datagen-0.2.5-jar-with-dependencies.jar"
HADOOP_HOME="/Users/sheldon/Repos/ldbc-snb/hadoop-2.6.0"

export HADOOP_HOME
