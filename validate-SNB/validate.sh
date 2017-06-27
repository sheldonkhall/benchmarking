#!/bin/bash

set -e

# execute validation
java -cp $LDBC_DRIVER:$LDBC_CONNECTOR com.ldbc.driver.Client -db ai.grakn.GraknDb -P $LDBC_VALIDATION_CONFIG -vdb $WORKSPACE/benchmarking/generate-SNB/$CSV_DATA/validation_params.csv -p ldbc.snb.interactive.parameters_dir $WORKSPACE/benchmarking/generate-SNB/$CSV_DATA -p ai.grakn.uri $ENGINE -p ai.grakn.keyspace $KEYSPACE

# check for errors from Grakn
FAILURES=$(curl http://$ENGINE/tasks?status=FAILED)
if [ "$FAILURES" == "[]" ]; then
        echo "Load completed without failures."
else
        echo "There were failures during loading."
        echo $FAILURES
        echo $FAILURES | jq -r '.[].id' | while read line ; do
                echo `curl http://$ENGINE/tasks/$line`
        done   
        exit 1
fi

# check for errors from LDBC
FAILURES=$(cat $WORKSPACE/benchmarking/generate-SNB/$CSV_DATA/validation_params-failed-actual.json)
if [ "$FAILURES" == "[ ]" ]; then
        echo "Validation completed without failures."
else
        echo "There were failures during validation."
        echo $FAILURES
        exit 1
fi
