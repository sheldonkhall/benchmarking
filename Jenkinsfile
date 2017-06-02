node('agent') {
    checkout scm
    stage ('Build Grakn') {
        sh 'npm config set registry http://registry.npmjs.org/'
        sh 'rm -rf grakn/ && git clone https://github.com/graknlabs/grakn/'
        sh 'cd grakn && mvn clean package -DskipTests -B -U -Djetty.log.level=WARNING -Djetty.log.appender=STDOUT'
    }
    stage ('Init Grakn') {
        sh 'tar -xf grakn/grakn-dist/target/grakn-dist*.tar.gz'
        sh 'cd grakn-dist* && bin/grakn.sh start'
    }
    stage('Build') {
        sh 'mvn --version'
    }
}
