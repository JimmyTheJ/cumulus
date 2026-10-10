import org.gradle.api.Project

/**
 * Version baked into this build.
 *
 * An explicit `cumulus.version` property or `CUMULUS_VERSION` environment variable wins,
 * so a release build is not taken from the local counter.
 * Otherwise the next version is read from
 * `~/.local/state/apk-versions/<project>/version.properties`, which advances after each
 * Gradle invocation that packages an APK.
 * A leading "v" is removed. When no counter exists yet, [fallback] is used and stored.
 *
 * Sample output:
 * v1.0.0 -> 1.0.0
 * null -> 1.0.0-dev.1 (unless a different fallback is set)
 */
fun Project.getVersionName(fallback: String = "0.0.0-dev.1"): String {
    getProperty("cumulus.version")?.removePrefix("v")?.let { return it }
    DevBuildMetadata.ensureVersionFile(this, fallback)
    return DevBuildMetadata.loadVersionName(this) ?: fallback
}

/**
 * Get the version code for a given semantic version.
 * Does not validate the input and thus will throw an exception when parts are missing.
 *
 * The pre-release part ("-rc.1", "-dev.1" etc.) defaults to 99 when it has no number.
 *
 * Sample output:
 * MA.MI.PA-PR   -> MAMIPAPR
 * 0.0.0         ->       99
 * 1.1.1         ->  1010199
 * 1.0.0-dev.1   ->  1000001
 * 1.0.0         ->  1000099
 */
fun getVersionCode(versionName: String): Int {
    val (versionCore, versionPreRelease) =
        when (val index = versionName.indexOf('-')) {
            -1 -> versionName to null
            else -> versionName.substring(0, index) to
                versionName.substring(index + 1, versionName.length)
        }

    val (major, minor, patch) = versionCore
        .splitToSequence('.')
        .mapNotNull(String::toIntOrNull)
        .take(3)
        .toList()

    val buildVersion = versionPreRelease
        ?.substringAfter('.')
        ?.let(String::toIntOrNull)

    var code = 0
    code += major * 1000000
    code += minor * 10000
    code += patch * 100
    code += buildVersion ?: 99

    return code
}
