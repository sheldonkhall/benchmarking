#!/bin/bash

FAILURES=$(curl http://$ENGINE/tasks?status=FAILED)
if [ "$FAILURES" == "[]" ]; then
	echo "There were failures during loading."
        echo $FAILURES
        echo $FAILURES | jq -r '.[].id' | while read line ; do
                echo `curl http://$ENGINE/tasks/$line`
        done
fi
