package org.devops

/**
 * Central configuration for the shared library.
 *
 * Every vars/ step calls Config.section('<name>', overrides) to obtain a
 * fully-populated, deeply-merged configuration map. This keeps sane
 * enterprise defaults in one place while allowing per-job overrides.
 */
class Config implements Serializable {

    /** Global defaults applied to every step unless overridden. */
    static Map defaults() {
        return [
            checkout: [
                url           : '',
                branch        : '',
                credentialsId : '',
                changelog     : true,
                poll          : false,
                timeout       : 15
            ],

            security: [
                enabled        : true,
                failOnFindings : true,
                gitleaks       : [
                    enabled : true,
                    image   : 'zricethezav/gitleaks:latest',
                    args    : 'detect --source . --no-git --verbose --redact',
                    config  : ''
                ],
                dependencyAudit: [
                    enabled    : true,
                    failOnError: false
                ]
            ],

            docker: [
                enabled          : true,
                registry         : 'docker.io',
                registryProvider : '',           // '', 'dockerhub', 'ghcr' (auto-detected when empty)
                credentialsId    : 'docker-registry-creds',
                imageName        : '',           // derived from repo name when empty
                imageTag         : '',           // derived from git sha when empty
                additionalTags   : [],
                dockerfile       : 'Dockerfile',
                context          : '.',
                buildArgs        : [:],
                platform         : '',
                push             : true,
                buildx           : false,
                pullBaseImage    : false,
                noCache          : false,
                lintDockerfile   : [
                    enabled : false,
                    image   : 'hadolint/hadolint:latest'
                ],
                scanImage        : [
                    enabled : false,
                    image   : 'aquasec/trivy:latest',
                    severity: 'HIGH,CRITICAL',
                    exitCode: 1
                ]
            ],

            laravel: [
                phpImage        : 'php:8.3-cli',
                composerImage   : 'composer:2',
                nodeImage       : 'node:20-alpine',
                installArgs     : '--no-interaction --prefer-dist --no-progress --optimize-autoloader',
                withNode        : false,
                nodeInstallCmd  : 'npm ci',
                nodeBuildCmd    : 'npm run build',
                lint            : [ enabled: true, tool: 'pint', args: '' ],
                staticAnalysis  : [ enabled: false, tool: 'phpstan', args: 'analyse --no-progress --level=max' ],
                test            : [ enabled: true, cmd: 'php artisan key:generate --env=testing && php artisan test' ],
                envFile         : '.env.example'
            ],

            nodejs: [
                nodeImage      : 'node:20-alpine',
                packageManager : 'npm',              // npm | yarn | pnpm
                installCmd     : '',                 // auto-detected when empty
                lint           : [ enabled: true, cmd: 'npm run lint' ],
                build          : [ enabled: false, cmd: 'npm run build' ],
                test           : [ enabled: true, cmd: 'npm test', coverage: false ],
                audit          : [ enabled: true, level: 'high' ]
            ],

            go: [
                goImage        : 'golang:1.23-alpine',
                golangciImage  : 'golangci/golangci-lint:v1.61-alpine',
                lint           : [ enabled: true, args: '--timeout=5m' ],
                test           : [ enabled: true, cmd: 'go test ./... -race -coverprofile=coverage.out', coverage: true ],
                build          : [ enabled: false, cmd: '', output: 'bin/app' ],
                vuln           : [ enabled: true ]
            ],

            notifications: [
                enabled  : false,
                slack    : [ enabled: false, channel: '', credentialsId: '' ]
            ]
        ]
    }

    /**
     * Return the merged configuration for a named section.
     *
     * @param name      section key (checkout, security, docker, laravel, nodejs, go, ...)
     * @param overrides per-job values that win over the defaults
     */
    static Map section(String name, Map overrides = [:]) {
        Map all = defaults()
        Map base = (all.containsKey(name) ? all[name] : [:]) as Map
        return deepMerge(base, overrides ?: [:])
    }

    /** Recursively merge two maps; values in override win. */
    static Map deepMerge(Map base, Map override) {
        Map result = new LinkedHashMap()
        (base ?: [:]).each { k, v -> result[k] = v }
        (override ?: [:]).each { k, v ->
            if (v instanceof Map && result[k] instanceof Map) {
                result[k] = deepMerge(result[k] as Map, v as Map)
            } else if (v != null) {
                result[k] = v
            }
        }
        return result
    }
}
