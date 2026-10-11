#!/usr/bin/env groovy

/**
 * buildLaravel - Composer install, Pint lint and PHPUnit/Artisan tests.
 *
 * Usage:
 *   buildLaravel()
 *   buildLaravel(phpImage: 'php:8.3-cli', withNode: true)
 *   buildLaravel(lint: [enabled: false], test: [cmd: 'php artisan test --parallel'])
 *
 * @param config optional map merged with Config.section('laravel') (see Config.groovy)
 */
def call(Map config = [:]) {
    Map cfg = org.devops.Config.section('laravel', config)
    new org.devops.LaravelBuilder(this, cfg).build()
}
