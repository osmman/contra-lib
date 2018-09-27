



def call(parameters = [:]) {
    def version = parameters.get('version')
    def test_cmd = parameters.get('test_cmd')
    def image_name = parameters.get('image_name')
    def build_args = parameters.get('build_args', [:])
    def modify_args = parameters.get('modify_args', [:])
    def docker_registry = parameters.get('docker_registry', 'docker://docker.io')
    def docker_namespace = parameters.get('docker_namespace')
    def send_metrics = parameters.get('send_metrics', true)
    def podTemplateProps = parameters.get('podTemplateProps', [:])
    def credentials = parameters.get('credentials', [])
    def build_root = parameters.get('build_root', '.')
    def container_name = parameters.get('container_name', UUID.randomUUID().toString())

    def buildContainer = podTemplateProps.get('containers')
    if (buildContainer) {
        buildContainer = buildContainer[0]
    }

    deployOpenShiftTemplate(podTemplateProps) {
        ciPipeline(sendMetrics: send_metrics) {


            def containerWrapper = { cmd ->
                executeInContainer(containerName: buildContainer, containerScript: cmd, stageVars: [], credentials: credentials)
            }

            stage('prepare-build') {
                handlePipelineStep {
                    deleteDir()

                    checkout scm

                    currentBuild.displayName = "Build#: ${env.BUILD_NUMBER} - Container Build: ${version}"

                }
            }

            stage('Build-Docker-Image') {
                def buildCmd = null

                if (build_args) {
                    def joinedArgs = build_args.collect { key, value -> "${key}=${value}"}.join(" --build-args ")
                    buildCmd = "buildah bud -t ${image_name} ${build_root} --build-args ${joinedArgs}"

                } else {
                    buildCmd = "buildah bud -t ${image_name} ${build_root}"
                }

                def cmd = """
                set -x
                ${buildCmd}
                buildah from --name ${container_name} ${image_name}
                """
                containerWrapper(cmd)
            }

            stage('test-docker-container') {
                def test_container = "${container_name}-test"
                def cmd = ""
                if (modify_args) {
                    def owner = modify_args['owner']
                    def copyCmds = modify_args['items'].collect { item ->
                        "buildah copy --chown ${owner} ${container_name} ${item[0]} ${item[1]}"
                    }.join('\n')

                    cmd = """
                    set -x
                    ${copyCmds}
                    buildah commit ${container_name} ${image_name}-test
                    buildah from --name ${test_container} ${image_name}-test
                    buildah run -v ${test_container} -- ${test_cmd}
                    """
                } else {
                    cmd = """
                    set -x
                    buildah run ${test_container} -- ${test_cmd}
                    """
                }

                containerWrapper(cmd)
            }

            stage('Tag-Push-docker-image') {
                def pushCmd = null
                if (credentials) {
                    pushCmd = "buildah push --creds \${DOCKER_USERNAME}:\${DOCKER_PASSWORD} localhost/${image_name}:latest ${docker_registry}/${docker_namespace}/${image_name}:latest"
                } else {
                    pushCmd = "buildah push localhost/${image_name}:latest ${docker_registry}/${docker_namespace}/${image_name}:latest"
                }
                def cmd = """
                set -x
                buildah tag ${image_name} ${image_name}:latest ${image_name}:${version}
                ${pushCmd}
                """
                containerWrapper(cmd)
            }
        }
    }
}
