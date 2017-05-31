#!/bin/bash

./runGraknMigration.sh $1 snb 1000 100
nodetool flush
du -d /opt/grakn/$2/db
