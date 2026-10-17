package org.devops

/**
 * Generic helpers shared by all steps. Pure string/map helpers are static,
 * shell-backed helpers take the pipeline script as constructor argument so
 * they can call sh/echo with the correct context.
 */
class Utils implements Serializable {

    def script

    Utils(script) {
        this.script = script
    }

    // ------------------------------------------------------------------
    // Static / pure helpers
    // ------------------------------------------------------------------

    /** Short git commit SHA of the current workspace (falls back to BUILD_NUMBER). */
    static String shortSha(def script) {
        try {
            String sha = script.sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
            return sha ?: "b${script.env.BUILD_NUMBER}"
        } catch (ignored) {
            return "b${script.env.BUILD_NUMBER}"
        }
    }

    /** Full git commit SHA of the current workspace. */
    static String fullSha(def script) {
        try {
            return script.sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        } catch (ignored) {
            return ''
        }
    }

    /** Slugify an arbitrary string so it is safe for image/repo names. */
    static String slug(String value) {
        return (value ?: '')
            .toLowerCase()
            .replaceAll('[^a-z0-9._/-]+', '-')
            .replaceAll('^-+|-+$', '')
    }

    /** Detect the registry provider from a registry hostname. */
    static String detectProvider(String registry) {
        String host = (registry ?: 'docker.io').toLowerCase()
        if (host.contains('ghcr.io'))   return 'ghcr'
        if (host.contains('docker.io') || host.contains('docker.com')) return 'dockerhub'
        return 'generic'
    }

    // ------------------------------------------------------------------
    // Instance helpers (need the pipeline script)
    // ------------------------------------------------------------------

    /** Whether we appear to run on a Unix-like agent. */
    boolean isUnix() {
        return script.isUnix()
    }

    /** Echo only when logging is not disabled for the step. */
    void log(String message, Map config = [:]) {
        if (config.quiet != true) {
            script.echo message
        }
    }

    /** Run a shell command, optionally tolerant of failures. */
    Object sh(String command, Map opts = [:]) {
        Map args = [script: command]
        if (opts.containsKey('returnStdout')) args.returnStdout = opts.returnStdout
        if (opts.containsKey('returnStatus')) args.returnStatus = opts.returnStatus
        if (opts.containsKey('label'))        args.label        = opts.label
        Object result = script.sh(args)
        if (opts.allowFailure == true) {
            return result
        }
        return result
    }

    /**
     * Derive the fully qualified image name.
     * Precedence: explicit imageName > registry/owner-repo.
     */
    String resolveImageName(Map dockerCfg, String fallbackRepo) {
        if (dockerCfg.imageName?.trim()) {
            String name = dockerCfg.imageName.trim()
            String registry = dockerCfg.registry ?: 'docker.io'
            String host = registry.replaceFirst('^https?://', '').replaceAll('/+$', '')
            if (name.contains('/')) {
                return name
            }
            if (host && !host.equalsIgnoreCase('docker.io')) {
                return "${host}/${name}".toString()
            }
            return name
        }
        String repo = slug(fallbackRepo ?: 'app')
        String host = (dockerCfg.registry ?: 'docker.io').replaceFirst('^https?://', '').replaceAll('/+$', '')
        if (host && !host.equalsIgnoreCase('docker.io')) {
            return "${host}/${repo}".toString()
        }
        return repo
    }

    /** Derive the primary image tag. */
    String resolveImageTag(Map dockerCfg, String fallbackTag) {
        if (dockerCfg.imageTag?.trim()) {
            return dockerCfg.imageTag.trim()
        }
        return slug(fallbackTag ?: shortSha(script))
    }

    /** Determine the Java/OS-agnostic login command for a registry. */
    String dockerLoginCommand(Map dockerCfg) {
        String registry = dockerCfg.registry ?: 'docker.io'
        String provider = dockerCfg.registryProvider ?: detectProvider(registry)
        String host = registry.replaceFirst('^https?://', '').replaceAll('/+$', '')

        if (provider == 'ghcr') {
            return 'echo "$DOCKER_PASSWORD" | docker login ghcr.io -u "$DOCKER_USERNAME" --password-stdin'
        }
        if (provider == 'dockerhub') {
            return 'echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin'
        }
        return "echo \"\$DOCKER_PASSWORD\" | docker login ${host} -u \"\$DOCKER_USERNAME\" --password-stdin"
    }

    /** Run a docker-in-docker / host-socket image step safely. */
    Object dockerRun(String image, String command, Map opts = [:]) {
    // Terjemahkan path workspace container ke path host laptop kamu
        String hostWorkspace = script.WORKSPACE.replace('/var/jenkins_home', '/home/phaethon/jenkins_home')
    
        String mount = opts.workspace != false ? "-v \"${hostWorkspace}:/workspace\" -w /workspace" : ''
        String envArgs = ''
        (opts.env ?: [:]).each { k, v -> envArgs += " -e ${k}=${v}" }
        String extra = opts.extra ?: ''
        // Bungkus dengan `sh -c` DAN paksa --entrypoint sh, karena kalau tidak
        // operator shell (&&, ||, ;) dievaluasi oleh shell HOST Jenkins,
        // bukan di dalam kontainer -> "php: not found" (exit 127).
        String escaped = command.replace("'", "'\\''")
        String cmd = "docker run --rm --entrypoint sh ${mount}${envArgs} ${extra} ${image} -c '${escaped}'".toString()

        Map shOpts = [script: cmd, label: opts.label ?: "docker run ${image}"]
        if (opts.returnStatus == true || opts.allowFailure == true) {
            shOpts.returnStatus = true
        }
        return script.sh(shOpts)
    }
}
