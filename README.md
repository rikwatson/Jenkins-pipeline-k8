# Jenkins-pipeline-k8
Jenkins shared library pipeline code for deploying to a kubernetes cluster

Please see below an example jenkinsfile using this library
```
// import
@Library("jenkins-k8-deploy@master")
import io.k8deploy.*
def namespaces = [
    "dev": "namespace-dev",
    "test": "namespace-test",
    "e2e": "namespace-e2e",
    "prod": "namespace-prod"
]
def env_stage = params.env_stage
def app_name = params.app_name
def git_hash = params.git_hash
def image_name = params.image_name
def image_tag = params.image_tag
def no_of_pods = params.no_of_pods
def kube_config_credId = params.kube_config_credId
def agent_label = "namespace-${params.env_stage}-deployer"

// Run deployment
deployTok8 {
    envStage = env_stage
    appName = app_name
    gitHash = git_hash
    namespace = namespaces[env_stage]
    imageName = image_name
    imageTag = image_tag
    noOfPods = no_of_pods
    kubeConfigCredId = kube_config_credId
    agentLabel = agent_label
    kubectlPath = "/snap/bin/kubectl"
}
```
