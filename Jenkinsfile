node('slave1-dev-jenkins') {
    stage ('root script') {
        sh 'echo "I am here"'
    }
    evaluate(new File("child"))
}
