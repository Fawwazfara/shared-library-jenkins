#!/usr/bin/env groovy

/**
 * dockerBuildPush - Build a Docker image and push it to a registry.
 *
 * Supports Docker Hub and GHCR. Registry provider is auto-detected from the
 * registry hostname unless `registryProvider` is set explicitly.
 *
 * Usage:
 *   dockerBuildPush()                                             // docker.io
 *   dockerBuildPush(registry: 'ghcr.io', credentialsId: 'ghcr-creds',
 *                   imageName: 'org/app', additionalTags: ['latest'])
 *   dockerBuildPush(registry: 'ghcr.io', buildx: true, platform: 'linux/amd64,linux/arm64')
 *
 * @param config optional map merged with Config.section('docker') (see Config.groovy)
 */
def call(Map config = [:]) {
    Map cfg = org.devops.Config.section('docker', config)

    String repoName = ''
    try {
        repoName = sh(script: 'basename -s .git $(git config --get remote.origin.url)', returnStdout: true).trim()
    } catch (ignored) {
        repoName = env.JOB_NAME
    }

    def builder = new org.devops.DockerBuilder(this, cfg)
    builder.run([
        repoName: repoName,
        gitSha  : org.devops.Utils.shortSha(this)
    ])
    return builder.pushedImages
}
