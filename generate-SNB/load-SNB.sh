#!/bin/bash

# set script directory as working directory
SCRIPTPATH=`cd "$(dirname "$0")" && pwd -P`
DATA=$SCRIPTPATH/./social_network
GRAQL=$SCRIPTPATH/./graql

# force script to exit on failed command
set -e

# validate the number of arguments
if [ "$#" -lt "2" ]; then
	echo "Wrong number of arguments." >&2
	exit 1
fi

# extract the data from a tar
function extractArchData {
	mkdir -p $CSV_DATA
	case "$1" in
		validate)
			tar -xf $VALIDATION_DATA --strip=1 -C $CSV_DATA validation_set
			;;
		SF1)
			tar -xf $SF1_DATA
			;;
		*)
			echo "Usage: arch {SF1}"
			exit 1
			;;
	esac
}

# generate new data
function generateData {
	LDBC_SNB_DATAGEN_HOME=${LDBC_SNB_DATAGEN_HOME:-$DEFAULT_LDBC_SNB_DATAGEN_HOME}

	paramFile=tmpParams.ini
	cat params.ini > $paramFile

	case "$1" in
		SF*)

			echo "ldbc.snb.datagen.generator.scaleFactor:snb.interactive.${1:2:4}" >> $paramFile
			;;
		P*)
			echo "ldbc.snb.datagen.generator.numPersons:${1:1:6}" >> $paramFile
			;;
		*)
			echo "Usage: gen {SF*|P*}"
			exit 1
			;;
	esac

	export HADOOP_CLIENT_OPTS="-Xmx1024m"
	$HADOOP_HOME/bin/hadoop jar $LDBC_JAR $SCRIPTPATH/$paramFile

	rm $paramFile
	rm -f m*personFactors*
	rm -f .m*personFactors*
	rm -f m*activityFactors*
	rm -f .m*activityFactors*
	rm -f m0friendList*
	rm -f .m0friendList*
}

# switch between generating data or using archive data
case "$1" in
	gen)
		generateData $2
		;;
	arch)
		extractArchData $2
		;;
	*)
		echo "Usage: $0 {gen|arch}"
		exit 1
		;;
esac

# migrate the data into Grakn

graql.sh -k $KEYSPACE -f $GRAQL/ldbc-snb-1-resources.gql -r $ENGINE
graql.sh -k $KEYSPACE -f $GRAQL/ldbc-snb-2-relations.gql -r $ENGINE
graql.sh -k $KEYSPACE -f $GRAQL/ldbc-snb-3-entities.gql -r $ENGINE
graql.sh -k $KEYSPACE -f $GRAQL/ldbc-snb-4-rules.gql -r $ENGINE

# lazily take account of OS
unamestr=`uname`
if [[ "$unamestr" == 'Linux' ]]; then
    sed -i "1s/Comment.id|Comment.id/Comment.id|Message.id/" $DATA/comment_replyOf_comment_0_0.csv
    sed -i "1s/Person.id|Person.id/Person1.id|Person.id/" $DATA/person_knows_person_0_0.csv
    sed -i "1s/Place.id|Place.id/Place1.id|Place.id/" $DATA/place_isPartOf_place_0_0.csv
    sed -i "1s/TagClass.id|TagClass.id/TagClass1.id|TagClass.id/" $DATA/tagclass_isSubclassOf_tagclass_0_0.csv
elif [[ "$unamestr" == 'Darwin' ]]; then
    sed -i '' "1s/Comment.id|Comment.id/Comment.id|Message.id/" $DATA/comment_replyOf_comment_0_0.csv
    sed -i '' "1s/Person.id|Person.id/Person1.id|Person.id/" $DATA/person_knows_person_0_0.csv
    sed -i '' "1s/Place.id|Place.id/Place1.id|Place.id/" $DATA/place_isPartOf_place_0_0.csv
    sed -i '' "1s/TagClass.id|TagClass.id/TagClass1.id|TagClass.id/" $DATA/tagclass_isSubclassOf_tagclass_0_0.csv
fi

while read p;
do
        DATA_FILE=$(echo $p | awk '{print $2}')
        TEMPLATE_FILE=$(echo $p | awk '{print $1}')

        NUM_SPLIT=$(head -1 ${DATA}/${DATA_FILE} | tr -cd \| | wc -c)
        BATCH_SIZE=$(awk "BEGIN {print int(1000/${NUM_SPLIT})}")

        echo "Dynamic batch size: $BATCH_SIZE"

        tail -n +2 $DATA/${DATA_FILE} | wc -l
        time migration.sh csv -s \| -t $GRAQL/${TEMPLATE_FILE} -i $DATA/${DATA_FILE} -k $KEYSPACE -u $ENGINE -a ${ACTIVE_TASKS:-25} -b ${BATCH_SIZE}
done < migrationsToRun.txt

# confirm there were no errors

FAILURES=$(curl http://$ENGINE/tasks?status=FAILED)
if [ "$FAILURES" == "[]" ]; then
	echo "Load completed without failures."
else
	echo "There were failures during loading."
	echo $FAILURES
	echo $FAILURES | jq -r '.[].id' | while read line ; do
		RESULT=$(curl http://$ENGINE/tasks/$line)
		echo $RESULT
		if [ `echo $RESULT | jq -r '.className | contains("ai.grakn.engine.postprocessing.PostProcessingTask") | not'` ] ; then SHOULDEXIT=True; fi
	done
	if [ $SHOULDEXIT ] ; then exit 1 ; fi
fi
