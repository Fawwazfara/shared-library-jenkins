#!/usr/bin/env groovy

/**
 * appPipeline - Opinionated, multi-stack CI wrapper.
 *
 * Orchestrates: checkout -> stack build/test -> security scan -> docker build/push.
 * The stack-specific stages are selected automatically from `type`.
 *
 * Supported types: 'laravel', 'nodejs' (NestJS/Express), 'go' (Fiber).
 *
 * ---------------------------------------------------------------------------
 * Scripted usage (recommended, wrapper provides its own node):
 *
 *   @Library('shared-library@main') _
 *   appPipeline(type: 'nodejs', agent: 'docker')
 *
 * Declarative-ish usage (caller owns the node context):
 *
 *   @Library('shared-library@main') _
 *   node {
 *       appPipeline(type: 'go', skipDocker: true)
 *   }
 * ---------------------------------------------------------------------------
 *
 * @param config map:
 *   type           (required) laravel | nodejs | go (aliases supported)
 *   agent          when present, wraps the run in node(agent)
 *   skipCheckout   bool
 *   skipSecurity   bool
 *   skipDocker     bool
 *   checkout       map  -> merged into checkoutCode config
 *   security       map  -> merged into securityScan config
 *   docker         map  -> merged into dockerBuildPush config
 *   <stack>        map  -> per-stack overrides (e.g. nodejs: [nodeImage: 'node:22'])
 */
def call(Map config = [:]) {
    String type = (config.type ?: '').toString().toLowerCase().trim()
    if (!type) {
        error("appPipeline: 'type' is required. Supported types: ${org.devops.BuilderFactory.supportedTypes().join(', ')}")
    }
    String section = org.devops.BuilderFactory.sectionFor(type)

    Closure pipelineBody = {
        Map stackOverrides = (config[section] ?: [:]) as Map
        Map buildConfig = org.devops.Config.section(section, stackOverrides)

        // 1. Checkout
        if (config.skipCheckout != true) {
            checkoutCode((config.checkout ?: [:]) as Map)
        }

        // 2. Stack specific build & test
        org.devops.BuilderFactory.create(this, type, buildConfig).build()

        // 3. Security scan (secrets + dependency audit)
        if (config.skipSecurity != true) {
            securityScan(
                [type: type] + ((config.security ?: [:]) as Map)
            )
        }

        // 4. Docker build & push
        if (config.skipDocker != true) {
            dockerBuildPush((config.docker ?: [:]) as Map)
        }

        echo "appPipeline completed for type='${type}' (@ ${env.BUILD_TAG})"
    }

    if (config.containsKey('agent')) {
        node(config.agent) {
            pipelineBody()
        }
    } else {
        pipelineBody()
    }
}
