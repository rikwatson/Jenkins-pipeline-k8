def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    node ("${pipelineParams.agentLabel}") {
        // Clean workspace before doing anything
        deleteDir()

        try {
            stage ('Clone') {
                checkout scm
            }
            
            stage ('Prepare and deploy') {

                def configMapFile = "configMap/${pipelineParams.envStage}/${pipelineParams.appName}-config.yaml"
                def configMapData = readYaml file: configMapFile
                def longsha1ConfigMap = sha1 file: configMapFile
                def sha1ConfigMap = longsha1ConfigMap.substring(0,10)
                def newConfigMapName = "${pipelineParams.appName}-config-${sha1ConfigMap}"
                configMapData.metadata.name = "${newConfigMapName}"
                def newConfigMapFile = "configMap/${pipelineParams.envStage}/${pipelineParams.appName}-config-${sha1ConfigMap}.yaml"

                writeYaml file: newConfigMapFile, data: configMapData

                sh("cat ${newConfigMapFile}")
            
                sh("cp deployments/${pipelineParams.envStage}/deployment.tmpl.yaml deployments/${pipelineParams.envStage}/deployment.yaml " )
                sh("sed -i.bak 's#APP_NAME#${pipelineParams.appName}#' deployments/${pipelineParams.envStage}/deployment.yaml")
                sh("sed -i.bak 's#VERSION#${pipelineParams.gitHash}#' deployments/${pipelineParams.envStage}/deployment.yaml")
                sh("sed -i.bak 's#NO_OF_PODS#${pipelineParams.noOfPods}#' deployments/${pipelineParams.envStage}/deployment.yaml")
                sh("sed -i.bak 's#IMAGE_NAME#${pipelineParams.imageName}#' deployments/${pipelineParams.envStage}/deployment.yaml")
                sh("sed -i.bak 's#IMAGE_TAG#${pipelineParams.imageTag}#' deployments/${pipelineParams.envStage}/deployment.yaml")
                sh("sed -i.bak 's#NAMESPACE#${pipelineParams.namespace}#' deployments/${pipelineParams.envStage}/deployment.yaml")
                sh("sed -i.bak 's#APP_RUN_ENV#${pipelineParams.envStage}#' deployments/${pipelineParams.envStage}/deployment.yaml")
                sh("sed -i.bak 's#CONFIGNAMEHASH#${newConfigMapName}#' deployments/${pipelineParams.envStage}/deployment.yaml")
                sh("cat deployments/${pipelineParams.envStage}/deployment.yaml")
            
                withCredentials([file(credentialsId: "${pipelineParams.kubeConfigCredId}", variable: 'KUBEFILE')]) {
                    sh "${pipelineParams.kubectlPath} --kubeconfig='$KUBEFILE' apply -f ${newConfigMapFile} --alsologtostderr=true"
                    sh "${pipelineParams.kubectlPath} --kubeconfig='$KUBEFILE' apply -f deployments/${pipelineParams.envStage}/deployment.yaml --alsologtostderr=true"
                }
            }

            //Wait for a while for pods to spin up
            stage('Wait for deployment to finish') {
                sh "echo varifying deployment on '${pipelineParams.env_stage}' cluster, image ${pipelineParams.image_name}:${pipelineParams.image_tag}"
                echo "Delaying for ${pipelineParams.wait_before_validate} secs..."
                if (pipelineParams.wait_before_validate.isInteger()) {
                    int wait_before_validate = pipelineParams.wait_before_validate as Integer
                    sleep(wait_before_validate)
                }
            }

            stage ('Verify deployment ') {
                expectedStatus = "deployment \"${pipelineParams.app_name}-deployment-${pipelineParams.git_hash}\" successfully rolled out"
                withCredentials([file(credentialsId: "${pipelineParams.kubeConfigCredId}", variable: 'KUBEFILE')]) {
                    sh "${pipelineParams.kubectlPath} --kubeconfig='$KUBEFILE' get deployment ${pipelineParams.app_name}-deployment-${pipelineParams.git_hash}"
                    def rolloutStatus = sh (
                            script: "${pipelineParams.kubectlPath} --kubeconfig='$KUBEFILE' rollout status deployment ${pipelineParams.app_name}-deployment-${pipelineParams.git_hash}",
                            returnStdout: true
                        ).trim()
                        if (expectedStatus == rolloutStatus) {
                            sh "echo depolyment successfully rolled out."
                        }
                        else {
                            print rolloutStatus
                            currentBuild.result = 'FAILURE'
                            return
                        }
                    sh "${pipelineParams.kubectlPath} --kubeconfig='$KUBEFILE' get deployment ${pipelineParams.app_name}-deployment-${pipelineParams.git_hash} > deploymentStatus"

                    def desiredPodStatus = sh (
                        script: "grep ${pipelineParams.app_name}-deployment-${pipelineParams.git_hash} deploymentStatus | awk '{print \$2}'",
                        returnStdout: true
                    ).trim()
                    def currentPodStatus = sh (
                        script: "grep ${pipelineParams.app_name}-deployment-${pipelineParams.git_hash} deploymentStatus | awk '{print \$3}'",
                        returnStdout: true
                    ).trim()
                    def uptodatePodStatus = sh (
                        script: "grep ${pipelineParams.app_name}-deployment-${pipelineParams.git_hash} deploymentStatus | awk '{print \$4}'",
                        returnStdout: true
                    ).trim()
                    def availablePodStatus = sh (
                        script: "grep ${pipelineParams.app_name}-deployment-${pipelineParams.git_hash} deploymentStatus | awk '{print \$5}'",
                        returnStdout: true
                    ).trim()

                    if (desiredPodStatus == currentPodStatus == uptodatePodStatus == availablePodStatus) {
                        sh "echo All Pods successfully deployed!"
                    }
                    else {
                        print availablePodStatus
                        currentBuild.result = 'FAILURE'
                        return
                    }
                }
            }
        } catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        }
    }
}
