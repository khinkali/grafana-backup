podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'docker', image: 'docker', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.0', command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'curl', image: 'khinkali/jenkinstemplate:0.0.3', command: 'cat', ttyEnabled: true),
        containerTemplate(name: 'maven', image: 'maven:3.5.2-jdk-8', command: 'cat', ttyEnabled: true)
],
        volumes: [
                hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock'),
        ]) {
    node('mypod') {
        properties([
                buildDiscarder(
                        logRotator(artifactDaysToKeepStr: '',
                                artifactNumToKeepStr: '',
                                daysToKeepStr: '',
                                numToKeepStr: '30'
                        )
                ),
                pipelineTriggers([cron('30 0 * * *')])
        ])

        stage('create backup') {
            def kc = 'kubectl -n test'
            def containerPath = '/var/lib/grafana'
            def containerName = 'grafana'
            def podLabel = 'app=grafana'
            container('kubectl') {
                createBackup(podLabel, containerName, containerPath, kc)
            }
        }

    }
}

void createBackup(String podLabel,
                  String containerName,
                  String containerPath,
                  String kc = 'kubectl',
                  String gitEmail = 'jenkins@khinkali.ch',
                  String gitName = 'Jenkins',
                  String commitMessage = 'new_version',
                  String repositoryCredentials = 'bitbucket',
                  String repositoryUrl = 'bitbucket.org/khinkali/grafana_backup') {
    def jenkinsPods = sh(
            script: "${kc} get po -l ${podLabel} --no-headers",
            returnStdout: true
    ).trim()
    def podNameLine = jenkinsPods.split('\n')[0]
    def startIndex = podNameLine.indexOf(' ')
    if (startIndex == -1) {
        return
    }
    def podName = podNameLine.substring(0, startIndex)
    sh "${kc} exec ${podName} -c ${containerName} -- git -C '${containerPath}' config user.email \"${gitEmail}\""
    sh "${kc} exec ${podName} -c ${containerName} -- git -C '${containerPath}' config user.name \"${gitName}\""
    sh "${kc} exec ${podName} -c ${containerName} -- git -C '${containerPath}' add --all"
    sh "${kc} exec ${podName} -c ${containerName} -- git -C '${containerPath}' diff --quiet && ${kc} exec ${podName} -c ${containerName} -- git -C '${containerPath}' diff --staged --quiet || ${kc} exec ${podName} -c ${containerName} -- git -C '${containerPath}' commit -am '${commitMessage}'"
    withCredentials([usernamePassword(credentialsId: repositoryCredentials, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        sh "${kc} exec ${podName} -c ${containerName} -- git -C '${containerPath}' push https://${GIT_USERNAME}:${GIT_PASSWORD}@${repositoryUrl}"
    }
}

