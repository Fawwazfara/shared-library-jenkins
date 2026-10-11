package org.devops

/**
 * Docker image build / tag / push logic for Docker Hub and GHCR.
 * Supports buildx, additional tags, multi-stage build args and image scanning.
 */
class DockerBuilder implements Serializable {

    def script
    Map cfg
    Utils utils
    List<String> pushedImages = []

    DockerBuilder(script, Map cfg) {
        this.script = script
        this.cfg = cfg ?: [:]
        this.utils = new Utils(script)
    }

    void run(Map context = [:]) {
        if (cfg.enabled == false) {
            script.echo 'Docker build/push disabled'
            return
        }

        String registry = cfg.registry ?: 'docker.io'
        String imageName = utils.resolveImageName(cfg, context.repoName ?: script.env.JOB_NAME)
        String imageTag = utils.resolveImageTag(cfg, context.gitSha ?: '')
        List<String> tags = collectTags(imageName, imageTag)

        script.stage('Docker: Lint Dockerfile') {
            lintDockerfile()
        }

        script.stage('Docker: Build Image') {
            buildImage(tags)
        }

        script.stage('Docker: Scan Image') {
            if ((cfg.scanImage as Map)?.enabled == true) {
                scanImage(tags.first())
            } else {
                script.echo 'Image scan disabled'
            }
        }

        if (cfg.push != false) {
            script.stage('Docker: Push Image') {
                loginAndPush(registry, tags)
            }
        }
    }

    // ------------------------------------------------------------------

    private List<String> collectTags(String imageName, String imageTag) {
        List<String> tags = ["${imageName}:${imageTag}".toString()]
        ((cfg.additionalTags ?: []) as List).each { t ->
            String extra = "${imageName}:${Utils.slug(t.toString())}".toString()
            if (!tags.contains(extra)) {
                tags.add(extra)
            }
        }
        return tags
    }

    private void lintDockerfile() {
        Map lint = cfg.lintDockerfile as Map
        if (lint?.enabled != true) {
            script.echo 'Dockerfile lint disabled'
            return
        }
        String image = (lint.image ?: 'hadolint/hadolint:latest') as String
        String dockerfile = (cfg.dockerfile ?: 'Dockerfile') as String
        utils.sh("docker run --rm -i ${image} hadolint - < ${dockerfile}",
                 [label: 'hadolint', returnStatus: true, allowFailure: true])
    }

    private void buildImage(List<String> tags) {
        StringBuilder cmd = new StringBuilder('docker build')
        if (cfg.buildx == true) {
            cmd.append(' buildx build')
            cmd.append(cfg.push != false ? ' --push' : ' --load')
        }
        cmd.append(" -f ${cfg.dockerfile ?: 'Dockerfile'}")
        tags.each { cmd.append(" -t ${it}") }
        if (cfg.platform)     cmd.append(" --platform ${cfg.platform}")
        if (cfg.pullBaseImage) cmd.append(' --pull')
        if (cfg.noCache)      cmd.append(' --no-cache')
        (cfg.buildArgs ?: [:]).each { k, v -> cmd.append(" --build-arg ${k}=${v}") }
        cmd.append(" ${cfg.context ?: '.'}")

        utils.sh(cmd.toString(), [label: 'docker build'])
        this.pushedImages = tags
    }

    private void loginAndPush(String registry, List<String> tags) {
        script.withCredentials([script.usernamePassword(
                credentialsId: cfg.credentialsId ?: 'docker-registry-creds',
                usernameVariable: 'DOCKER_USERNAME',
                passwordVariable: 'DOCKER_PASSWORD')]) {
            utils.sh(utils.dockerLoginCommand(cfg), [label: 'docker login'])
        }
        // buildx already pushed during build
        if (cfg.buildx == true && cfg.push != false) {
            script.echo "Images pushed via buildx: ${tags.join(', ')}"
            return
        }
        tags.each { tag ->
            utils.sh("docker push ${tag}".toString(), [label: "docker push ${tag}"])
        }
    }

    private void scanImage(String image) {
        Map scan = cfg.scanImage as Map
        String scanner = (scan.image ?: 'aquasec/trivy:latest') as String
        String severity = (scan.severity ?: 'HIGH,CRITICAL') as String
        int exitCode = (scan.exitCode ?: 1) as int
        String cmd = "docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${scanner} image --severity ${severity} --exit-code ${exitCode} ${image}".toString()
        utils.sh(cmd, [label: 'trivy image scan', allowFailure: true])
    }
}
