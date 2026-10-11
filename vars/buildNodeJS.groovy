#!/usr/bin/env groovy

/**
 * buildNodeJS - Install, lint and test a Node.js project (NestJS / Express).
 *
 * Usage:
 *   buildNodeJS()
 *   buildNodeJS(packageManager: 'pnpm')
 *   buildNodeJS(nodeImage: 'node:22-alpine', test: [cmd: 'npm run test:ci'])
 *
 * @param config optional map merged with Config.section('nodejs') (see Config.groovy)
 */
def call(Map config = [:]) {
    Map cfg = org.devops.Config.section('nodejs', config)
    new org.devops.NodeJSBuilder(this, cfg).build()
}
