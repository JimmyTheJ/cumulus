package app.cumulus.data

import app.cumulus.model.ClimateNormals
import app.cumulus.model.SavedPlace
import app.cumulus.model.UnitPrefs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

@Serializable
data class PersistedState(
    val places: List<SavedPlace> = emptyList(),
    val selectedId: String? = null,
    val units: UnitPrefs = UnitPrefs(),
)

@Serializable
private data class CacheEnvelope(val savedAt: Long, val body: String)

class DiskStore(storageDir: String) {
    private val fs = FileSystem.SYSTEM
    private val root: Path = storageDir.toPath()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val mutex = Mutex()

    init {
        runCatching { fs.createDirectories(root) }
    }

    suspend fun load(): PersistedState = mutex.withLock {
        readText(root / "prefs.json")?.let { text ->
            runCatching { json.decodeFromString(PersistedState.serializer(), text) }.getOrNull()
        } ?: PersistedState()
    }

    suspend fun save(state: PersistedState) = mutex.withLock {
        writeText(root / "prefs.json", json.encodeToString(PersistedState.serializer(), state))
    }

    suspend fun readCache(name: String, maxAgeMs: Long, nowMs: Long): String? = mutex.withLock {
        val text = readText(root / "cache" / "$name.json") ?: return null
        val envelope = runCatching { json.decodeFromString(CacheEnvelope.serializer(), text) }.getOrNull() ?: return null
        if (nowMs - envelope.savedAt > maxAgeMs) null else envelope.body
    }

    suspend fun writeCache(name: String, body: String, nowMs: Long) = mutex.withLock {
        fs.createDirectories(root / "cache")
        val envelope = CacheEnvelope(nowMs, body)
        writeText(root / "cache" / "$name.json", json.encodeToString(CacheEnvelope.serializer(), envelope))
    }

    suspend fun readNormals(key: String): ClimateNormals? = mutex.withLock {
        val text = readText(root / "normals" / "$key.json") ?: return null
        runCatching { json.decodeFromString(ClimateNormals.serializer(), text) }.getOrNull()
    }

    suspend fun writeNormals(key: String, normals: ClimateNormals) = mutex.withLock {
        fs.createDirectories(root / "normals")
        writeText(root / "normals" / "$key.json", json.encodeToString(ClimateNormals.serializer(), normals))
    }

    private fun readText(path: Path): String? {
        if (!fs.exists(path)) return null
        return runCatching { fs.read(path) { readUtf8() } }.getOrNull()
    }

    private fun writeText(path: Path, text: String) {
        runCatching {
            val parent = path.parent
            if (parent != null) fs.createDirectories(parent)
            val temporary = parent?.let { it / "${path.name}.tmp" } ?: return
            fs.write(temporary) { writeUtf8(text) }
            if (fs.exists(path)) fs.delete(path)
            fs.atomicMove(temporary, path)
        }
    }
}
