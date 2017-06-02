node('agent') {
    checkout scm
    stage ('Build Grakn') {
        sh 'npm config set registry http://registry.npmjs.org/'
        sh 'rm -rf grakn/ && git clone https://github.com/graknlabs/grakn/'
        sh 'git checkout stable'
        sh 'cd grakn && mvn clean install -DskipTests -B -U -Djetty.log.level=WARNING -Djetty.log.appender=STDOUT'
    }
    stage ('Init Grakn') {
        sh 'tar -xf grakn/grakn-dist/target/grakn-dist*.tar.gz'
        sh 'cd grakn-dist* && bin/grakn.sh start'
    }
    stage('Scale Test') {
        sh 'cd single-machine-graph-scaling && mvn clean package'
	sh 'java -jar single-machine-graph-scaling/target/single-machine-graph-scaling-0.13.0-SNAPSHOT-allinone.jar'
    }
}
