#!/usr/bin/env groovy

/**
 * checkoutCode - Checkout source code from Git SCM.
 *
 * Usage:
 *   checkoutCode()                                  // uses the job's SCM definition
 *   checkoutCode(branch: 'develop')
 *   checkoutCode(url: 'git@github.com:org/repo.git', credentialsId: 'github-ssh')
 *
 * @param config optional map:
 *   url, branch, credentialsId, changelog, poll, timeout, sparseCheckout (List)
 */
def call(Map config = [:]) {
    Map cfg = org.devops.Config.section('checkout', config)
    Map result = [branch: cfg.branch]

    stage('Checkout') {
        timeout(time: (cfg.timeout ?: 15) as int, unit: 'MINUTES') {
            if (!cfg.url) {
                // No explicit URL -> use the SCM configured on the job.
                checkout scm
                result.commit = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                return
            }

            List extensions = []
            if (cfg.changelog != false) {
                extensions << [$class: 'ChangelogToBranch',
                               options: [compareBranches: false]]
            }
            if (cfg.poll == true) {
                extensions << [$class: 'PollingTrigger']
            }
            if (config.sparseCheckout) {
                extensions << [$class: 'SparseCheckoutPaths',
                               sparseCheckoutPaths: ((List) config.sparseCheckout).collect { [path: it] }]
            }

            List userRemoteConfigs = [[url: cfg.url]]
            if (cfg.credentialsId) {
                userRemoteConfigs[0].credentialsId = cfg.credentialsId
            }

            checkout([
                $class           : 'GitSCM',
                branches         : [[name: cfg.branch ?: '*/main']],
                extensions       : extensions,
                userRemoteConfigs: userRemoteConfigs
            ])

            result.commit = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        }
    }
    return result
}
