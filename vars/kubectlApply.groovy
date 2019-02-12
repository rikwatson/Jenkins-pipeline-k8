def call(body) {
    // evaluate the body block, and collect configuration into the object
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()


    node ('k8-deploy') {
        try {
            stage ('Run kubectl apply command') {

                withCredentials([file(credentialsId: "${pipelineParams.kubeConfigCredId}", variable: 'KUBEFILE')]) {
                    sh "kubectl --kubeconfig='$KUBEFILE' apply -f ${pipelineParams.deploymentFile} --alsologtostderr=true"
                }
            }
        } catch (err) {
            currentBuild.result = 'FAILED'
            throw err
    }
}