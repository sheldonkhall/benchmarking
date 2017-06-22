#!/bin/bash

FAILURES=$(curl http://$ENGINE/tasks?status=FAILED)
if [ "$FAILURES" == "[]" ]; then
	echo "There were failures during loading."
        echo $FAILURES
        jq -r '.[].id' $FAILURES | while read line ; do
                echo `curl http://$ENGINE/tasks/$line`
        done
fi
