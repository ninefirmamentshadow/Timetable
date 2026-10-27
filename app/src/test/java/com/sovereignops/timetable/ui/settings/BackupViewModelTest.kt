package com.sovereignops.timetable.ui.settings

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.core.model.BookingDraft
import com.sovereignops.timetable.core.model.SavedDraft
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.toEntity
import com.sovereignops.timetable.data.backup.BackupSession
import com.sovereignops.timetable.ui.TestSupport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupViewModelTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var vm: BackupViewModel
    private val passphrase = "correct horse battery"

    @Before
    fun setUp() {
        TestSupport.fixClock()
        BackupSession.reset()
        db = TestSupport.installInMemoryDatabase()
        TestSupport.seed(db)
        vm = BackupViewModel(ApplicationProvider.getApplicationContext<Application>())
    }

    @After
    fun tearDown() {
        TestSupport.uninstallDatabase(db)
        TestSupport.restoreClock()
    }

    private fun waitFor(predicate: (BackupViewModel.State) -> Boolean): BackupViewModel.State {
        var last: BackupViewModel.State = BackupViewModel.State.Idle
        TestSupport.awaitUntil(30_000) { last = vm.state.value!!; predicate(last) }
        return last
    }

    private fun rows() = runBlocking { db.backupDao().allBookings() to db.backupDao().allAvailability() }

    private fun newFile(name: String): Pair<File, Uri> = tmp.newFile(name).let { it to Uri.fromFile(it) }

    @Test fun createThenRestoreRoundTripsThroughAFile() {
        val before = rows()
        val (file, uri) = newFile("backup.json")
        vm.holdBackupPassphrase(passphrase)
        vm.createBackup(uri)
        val done = waitFor { it is BackupViewModel.State.Done } as BackupViewModel.State.Done
        assertFalse(done.message, done.isError)
        assertTrue(done.message.startsWith("Backup saved: 5 bookings, 2 availability windows"))
        assertTrue("an encrypted backup was made this session", BackupSession.backedUpThisSession)
        assertTrue(file.length() > 0)
        assertFalse("no plaintext in the file", file.readText().contains("Alias A"))
        vm.consume()

        // Change the data, then restore from the file.
        runBlocking { db.backupDao().replaceAll(emptyList(), emptyList()) }
        vm.openRestoreFile(uri)
        waitFor { it is BackupViewModel.State.NeedPassphrase }
        vm.decrypt(passphrase)
        val confirm = waitFor { it is BackupViewModel.State.ConfirmRestore } as BackupViewModel.State.ConfirmRestore
        assertEquals(5, confirm.bookings)
        assertEquals(2, confirm.windows)
        assertEquals("nothing is written before the user confirms", emptyList<Any>(), rows().first)

        vm.applyRestore()
        val finished = waitFor { it is BackupViewModel.State.Done } as BackupViewModel.State.Done
        assertFalse(finished.message, finished.isError)
        assertEquals(before, rows())
    }

    @Test fun wrongPassphraseLeavesTheDataAndTheFileUntouchedAndAllowsARetry() {
        val (_, uri) = newFile("backup.json")
        vm.holdBackupPassphrase(passphrase)
        vm.createBackup(uri)
        waitFor { it is BackupViewModel.State.Done }
        vm.consume()
        val before = rows()

        vm.openRestoreFile(uri)
        waitFor { it is BackupViewModel.State.NeedPassphrase }
        vm.decrypt("not the passphrase")
        val failed = waitFor { it is BackupViewModel.State.Done } as BackupViewModel.State.Done
        assertTrue(failed.isError)
        assertEquals("Could not unlock the backup. Check the passphrase and try again.", failed.message)
        assertEquals(before, rows())
        vm.consume()

        // The file is still held, so the right passphrase works without picking it again.
        vm.decrypt(passphrase)
        assertTrue(waitFor { it is BackupViewModel.State.ConfirmRestore || it is BackupViewModel.State.Done } is BackupViewModel.State.ConfirmRestore)
    }

    @Test fun aFileThatIsNotABackupIsRejectedBeforeAskingForAPassphrase() {
        val (file, uri) = newFile("notes.txt")
        file.writeText("just some text")
        vm.openRestoreFile(uri)
        val done = waitFor { it is BackupViewModel.State.Done } as BackupViewModel.State.Done
        assertTrue(done.isError)
        assertEquals("This file is not a backup", done.message)
    }

    @Test fun cancellingBeforeConfirmChangesNothing() {
        val (_, uri) = newFile("backup.json")
        vm.holdBackupPassphrase(passphrase)
        vm.createBackup(uri)
        waitFor { it is BackupViewModel.State.Done }
        vm.consume()
        val before = rows()
        runBlocking { db.bookingDao().deleteById(rows().first.first().id) }
        val changed = rows()

        vm.openRestoreFile(uri)
        waitFor { it is BackupViewModel.State.NeedPassphrase }
        vm.decrypt(passphrase)
        waitFor { it is BackupViewModel.State.ConfirmRestore }
        vm.cancelPending()
        assertEquals(BackupViewModel.State.Idle, vm.state.value)
        assertEquals(changed, rows())
        // Nothing left to apply after a cancel.
        vm.applyRestore()
        assertEquals(BackupViewModel.State.Idle, vm.state.value)
        assertEquals(changed, rows())
        assertTrue(before != changed)
    }

    @Test fun calendarExportWritesAnIcsWithoutNotes() {
        val (file, uri) = newFile("cal.ics")
        vm.exportCalendar(uri)
        val done = waitFor { it is BackupViewModel.State.Done } as BackupViewModel.State.Done
        assertFalse(done.isError)
        val text = file.readText()
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\n"))
        assertEquals(5, Regex("BEGIN:VEVENT").findAll(text).count())
        assertTrue(text.contains("SUMMARY:Alias A"))
    }

    @Test fun backupFromAnEmptyPassphraseIsNotStartedWithoutOne() {
        val (file, uri) = newFile("never.json")
        vm.createBackup(uri) // no passphrase was held
        assertEquals(BackupViewModel.State.Idle, vm.state.value)
        assertEquals(0L, file.length())
    }

    private fun restoreFrom(open: () -> Unit) {
        open()
        waitFor { it is BackupViewModel.State.NeedPassphrase }
        vm.decrypt(passphrase)
        waitFor { it is BackupViewModel.State.ConfirmRestore }
        vm.applyRestore()
        val done = waitFor { it is BackupViewModel.State.Done } as BackupViewModel.State.Done
        assertFalse(done.message, done.isError)
        vm.consume()
    }

    // Same convention as Drafts: a restore first seals the current data as a safety copy,
    // and that copy restores through the normal path.
    @Test fun aRestoreKeepsASafetyCopyOfTheReplacedDataThatRestoresItBack() {
        val (_, uri) = newFile("empty.json")
        // A backup of an empty timetable…
        val seeded = rows()
        runBlocking { db.backupDao().replaceAll(emptyList(), emptyList()) }
        vm.holdBackupPassphrase(passphrase)
        vm.createBackup(uri)
        waitFor { it is BackupViewModel.State.Done }
        vm.consume()
        // …restored over the seeded data plus a kept draft.
        TestSupport.seed(db)
        runBlocking {
            db.draftDao().upsert(SavedDraft(0, BookingDraft.blank(LocalDate.of(2026, 10, 20), 60).copy(alias = "Kept"), TestSupport.NOW).toEntity())
        }
        val withData = rows()
        val before = vm.safetyCopies().size
        restoreFrom { vm.openRestoreFile(uri) }
        assertEquals(emptyList<Any>(), rows().first)
        assertEquals("the backup had no drafts, so none are left", null, runBlocking { db.draftDao().get(0) })
        assertEquals(before + 1, vm.safetyCopies().size)
        assertFalse("the safety copy is sealed", vm.safetyCopies().first().readText().contains("Alias A"))

        // The safety copy brings back the replaced bookings, windows and the draft.
        restoreFrom { vm.openSafetyCopy(vm.safetyCopies().first()) }
        assertEquals(withData, rows())
        assertEquals("Kept", runBlocking { db.draftDao().get(0) }?.alias)
        assertTrue(seeded.first.isNotEmpty())
    }
}
