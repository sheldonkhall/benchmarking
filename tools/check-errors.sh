#!/bin/bash

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
                if [ `echo $RESULT | jq -r '.className | contains("ai.grakn.engine.postprocessing.PostProcessingTask") | not'` ] ; then SHOULDEXIT=True; fi
        done
        if [ $SHOULDEXIT ] ; then exit 1 ; fi
fi
