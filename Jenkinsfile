node('agent') {
    checkout scm
    try {
    stage ('Build Grakn') {
        sh 'ls -la'
        sh 'npm config set registry http://registry.npmjs.org/'
        sh 'rm -rf grakn && git clone https://github.com/graknlabs/grakn/'
        sh 'cd grakn && git checkout stable'
        sh 'cd grakn && mvn clean -U install -DskipTests -B -U -Djetty.log.level=WARNING -Djetty.log.appender=STDOUT'
    }
    stage ('Init Grakn') {
        sh 'tar -xf grakn/grakn-dist/target/grakn-dist*.tar.gz'
        sh 'cd grakn-dist* && bin/grakn.sh start'
    }
    stage('Scale Test') {
        sh 'cd single-machine-graph-scaling && mvn clean -U package'
	sh 'java -jar single-machine-graph-scaling/target/single-machine-graph-scaling-0.14.0-SNAPSHOT-allinone.jar'
    }
    } finally {
    stage('Tear Down') {
            sh 'cd grakn-dist* && bin/grakn.sh stop'
	    sh 'rm -rf grakn'
            sh 'rm -rf grakn-dist*'
    }
    }
}
