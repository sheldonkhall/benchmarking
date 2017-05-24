#!/bin/bash

VALIDATION_DATA="/opt/grakn/data/readwrite_neo4j--validation_set.tar.gz"
SF1_DATA="/opt/grakn/data/snb-data-sf1.tar.gz"
CSV_DATA="social_network"
KEYSPACE="snb"
ENGINE="localhost:4567"
ACTIVE_TASKS="1000"

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

# switch between generating data or using archive data
case "$1" in
	gen)
		echo "Option gen not yet supported."
		exit 0
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

# load ontology
graql.sh -k $KEYSPACE -f $GRAQL/ldbc-snb-1-resources.gql -r $ENGINE
graql.sh -k $KEYSPACE -f $GRAQL/ldbc-snb-2-relations.gql -r $ENGINE
graql.sh -k $KEYSPACE -f $GRAQL/ldbc-snb-3-entities.gql -r $ENGINE
graql.sh -k $KEYSPACE -f $GRAQL/ldbc-snb-4-rules.gql -r $ENGINE

sed -i '' "1s/Comment.id|Comment.id/Comment.id|Message.id/" $DATA/comment_replyOf_comment_0_0.csv
sed -i '' "1s/Person.id|Person.id/Person1.id|Person.id/" $DATA/person_knows_person_0_0.csv
sed -i '' "1s/Place.id|Place.id/Place1.id|Place.id/" $DATA/place_isPartOf_place_0_0.csv
sed -i '' "1s/TagClass.id|TagClass.id/TagClass1.id|TagClass.id/" $DATA/tagclass_isSubclassOf_tagclass_0_0.csv

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
