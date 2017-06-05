node('agent') {
    checkout scm
    try {
    stage ('Build Grakn') {
        //sh 'npm config set registry http://registry.npmjs.org/'
        //sh 'rm -rf grakn && git clone https://github.com/graknlabs/grakn/'
        //sh 'cd grakn && git checkout stable'
        //sh 'cd grakn && mvn clean -U install -DskipTests -B -U -Djetty.log.level=WARNING -Djetty.log.appender=STDOUT'
    }
    stage ('Init Grakn') {
        //sh 'tar -xf grakn/grakn-dist/target/grakn-dist*.tar.gz'
        //sh 'cd grakn-dist* && bin/grakn.sh start'
    }
    stage('Scale Test') {
        //sh 'cd single-machine-graph-scaling && mvn clean -U package'
	//sh 'java -jar single-machine-graph-scaling/target/single-machine-graph-scaling-0.14.0-SNAPSHOT-allinone.jar'
    }
    stage('Load Validation Data') {
        withEnv(['VALIDATION_DATA=~/readwrite_neo4j--validation_set.tar.gz',
                 'SF1_DATA=~/snb-data-sf1.tar.gz',
                 'CSV_DATA=social_network',
                 'KEYSPACE=snb',
                 'ENGINE=localhost:4567',
                 'ACTIVE_TASKS=1000',
                 'LDBC_JAR=~/ldbc-snb/target/ldbc_snb_datagen-0.2.5-jar-with-dependencies.jar'
                 'HADOOP_HOME=~/hadoop-2.6.0']) {
            sh 'cd generate-SNB && ./load-SN.sh arch validate
        }
    }
    } finally {
    stage('Tear Down') {
            //sh 'cd grakn-dist* && bin/grakn.sh stop'
	    //sh 'rm -rf grakn'
            //sh 'rm -rf grakn-dist*'
    }
    }
}
