#!/bin/bash

VALIDATION_DATA="/opt/grakn/data/readwrite_neo4j--validation_set.tar.gz"
SF1_DATA="/opt/grakn/data/snb-data-sf1.tar.gz"
CSV_DATA="social_network"

# force script to exit on failed command
set -e

# validate the number of arguments
if [ "$#" -lt "2" ]; then
	echo "Wrong number of arguments." >&2
	exit 1
fi

# load the data from a tar
function loadArchData {
	case "$1" in
		mkdir -p $CSV_DATA
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
		loadArchData $2
		;;
	*)
		echo "Usage: $0 {gen|arch}"
		exit 1
		;;
esac

