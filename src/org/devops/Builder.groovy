package org.devops

/**
 * Base class for stack-specific builders. Holds the pipeline script and
 * merged config, and exposes small helpers used by all subclasses.
 */
abstract class Builder implements Serializable {

    def script
    Map cfg
    Utils utils

    Builder(script, Map cfg) {
        this.script = script
        this.cfg = cfg ?: [:]
        this.utils = new Utils(script)
    }

    /** Run the stack build/test stages. Must be implemented by subclasses. */
    abstract void build()

    /** Convenience wrapper around script.stage with a skipped guard. */
    protected void stage(String name, Closure body) {
        script.stage(name) {
            body()
        }
    }

    /** Wrap a command so it is executed inside a container, or on host. */
    protected void runIn(String image, List<String> commands, Map opts = [:]) {
        if (image) {
            script.dir(opts.dir ?: '.') {
                utils.dockerRun(image, commands.join(' && '), [
                    label : opts.label ?: "container:${image}",
                    env   : opts.env ?: [:],
                    extra : opts.extra ?: ''
                ])
            }
        } else {
            script.dir(opts.dir ?: '.') {
                commands.each { utils.sh(it) }
            }
        }
    }
}
