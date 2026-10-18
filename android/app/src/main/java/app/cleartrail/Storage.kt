package app.cleartrail

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.net.URI
import java.util.UUID
import org.json.JSONObject

data class Pairing(val deviceId: String, val baseUrl: String, val uploadToken: String, val key: ByteArray) {
    companion object {
        fun parse(json: JSONObject): Pairing {
            require(json.keys().asSequence().toSet() == setOf("version", "deviceId", "baseUrl", "uploadToken", "key")) { "Unexpected pairing fields" }
            require(json.getInt("version") == 1)
            val id = json.getString("deviceId"); require(UUID.fromString(id).toString() == id)
            val uri = URI(json.getString("baseUrl"))
            require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path in listOf("", "/")) { "Invalid server address" }
            val local = uri.host in listOf("127.0.0.1", "localhost", "10.0.2.2")
            require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http" && local)) { "Remote server must use HTTPS" }
            val token = json.getString("uploadToken"); require(token.matches(Regex("[A-Za-z0-9_-]{43}")))
            val key = Crypto.unbase64(json.getString("key")); require(key.size == 32)
            return Pairing(id, uri.toString().trimEnd('/'), token, key)
        }
    }
    fun json(): JSONObject = JSONObject().put("version", 1).put("deviceId", deviceId).put("baseUrl", baseUrl).put("uploadToken", uploadToken).put("key", Crypto.b64(key))
}

fun writeAtomic(file: File, bytes: ByteArray) {
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try { stream.write(bytes); atomic.finishWrite(stream) }
    catch (error: Exception) { atomic.failWrite(stream); throw error }
}

class Vault(context: Context) {
    private val file = File(context.noBackupFilesDir, "vault.bin")
    fun load(): JSONObject? = if (file.exists() || File(file.path + ".bak").exists()) JSONObject(String(Crypto.unwrap(AtomicFile(file).readFully()), Charsets.UTF_8)) else null
    fun save(value: JSONObject) = writeAtomic(file, Crypto.wrap(value.toString().toByteArray(Charsets.UTF_8)))
    fun clear() = AtomicFile(file).delete()
}

class Outbox(context: Context) {
    private val directory = File(context.noBackupFilesDir, "outbox").apply { mkdirs() }
    fun files(): List<File> = directory.listFiles()?.filter { it.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
    fun put(id: String, envelope: JSONObject) {
        val bytes = envelope.toString().toByteArray(Charsets.UTF_8)
        require(files().size < 1000 || File(directory, "$id.json").exists()) { "Outbox full. Sync or clear local data." }
        require(bytes.size <= 96_000)
        writeAtomic(File(directory, "$id.json"), bytes)
    }
    fun remove(id: String) { require(File(directory, "$id.json").delete()) { "Could not acknowledge local record" } }
    fun clear() { directory.listFiles()?.forEach { check(it.delete()) { "Could not delete local data" } } }
}
