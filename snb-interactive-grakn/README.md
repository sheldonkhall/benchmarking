Grakn LDBC SNB Interactive Workload Implementation
==================================================

# Prerequisites

You need to have Grakn running on your machine and also installed using maven.

# Generating the validation data

In order to generate the validation data clone these two git repos:

`git@github.com:ldbc/ldbc_snb_interactive_validation.git`

`git@github.com:graknlabs/ldbc-snb.git`

The validation repo contains the pre-generated csv files used for validation.
The Grakn ldbc repo contains the code for loading the graph.
Unpack the archive `readwrite_neo4j--validation_set.tar.gz` from the validation repo and record the path `$VALIDATION`.
Then the raw data needs to be copied into the Grakn ldbc folder.

`cp -r $VALIDATION/* $GRAKN_LDBC/social_network/`

The following command should be removed from the `runGraknMigrator.sh` script:

`run.sh`

Then the script can be executed with a running instance of the Grakn distribution.

`./runGraknMigrator.sh localhost:4567 snb`

# Running the data validation

In order to confirm that the queries are performing as expected the ldbc provide a validation driver.
This should be run before the benchmark to ensure everything is running as expected.
In order to run the validation you need this additional git repo:

`git@github.com:ldbc/ldbc_driver.git`

This repo contains the driver for executing the queries. Before proceeding this driver should be installed using:

`mvn install -DskipTests`

The implementations of the driver interfaces are stored in this repository:

`git@github.com:mikonapoli/ldbc-snb-impls.git`

in the `short-query-handlers` branch.
These implementations need to be compiled using

`mvn clean compile assembly:single`

There are some configuration options that need to be set in order to run the benchmark.
These are contained in the file `$LDBC_SNB_INTERACTIVE_VALIDATION/neo4j/readwrite_neo4j--ldbc_driver_config--db_validation.properties`.
The two properties that should be set are:

`validate_database=$VALIDATION/validation_set/validation_params.csv`

`ldbc.snb.interactive.parameters_dir=$VALIDATION/validation_set`

Last of all execute the validation

`java -cp $LDBC_DRIVER/target/jeeves-0.3-SNAPSHOT.jar:$LDBC_SNB_IMPLS/snb-interactive-grakn/target/snb-interactive-grakn-0.0.1-jar-with-dependencies.jar com.ldbc.driver.Client -db net.ellitron.ldbcsnbimpls.interactive.grakn.GraknDb -P $LDBC_SNB_INTERACTIVE_VALIDATION/neo4j/readwrite_neo4j--ldbc_driver_config--db_validation.properties`

# Important Notes

Don't forget that some of these queries mutate the graph and so it must be reloaded/restored to the original state before running again.