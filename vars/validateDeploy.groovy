def call(body) {
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    node ("${pipelineParams.agentLabel}") {
        try {
            stage('Run shell') {
                echo "Validating deployment of ${pipelineParams.gitHash} to ${pipelineParams.versionUrl}"
                echo "Delaying for ${pipelineParams.delay} secs..."
                int delay_int = pipelineParams.delay as Integer
                sleep(delay_int)
            }

            stage("Validate deployment version") {
                VERSION_RESPONSE = sh (
                    script: "curl -s -k ${pipelineParams.versionUrl}",
                    returnStdout: true
                ).trim()
                PARSED_VERSION_RESPONSE = readJSON text: VERSION_RESPONSE
                DEPLOYED_VERSION = PARSED_VERSION_RESPONSE['gitHash']
                if(DEPLOYED_VERSION != pipelineParams.gitHash) {
                    error("Expected version ${pipelineParams.gitHash}, but found version ${DEPLOYED_VERSION}. Deployment Failed!")
                }
                echo "Version ${pipelineParams.gitHash} successfully deployed"
            }    
        }
        catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        }
    }

}
