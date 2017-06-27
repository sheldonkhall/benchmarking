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
        # until we have released the last version with postprocessing use a modified line to ignore
	echo $FAILURES | jq -r '.[].id' | while read line ; do
                RESULT=$(curl http://$ENGINE/tasks/$line)
		echo $RESULT
		if [ `echo $RESULT | jq -r '.[].className | contains("ai.grakn.engine.postprocessing.PostProcessingTask") | not'` ] ; then SHOULDEXIT=True; fi
        done   
        if [ $SHOULDEXIT ] ; then exit 1 ; fi
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
