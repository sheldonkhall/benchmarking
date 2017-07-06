#!/bin/bash

export VALIDATION_DATA="/opt/grakn/data/readwrite_neo4j--validation_set.tar.gz"
export SF1_DATA="/opt/grakn/data/snb-data-sf1.tar.gz"
export CSV_DATA="/tmp/social-network"
export KEYSPACE="snb"
export ENGINE="localhost:4567"
export ACTIVE_TASKS="1000"
export HADOOP_HOME="/Users/sheldon/Repos/ldbc-snb/hadoop-2.6.0"
export LDBC_DRIVER="/Users/sheldon/Repos/ldbc_driver/target/jeeves-0.3-SNAPSHOT.jar"
export LDBC_CONNECTOR="/Users/sheldon/Repos/benchmarking/impls-SNB/target/snb-interactive-grakn-0.0.1-jar-with-dependencies.jar"
export LDBC_VALIDATION_CONFIG=readwrite_grakn--ldbc_driver_config--db_validation.properties
