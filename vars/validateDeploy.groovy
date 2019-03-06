def call(body) {
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    node ("${pipelineParams.agentLabel}") {
        try {
            stage('Run shell') {
                echo "Validating deployment of ${pipelineParams.git_hash} to ${pipelineParams.version_url}"
                echo "Delaying for ${pipelineParams.delay} secs..."
                int delay_int = pipelineParams.delay
                sleep(delay_int)
            }

            stage("Validate deployment version") {
                VERSION_RESPONSE = sh (
                    script: "curl -s -k ${pipelineParams.version_url}",
                    returnStdout: true
                ).trim()
                PARSED_VERSION_RESPONSE = readJSON text: VERSION_RESPONSE
                DEPLOYED_VERSION = PARSED_VERSION_RESPONSE['gitHash']
                if(DEPLOYED_VERSION != pipelineParams.git_hash) {
                    error("Expected version ${pipelineParams.git_hash}, but found version ${DEPLOYED_VERSION}. Deployment Failed!")
                }
                echo "Version ${pipelineParams.git_hash} successfully deployed"
            }    
        }
        catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        }
    }

}
