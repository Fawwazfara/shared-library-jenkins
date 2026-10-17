package org.devops

/**
 * Build/test logic for Laravel / PHP projects.
 *
 * Stages: composer install -> (optional) frontend build -> pint -> tests.
 * The composer image is used for PHP commands; when withNode is enabled the
 * node image is used for the frontend build.
 */
class LaravelBuilder extends Builder implements Serializable {

    LaravelBuilder(script, Map cfg) {
        super(script, cfg)
    }

    @Override
    void build() {
        stage('Laravel: Composer Install') {
            runComposer()
        }

        if (cfg.withNode == true) {
            stage('Laravel: Frontend Build') {
                runIn(cfg.nodeImage as String, [cfg.nodeInstallCmd, cfg.nodeBuildCmd] as List<String>)
            }
        }

        if ((cfg.lint as Map).enabled != false) {
            stage('Laravel: Lint (Pint)') {
                runLint()
            }
        }

        if (((cfg.staticAnalysis ?: [:]) as Map).enabled == true) {
            stage('Laravel: Static Analysis') {
                runStaticAnalysis()
            }
        }

        if ((cfg.test as Map).enabled != false) {
            stage('Laravel: Test') {
                runTests()
            }
        }
    }

    private void runComposer() {
        prepareEnvFile()
        String installArgs = cfg.installArgs ?: ''
        runIn(cfg.composerImage as String,
            ["composer install ${installArgs} --ignore-platform-reqs".toString()] as List<String>,
            [label: 'composer install'])
    }

    private void prepareEnvFile() {
        String envFile = cfg.envFile as String
        if (envFile) {
            utils.sh("test -f .env || cp ${envFile} .env", [allowFailure: true, quiet: true])
        }
    }

    private void runLint() {
        Map lint = cfg.lint as Map
        String tool = (lint.tool ?: 'pint') as String
        String args = (lint.args ?: '') as String
        runIn(cfg.phpImage as String, ["vendor/bin/${tool} ${args}".toString()] as List<String>, [label: "${tool}"])
    }

    private void runStaticAnalysis() {
        Map sa = cfg.staticAnalysis as Map
        String tool = (sa.tool ?: 'phpstan') as String
        String args = (sa.args ?: '') as String
        runIn(cfg.phpImage as String, ["vendor/bin/${tool} ${args}".toString()] as List<String>, [label: "${tool}"])
    }

    private void runTests() {
        Map test = cfg.test as Map
        String cmd = (test.cmd ?: 'php artisan test') as String
        runIn(cfg.phpImage as String, [
            "php artisan key:generate --ansi --force && ${cmd}".toString()
        ] as List<String>, [label: 'php artisan test'])
    }
}
