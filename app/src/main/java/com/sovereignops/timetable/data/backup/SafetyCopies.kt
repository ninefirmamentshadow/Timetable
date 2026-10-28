package com.sovereignops.timetable.data.backup

import java.io.File
import java.io.IOException

/**
 * Pre-restore safety copies, the same convention Drafts uses: before a restore
 * replaces anything, the current data is sealed with the restore's passphrase and
 * kept here (app-private, never in Android backup), newest first, at most [keep].
 * A safety copy is an ordinary backup file and is restored through the normal path.
 */
class SafetyCopies(private val dir: File, private val keep: Int = 3) {

    /** Write [sealedText] as the newest copy and prune older ones. Throws if it cannot be written. */
    fun write(sealedText: String, nowMillis: Long): File {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create the safety copy folder")
        var file = File(dir, "$PREFIX$nowMillis$SUFFIX")
        var n = 1
        while (file.exists()) file = File(dir, "$PREFIX$nowMillis-${n++}$SUFFIX")
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(sealedText)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Could not write the safety copy")
        }
        list().drop(keep).forEach { it.delete() }
        return file
    }

    /** Safety copies, newest first. */
    fun list(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            .orEmpty()
            .sortedWith(compareByDescending<File> { order(it).first }.thenByDescending { order(it).second })

    /** (millis, same-millisecond counter) from the file name; unparseable names sort last. */
    private fun order(f: File): Pair<Long, Int> {
        val core = f.name.removePrefix(PREFIX).removeSuffix(SUFFIX)
        val millis = core.substringBefore('-').toLongOrNull() ?: return -1L to 0
        return millis to (core.substringAfter('-', "0").toIntOrNull() ?: 0)
    }

    companion object {
        const val PREFIX = "pre-restore-"
        const val SUFFIX = ".timetable"
    }
}
