node('slave1-dev-jenkins') {
properties(
    [
        pipelineTriggers([cron('H/30 * * * *')])
    ]
)
    stage ('root script') {
        sh 'echo "I am here"'
    }
}
