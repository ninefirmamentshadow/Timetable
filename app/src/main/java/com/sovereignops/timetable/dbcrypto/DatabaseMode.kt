package com.sovereignops.timetable.dbcrypto

import java.io.File
import java.io.IOException

/** What is on disk at a database path. */
enum class DatabaseMode { ABSENT, PLAINTEXT, ENCRYPTED }

object DatabaseModes {

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    /**
     * Reads the first 16 bytes. A plaintext SQLite file always starts with
     * "SQLite format 3"; an encrypted one starts with random-looking bytes. An
     * empty or missing file counts as [DatabaseMode.ABSENT].
     */
    fun detect(file: File): DatabaseMode {
        if (!file.exists() || file.length() == 0L) return DatabaseMode.ABSENT
        val head = ByteArray(SQLITE_HEADER.size)
        try {
            file.inputStream().use { input ->
                var read = 0
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
                if (read < head.size) return DatabaseMode.ENCRYPTED // too short to be SQLite: not readable as plaintext
            }
        } catch (e: IOException) {
            return DatabaseMode.ENCRYPTED
        }
        return if (head.contentEquals(SQLITE_HEADER)) DatabaseMode.PLAINTEXT else DatabaseMode.ENCRYPTED
    }
}
