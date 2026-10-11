package org.devops

/**
 * Resolves the correct stack builder for a given project type.
 */
class BuilderFactory implements Serializable {

    static final Map<String, Class> BUILDERS = [
        'laravel': LaravelBuilder,
        'php'    : LaravelBuilder,
        'nodejs' : NodeJSBuilder,
        'node'   : NodeJSBuilder,
        'nestjs' : NodeJSBuilder,
        'express': NodeJSBuilder,
        'go'     : GoBuilder,
        'fiber'  : GoBuilder,
        'golang' : GoBuilder
    ]

    static List<String> supportedTypes() {
        return ['laravel', 'nodejs', 'go']
    }

    static Builder create(def script, String type, Map cfg) {
        String key = (type ?: '').toLowerCase().trim()
        Class clazz = BUILDERS[key]
        if (clazz == null) {
            script.error("Unsupported project type '${type}'. Supported: ${supportedTypes().join(', ')}")
        }
        return clazz.newInstance(script, cfg) as Builder
    }

    /** Map a type to the config section name used for the security scanner. */
    static String sectionFor(String type) {
        String key = (type ?: '').toLowerCase().trim()
        switch (key) {
            case 'laravel':
            case 'php':
                return 'laravel'
            case 'nodejs':
            case 'node':
            case 'nestjs':
            case 'express':
                return 'nodejs'
            case 'go':
            case 'fiber':
            case 'golang':
                return 'go'
            default:
                return key
        }
    }
}
