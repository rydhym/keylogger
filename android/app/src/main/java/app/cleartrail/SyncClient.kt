package app.cleartrail

import java.net.HttpURLConnection
import java.net.URL

class SyncClient {
    fun upload(pairing: Pairing, outbox: Outbox): Int {
        var count = 0
        for (file in outbox.files()) {
            val id = file.name.removeSuffix(".json")
            val connection = URL("${pairing.baseUrl}/api/devices/${pairing.deviceId}/records/$id").openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Authorization", "Bearer ${pairing.uploadToken}")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                val bytes = file.readBytes()
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                check(code == 200 || code == 201) { if (code == 401) "Pairing rejected or revoked. Pending records remain on this phone." else "Sync failed (HTTP $code). Pending records remain on this phone." }
                outbox.remove(id)
                count++
            } finally { connection.disconnect() }
        }
        return count
    }
}
