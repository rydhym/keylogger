package com.example.inputmonitor.platform.ime

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** User-selected local media only. No search queries or editor content leave the device. */
class KeyboardMediaActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (deliver == null) {
            finish()
            return
        }
        if (state == null) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (intent.getStringExtra("mime") == "image/gif") "image/gif" else "image/*"
            }, 1)
        }
    }

    @Deprecated("Uses the platform result API without requiring an extra activity dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val callback = deliver
        deliver = null
        val source = data?.data
        if (requestCode != 1 || resultCode != RESULT_OK || source == null || callback == null) {
            finish()
            return
        }
        Thread {
            var output: File? = null
            val result = runCatching {
                val mime = contentResolver.getType(source) ?: error("Unknown image format")
                require(mime.startsWith("image/"))
                if (intent.getStringExtra("mime") == "image/gif") require(mime == "image/gif")
                val directory = File(cacheDir, "keyboard_media").apply { mkdirs() }
                // Keep recent files readable by recipients; remove expired media on the next use.
                directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }
                    ?.forEach { it.delete() }
                require((directory.listFiles()?.sumOf { it.length() } ?: 0L) < 50L * 1024 * 1024)
                val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                    ?: error("Unsupported image format")
                output = File(directory, "${UUID.randomUUID()}.$extension")
                contentResolver.openInputStream(source).use { input ->
                    requireNotNull(input)
                    output!!.outputStream().use { sink ->
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 10 * 1024 * 1024)
                            sink.write(buffer, 0, count)
                        }
                    }
                }
                FileProvider.getUriForFile(this, "$packageName.fileprovider", output!!) to mime
            }
            runOnUiThread {
                result.onSuccess { (uri, mime) -> callback(uri, mime) }.onFailure {
                    output?.delete()
                    Toast.makeText(this, "Unable to insert image. Choose an image under 10 MB; the local media cache may also be full.", Toast.LENGTH_LONG).show()
                }
                finish()
            }
        }.start()
    }

    companion object {
        internal var deliver: ((Uri, String) -> Unit)? = null
    }
}
