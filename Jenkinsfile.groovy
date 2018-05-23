@Library('semantic_releasing') _

podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.0', command: 'cat', ttyEnabled: true)
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
            def repositoryUrl = 'bitbucket.org/khinkali/grafana_backup'
            container('kubectl') {
                backup(podLabel, containerName, containerPath, repositoryUrl, kc)
            }
        }

    }
}

