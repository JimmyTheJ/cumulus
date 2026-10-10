import org.gradle.api.Project
import java.io.File
import java.time.Instant
import java.util.Properties

/**
 * Local APK versions, one directory per Gradle root project:
 * `~/.local/state/apk-versions/<project>/`
 *
 * - [VERSION_FILE]: version name baked into the next APK
 * - [LAST_BUILD_FILE]: metadata for the most recently packaged APKs of one version
 *
 * The version file advances once per Gradle invocation after an APK is packaged.
 * Additional APKs from that same invocation (other build types) are recorded
 * against the same version.
 */
object DevBuildMetadata {
    const val VERSION_FILE = "version.properties"
    const val LAST_BUILD_FILE = "last-build.properties"

    private val recordLock = Any()

    fun projectDir(project: Project): File {
        val safeName = project.rootProject.name
            .ifBlank { "unnamed" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(System.getProperty("user.home"), ".local/state/apk-versions/$safeName")
    }

    fun versionFile(project: Project): File = projectDir(project).resolve(VERSION_FILE)

    fun lastBuildFile(project: Project): File = projectDir(project).resolve(LAST_BUILD_FILE)

    fun loadVersionName(project: Project): String? =
        loadProperties(versionFile(project))?.getProperty("versionName")?.removePrefix("v")

    fun ensureVersionFile(project: Project, versionName: String) {
        if (versionFile(project).isFile) return
        projectDir(project).mkdirs()
        writeVersionName(project, versionName)
        project.logger.lifecycle("Created ${versionFile(project).absolutePath} with version $versionName")
    }

    /**
     * Record [apkFile] and advance the next version once for this [versionName].
     * Returns false when an explicit `cumulus.version` is set, or when this APK
     * was already recorded.
     */
    fun recordApkBuild(
        project: Project,
        versionName: String,
        versionCode: Int,
        apkFile: File,
        variant: String,
        flavor: String,
    ): Boolean {
        if (project.getProperty("cumulus.version") != null) {
            project.logger.lifecycle("Explicit cumulus.version set; local APK version was not advanced")
            return false
        }

        synchronized(recordLock) {
            projectDir(project).mkdirs()
            val existing = loadProperties(lastBuildFile(project))
            if (existing?.getProperty("versionName") == versionName) {
                if (apkFile.name in splitProperty(existing, "apkFileName")) return false
                appendProperty(existing, "apkFileName", apkFile.name)
                appendProperty(existing, "apkPath", apkFile.absolutePath)
                appendProperty(existing, "variant", variant)
                appendProperty(existing, "flavor", flavor)
                store(lastBuildFile(project), existing, "Last packaged APKs for this version")
                project.logger.lifecycle("Recorded additional APK ${apkFile.name} for $versionName")
                return true
            }

            val gitCommit = runCatching {
                project.providers.exec {
                    commandLine("git", "rev-parse", "--short", "HEAD")
                }.standardOutput.asText.get().trim()
            }.getOrElse { "unknown" }

            val lastBuild = Properties().apply {
                setProperty("versionName", versionName)
                setProperty("versionCode", versionCode.toString())
                setProperty("apkFileName", apkFile.name)
                setProperty("apkPath", apkFile.absolutePath)
                setProperty("variant", variant)
                setProperty("flavor", flavor)
                setProperty("gitCommit", gitCommit)
                setProperty("builtAt", Instant.now().toString())
            }
            store(lastBuildFile(project), lastBuild, "Last packaged APKs for this version")

            val nextVersion = bumpDevVersion(versionName)
            writeVersionName(project, nextVersion)
            project.logger.lifecycle("Recorded APK ${apkFile.name} as $versionName")
            project.logger.lifecycle("Next APK version: $nextVersion (${versionFile(project).absolutePath})")
            return true
        }
    }

    /**
     * Shared download folder for sideloading. Keep the APK filename.
     */
    fun copyToSharedFolder(project: Project, apkFile: File): File? {
        val destDir = File("/mnt/d/Users/jamus/Nextcloud/APKs")
        if (!destDir.mkdirs() && !destDir.isDirectory) {
            project.logger.warn("Could not create ${destDir.absolutePath}; APK was not copied")
            return null
        }
        val dest = destDir.resolve(apkFile.name)
        apkFile.copyTo(dest, overwrite = true)
        project.logger.lifecycle("Copied APK to ${dest.absolutePath}")
        return dest
    }

    /**
     * `1.0.0-dev.2` -> `1.0.0-dev.3`; appends `-dev.1` when no dev suffix exists.
     */
    fun bumpDevVersion(versionName: String): String {
        val match = Regex("""^(.*-dev\.)(\d+)$""").find(versionName) ?: return "$versionName-dev.1"
        val (prefix, build) = match.destructured
        return "$prefix${build.toInt() + 1}"
    }

    private fun writeVersionName(project: Project, versionName: String) {
        val props = Properties().apply {
            setProperty("versionName", versionName)
        }
        store(
            versionFile(project),
            props,
            "Next APK version for ${project.rootProject.name}. Not in git.",
        )
    }

    private fun loadProperties(file: File): Properties? {
        if (!file.isFile) return null
        return Properties().apply {
            file.inputStream().use { load(it) }
        }
    }

    private fun store(file: File, properties: Properties, comment: String) {
        file.outputStream().use { stream ->
            properties.store(stream, comment)
        }
    }

    private fun splitProperty(properties: Properties, key: String): Set<String> =
        properties.getProperty(key).orEmpty().split(';').filter { it.isNotBlank() }.toSet()

    private fun appendProperty(properties: Properties, key: String, value: String) {
        val values = splitProperty(properties, key).toMutableSet()
        values += value
        properties.setProperty(key, values.joinToString(";"))
    }
}
