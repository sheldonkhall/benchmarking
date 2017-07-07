#!/bin/bash

# check for errors from Grakn
FAILURES=$(curl http://$ENGINE/tasks?status=FAILED)
if [ "$FAILURES" == "[]" ]; then
        echo "Load completed without failures."
else
        echo "There were failures during loading."
        echo $FAILURES | jq -r '.[].id' | while read line ; do
                RESULT=$(curl http://$ENGINE/tasks/$line)
                echo $RESULT
        done
        exit 1
fi
