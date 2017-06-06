#!/bin/bash

set -e

# execute validation
java -cp $LDBC_DRIVER:$WORKSPACE/$LDBC_CONNECTOR com.ldbc.driver.Client -db ai.grakn.GraknDb -P $LDBC_VALIDATION_CONFIG -vdb $WORKSPACE/generate-SNB/$CSV_DATA/validation_params.csv -rd $WORKSPACE/results -vp $WORKSPACE/generate-SNB/$CSV_DATA

# check for errors from Grakn
FAILURES=$(curl http://$ENGINE/tasks?status=FAILED)
if [ "$FAILURES" == "[]" ]; then
        echo "Load completed without failures."
else
        echo "There were failures during loading."
        echo $FAILURES
        exit 1
fi

# check for errors from LDBC
FAILURES=$(cat results/validation_params-failed-actual.json)
if [ "$FAILURES" == "[ ]" ]; then
        echo "Validation completed without failures."
else
        echo "There were failures during validation."
        echo $FAILURES
        exit 1
fi
