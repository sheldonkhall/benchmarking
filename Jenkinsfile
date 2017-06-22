#!groovy
properties([pipelineTriggers([cron('H H/6 * * *')])])
def buildOnBranch = { String buildBranchVar ->
    def workspace = pwd()
    // this has to be done due to some concurrency issue
    def buildBranch = buildBranchVar
    try {

        dir('grakn') {
            git url: 'https://github.com/graknlabs/grakn', branch: buildBranch
            stage(buildBranch+' Build Grakn') {
                sh 'npm config set registry http://registry.npmjs.org/'
                sh 'mvn clean install -DskipTests -B -U -Djetty.log.level=WARNING -Djetty.log.appender=STDOUT'
            }
            stage(buildBranch+' Init Grakn') {
                sh 'mkdir grakn-package'
                sh 'tar -xf grakn-dist/target/grakn-dist*.tar.gz --strip=1 -C grakn-package'
                sh 'grakn-package/bin/grakn.sh start'
                //todo: remove after bugfix
                sh 'sleep 10'
            }
            stage(buildBranch+' Test Connection') {
                sh 'grakn-package/bin/graql.sh -e "match \\\$x;"'
            }
        }
        dir('benchmarking') {
            checkout scm

            dir('single-machine-graph-scaling') {
                stage(buildBranch+' Scale Test') {
                    sh 'mvn clean -U package'
                    sh 'java -jar target/single-machine-graph-scaling-0.14.0-SNAPSHOT-allinone.jar'
                }
            }

            dir('impls-SNB') {
                stage(buildBranch+' Build LDBC Connector') {
                    sh 'mvn -U clean install assembly:single'
                }
            }

            withEnv(['VALIDATION_DATA=/home/jenkins/readwrite_neo4j--validation_set.tar.gz',
                     'CSV_DATA=social_network',
                     'KEYSPACE=snb',
                     'ENGINE=localhost:4567',
                     'ACTIVE_TASKS=1000',
                     'PATH+EXTRA=' + workspace + '/grakn/grakn-package/bin',
                     'LDBC_DRIVER=/home/jenkins/ldbc_driver/target/jeeves-0.3-SNAPSHOT.jar',
                     'LDBC_CONNECTOR=' + workspace + '/benchmarking/impls-SNB/target/snb-interactive-grakn-0.0.1-jar-with-dependencies.jar',
                     'LDBC_VALIDATION_CONFIG=readwrite_grakn--ldbc_driver_config--db_validation.properties']) {
                dir('generate-SNB') {
                    stage(buildBranch+' Load Validation Data') {
                        sh './load-SNB.sh arch validate'
                    }
                }
                stage(buildBranch+' Measure Size') {
                    sh '../grakn/grakn-package/bin/nodetool flush'
                    sh 'du -hd 0 ../grakn/grakn-package/db/cassandra/data'
                }
                dir('validate-SNB') {
                    stage(buildBranch+' Validate Graph') {
                        sh './validate.sh'
                    }
                }
            }
        }
	slackSend channel: "#github", message: "Periodic Build Success on stable: ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
    } catch (error) {
	slackSend channel: "#github", message: "@here Periodic Build Failed on stable: ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
	throw error
    } finally {

        withEnv(['ENGINE=localhost:4567']) {
		dir('benchmarking/tools') {
			sh './check-errors.sh'
		}
	}

        dir('grakn') {
            archiveArtifacts artifacts: 'grakn-package/logs/grakn.log'
            stage(buildBranch+' Tear Down Grakn') {
                sh 'grakn-package/bin/grakn.sh stop'
                sh 'rm -rf grakn-package'
            }
        }

    }
}

parallel 'master':{node('slave3'){buildOnBranch('master')}}, 'stable':{node('slave1'){buildOnBranch('stable')}}, failFast: false
