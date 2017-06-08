node('slave1-dev-jenkins') {
properties(
    [
        pipelineTriggers([cron('H/2 * * * *')])
    ]
)
    stage ('root script') {
        sh 'echo "I am here"'
    }
}
