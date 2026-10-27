package com.sovereignops.timetable.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SafetyCopiesTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun keepsTheNewestThreeNewestFirst() {
        val copies = SafetyCopies(tmp.newFolder("pre-restore"))
        for (t in 1L..5L) copies.write("copy $t", nowMillis = 1_000L * t)
        assertEquals(listOf("copy 5", "copy 4", "copy 3"), copies.list().map { it.readText() })
    }

    @Test fun twoCopiesInTheSameMillisecondAreBothKeptInOrder() {
        val copies = SafetyCopies(tmp.newFolder("pre-restore"))
        copies.write("first", 7_000L)
        copies.write("second", 7_000L)
        assertEquals(listOf("second", "first"), copies.list().map { it.readText() })
    }

    @Test fun otherFilesInTheFolderAreIgnoredAndKept() {
        val dir = tmp.newFolder("pre-restore")
        val stray = dir.resolve("notes.txt").apply { writeText("x") }
        val copies = SafetyCopies(dir, keep = 1)
        copies.write("a", 1L)
        copies.write("b", 2L)
        assertEquals(listOf("b"), copies.list().map { it.readText() })
        assertTrue(stray.exists())
    }

    @Test fun aMissingFolderIsCreated() {
        val copies = SafetyCopies(tmp.root.resolve("not/yet/there"))
        copies.write("a", 1L)
        assertEquals(1, copies.list().size)
    }
}
