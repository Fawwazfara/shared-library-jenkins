package org.devops

/**
 * Build/test logic for Go projects (Fiber / net/http).
 *
 * Stages: go mod download -> golangci-lint -> go test -> (optional) build.
 * golangci-lint runs through its official image, the rest through the Go image.
 */
class GoBuilder extends Builder implements Serializable {

    GoBuilder(script, Map cfg) {
        super(script, cfg)
    }

    @Override
    void build() {
        stage('Go: Mod Download') {
            runIn(cfg.goImage as String,
                  ['go mod download', 'go mod verify'] as List<String>,
                  [label: 'go mod download'])
        }

        if ((cfg.lint as Map).enabled != false) {
            stage('Go: Lint (golangci-lint)') {
                String args = ((cfg.lint as Map).args ?: '') as String
                utils.dockerRun(
                    cfg.golangciImage as String,
                    "golangci-lint run ${args}".toString(),
                    [label: 'golangci-lint', env: [CGO_ENABLED: '0']]
                )
            }
        }

        if ((cfg.test as Map).enabled != false) {
            stage('Go: Test') {
                String cmd = ((cfg.test as Map).cmd ?: 'go test ./...') as String
                runIn(cfg.goImage as String, [cmd] as List<String>, [label: 'go test'])
            }
        }

        if ((cfg.build as Map).enabled == true) {
            stage('Go: Build') {
                String output = ((cfg.build as Map).output ?: 'bin/app') as String
                String cmd = ((cfg.build as Map).cmd ?: "go build -o ${output} ./...") as String
                runIn(cfg.goImage as String, [cmd] as List<String>, [label: 'go build'])
            }
        }
    }
}
