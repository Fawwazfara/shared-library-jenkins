package org.devops

/**
 * Build/test logic for Node.js projects (NestJS / Express).
 *
 * Stages: install -> lint -> build -> test. The install command is
 * auto-detected from the lockfile / packageManager field unless overridden.
 */
class NodeJSBuilder extends Builder implements Serializable {

    NodeJSBuilder(script, Map cfg) {
        super(script, cfg)
    }

    @Override
    void build() {
        stage('NodeJS: Install') {
            runIn(cfg.nodeImage as String, [resolveInstallCmd()] as List<String>, [label: 'node install'])
        }

        if ((cfg.lint as Map).enabled != false) {
            stage('NodeJS: Lint') {
                runIn(cfg.nodeImage as String, [((cfg.lint as Map).cmd ?: 'npm run lint') as String] as List<String>, [label: 'node lint'])
            }
        }

        if ((cfg.build as Map).enabled == true && (cfg.build as Map).cmd) {
            stage('NodeJS: Build') {
                runIn(cfg.nodeImage as String, [((cfg.build as Map).cmd) as String] as List<String>, [label: 'node build'])
            }
        }

        if ((cfg.test as Map).enabled != false) {
            stage('NodeJS: Test') {
                runIn(cfg.nodeImage as String, [((cfg.test as Map).cmd ?: 'npm test') as String] as List<String>, [label: 'node test'])
            }
        }
    }

    private String resolveInstallCmd() {
        if (cfg.installCmd) {
            return cfg.installCmd as String
        }
        String pm = (cfg.packageManager ?: 'npm') as String
        if (pm == 'yarn') return 'yarn install --frozen-lockfile'
        if (pm == 'pnpm') return 'corepack enable && pnpm install --frozen-lockfile'
        return 'npm ci || npm install'
    }
}
