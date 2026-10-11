package org.devops

/**
 * Universal security scanning: secret detection (gitleaks) + dependency audit.
 * The dependency audit command is chosen based on the project type.
 */
class SecurityScanner implements Serializable {

    def script
    Map cfg
    Utils utils

    SecurityScanner(script, Map cfg) {
        this.script = script
        this.cfg = cfg ?: [:]
        this.utils = new Utils(script)
    }

    void run(String type) {
        if (cfg.enabled == false) {
            return
        }
        if ((cfg.gitleaks as Map)?.enabled != false) {
            runGitleaks()
        }
        if ((cfg.dependencyAudit as Map)?.enabled != false) {
            runDependencyAudit(type)
        }
    }

    void runGitleaks() {
        Map gl = cfg.gitleaks as Map
        script.stage('Security: Secret Scan (gitleaks)') {
            String image = (gl.image ?: 'zricethezav/gitleaks:latest') as String
            String args = (gl.args ?: 'detect --source . --no-git --verbose --redact') as String
            String extra = ''
            if (gl.config?.trim()) {
                extra = "-v \"\$(pwd)/${gl.config}:/gitleaks.toml\" "
            }
            String cmd = "docker run --rm -v \"\$(pwd):/repo\" -w /repo ${extra}${image} ${args}".toString()

            int status = (utils.sh(cmd, [label: 'gitleaks', returnStatus: true, allowFailure: true]) ?: 0) as int
            if (status != 0) {
                if (cfg.failOnFindings != false) {
                    script.error("gitleaks found secrets or failed to run (exit ${status})")
                } else {
                    script.echo "WARNING: gitleaks reported findings (exit ${status}) - not failing build"
                }
            }
        }
    }

    void runDependencyAudit(String type) {
        Map da = cfg.dependencyAudit as Map
        String image = imageForType(type)
        String cmd = commandForType(type)

        script.stage('Security: Dependency Audit') {
            if (!cmd) {
                script.echo "No dependency audit command configured for type '${type}'"
                return
            }
            Object result = utils.dockerRun(image, cmd, [
                label        : "audit:${type}",
                env          : [CGO_ENABLED: '0'],
                returnStatus : true,
                allowFailure : true
            ])
            int status = (result ?: 0) as int
            if (status != 0 && da.failOnError == true) {
                script.error("Dependency audit failed for type '${type}' (exit ${status})")
            } else if (status != 0) {
                script.echo "WARNING: dependency audit reported issues for '${type}' (exit ${status})"
            }
        }
    }

    private String imageForType(String type) {
        switch (type) {
            case 'laravel': return Config.section('laravel').composerImage as String
            case 'nodejs' : return Config.section('nodejs').nodeImage as String
            case 'go'     : return Config.section('go').goImage as String
            default       : return ''
        }
    }

    private String commandForType(String type) {
        switch (type) {
            case 'laravel': return 'composer audit --no-interaction'
            case 'nodejs' : return "npm audit --audit-level=${(cfg.dependencyAudit as Map).level ?: 'high'}".toString()
            case 'go'     : return 'go run golang.org/x/vuln/cmd/govulncheck@latest ./...'
            default       : return ''
        }
    }
}
