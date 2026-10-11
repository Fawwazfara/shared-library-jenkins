#!/usr/bin/env groovy

/**
 * buildGo - go mod download, golangci-lint and go test (Go / Fiber).
 *
 * Usage:
 *   buildGo()
 *   buildGo(goImage: 'golang:1.23', test: [cmd: 'go test ./... -v'])
 *   buildGo(lint: [args: '--timeout=10m --verbose'])
 *
 * @param config optional map merged with Config.section('go') (see Config.groovy)
 */
def call(Map config = [:]) {
    Map cfg = org.devops.Config.section('go', config)
    new org.devops.GoBuilder(this, cfg).build()
}
