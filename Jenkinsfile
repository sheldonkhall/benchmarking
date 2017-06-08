node('slave1-dev-jenkins') {
    triggers { cron('H/2 * * * *') }
    stage ('root script') {
        sh 'echo "I am here"'
    }
}
