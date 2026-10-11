#!/usr/bin/env groovy

/**
 * securityScan - Universal secret scan + dependency audit.
 *
 * Usage:
 *   securityScan(type: 'nodejs')
 *   securityScan(type: 'go', failOnFindings: false)
 *   securityScan(type: 'laravel', gitleaks: [enabled: false])
 *
 * @param config optional map merged with Config.section('security'):
 *   type (laravel|nodejs|go), enabled, failOnFindings,
 *   gitleaks: [enabled, image, args, config],
 *   dependencyAudit: [enabled, failOnError, level]
 */
def call(Map config = [:]) {
    Map cfg = org.devops.Config.section('security', config)
    String type = (config.type ?: 'generic') as String
    new org.devops.SecurityScanner(this, cfg).run(org.devops.BuilderFactory.sectionFor(type))
}
