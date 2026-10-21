package com.sovereignops.timetable.backup

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Reads and writes backup text through the system file picker (Storage Access
 * Framework) URIs, so no storage permission is needed. Reads are size-bounded.
 */
object BackupFileIo {

    fun writeText(context: Context, uri: Uri, text: String) {
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open the destination file")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** Read at most [maxBytes] of text; a larger file is refused rather than loaded. */
    fun readText(context: Context, uri: Uri, maxBytes: Int = DEFAULT_MAX_BYTES): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the file")
        input.use { stream ->
            val buffer = ByteArray(16 * 1024)
            val collected = ByteArrayOutputStream()
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (collected.size() + read > maxBytes) throw IOException("File is too large to be a backup")
                collected.write(buffer, 0, read)
            }
            return collected.toString(Charsets.UTF_8.name())
        }
    }

    const val DEFAULT_MAX_BYTES = 32 * 1024 * 1024
}
