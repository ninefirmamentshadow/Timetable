package com.sovereignops.timetable.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sovereignops.timetable.R
import com.sovereignops.timetable.backup.BackupException
import com.sovereignops.timetable.backup.BackupFileIo
import com.sovereignops.timetable.core.export.IcsWriter
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.data.backup.BackupService
import com.sovereignops.timetable.data.backup.BackupSession
import com.sovereignops.timetable.data.backup.InvalidBackup
import com.sovereignops.timetable.data.backup.RestorePlan
import com.sovereignops.timetable.data.toModelResult
import com.sovereignops.timetable.ui.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The backup, restore and calendar-export flows. State lives here, not in the
 * fragment, so a rotation in the middle of a key derivation does not lose it.
 * Nothing is written to the database until [applyRestore], which only runs after
 * the user confirmed a successfully decrypted and validated backup.
 */
class BackupViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        data object Idle : State
        data class Working(val label: Int) : State

        /** Finished (or failed): show [message] once, then call [consume]. */
        data class Done(val message: String, val isError: Boolean) : State

        /** A file was read and looks like a backup: ask for its passphrase. */
        data object NeedPassphrase : State

        /** Decrypted and valid: ask the user to confirm replacing everything. */
        data class ConfirmRestore(val bookings: Int, val windows: Int, val exportedAt: String) : State
    }

    private val _state = MutableLiveData<State>(State.Idle)
    val state: LiveData<State> = _state

    private var pendingText: String? = null
    private var pendingPlan: RestorePlan? = null
    private var pendingBackupPassphrase: String? = null

    private fun service(): BackupService =
        BackupService(AppDatabase.get(getApplication()), Settings(getApplication()), AppClock::now)

    private fun text(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    private fun reason(e: Exception) = e.message ?: e.javaClass.simpleName

    fun consume() {
        if (_state.value is State.Done) _state.value = State.Idle
    }

    /** Remember the passphrase while the file picker is open. */
    fun holdBackupPassphrase(passphrase: String) {
        pendingBackupPassphrase = passphrase
    }

    fun cancelPending() {
        pendingBackupPassphrase = null
        pendingText = null
        pendingPlan = null
        _state.value = State.Idle
    }

    fun createBackup(uri: Uri) {
        val passphrase = pendingBackupPassphrase ?: return
        pendingBackupPassphrase = null
        _state.value = State.Working(R.string.working_backup)
        viewModelScope.launch {
            _state.value = try {
                val created = service().create(passphrase)
                withContext(Dispatchers.IO) { BackupFileIo.writeText(getApplication(), uri, created.text) }
                BackupSession.markBackedUp()
                val skipped = if (created.unreadableSkipped > 0) {
                    " " + text(R.string.backup_skipped_unreadable, created.unreadableSkipped)
                } else {
                    ""
                }
                State.Done(text(R.string.backup_saved, created.bookings, created.windows) + skipped, false)
            } catch (e: BackupException) {
                State.Done(e.message ?: text(R.string.backup_failed), true)
            } catch (e: IOException) {
                State.Done(text(R.string.backup_write_failed), true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Done(text(R.string.backup_failed_reason, reason(e)), true)
            }
        }
    }

    /** Read the chosen file and check it is a Timetable backup (no passphrase needed). */
    fun openRestoreFile(uri: Uri) {
        _state.value = State.Working(R.string.working_reading)
        viewModelScope.launch {
            _state.value = try {
                val read = withContext(Dispatchers.IO) { BackupFileIo.readText(getApplication(), uri) }
                service().inspect(read)
                pendingText = read
                State.NeedPassphrase
            } catch (e: BackupException) {
                State.Done(e.message ?: text(R.string.restore_failed), true)
            } catch (e: IOException) {
                State.Done(text(R.string.restore_read_failed), true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything unexpected in a chosen file is "not a backup", never a crash.
                State.Done(text(R.string.restore_invalid, reason(e)), true)
            }
        }
    }

    /** Decrypt and validate. On success ask for confirmation; on any failure nothing changed. */
    fun decrypt(passphrase: String) {
        val fileText = pendingText ?: return
        _state.value = State.Working(R.string.working_restore)
        viewModelScope.launch {
            _state.value = try {
                val plan = service().prepareRestore(fileText, passphrase)
                pendingPlan = plan
                State.ConfirmRestore(plan.bookings.size, plan.availability.size, plan.exportedAt)
            } catch (e: BackupException.AuthenticationFailed) {
                // Keep the file so the user can try the passphrase again.
                State.Done(e.message ?: text(R.string.restore_failed), true)
            } catch (e: BackupException) {
                pendingText = null
                State.Done(e.message ?: text(R.string.restore_failed), true)
            } catch (e: InvalidBackup) {
                pendingText = null
                State.Done(text(R.string.restore_invalid, e.message ?: ""), true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingText = null
                State.Done(text(R.string.restore_invalid, reason(e)), true)
            }
        }
    }

    fun applyRestore() {
        val plan = pendingPlan ?: return
        _state.value = State.Working(R.string.working_restore)
        viewModelScope.launch {
            _state.value = try {
                service().apply(plan)
                State.Done(text(R.string.restore_done, plan.bookings.size, plan.availability.size), false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The replace is one transaction: a failure leaves the old data in place.
                State.Done(text(R.string.restore_failed_unchanged), true)
            } finally {
                pendingPlan = null
                pendingText = null
            }
        }
    }

    fun exportCalendar(uri: Uri) {
        _state.value = State.Working(R.string.working_export)
        viewModelScope.launch {
            _state.value = try {
                val bookings = AppDatabase.get(getApplication()).backupDao().allBookings()
                    .mapNotNull { it.toModelResult().getOrNull() }
                val ics = IcsWriter.write(bookings, AppClock.now())
                withContext(Dispatchers.IO) { BackupFileIo.writeText(getApplication(), uri, ics) }
                State.Done(text(R.string.export_saved, bookings.size), false)
            } catch (e: IOException) {
                State.Done(text(R.string.backup_write_failed), true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Done(text(R.string.export_failed_reason, reason(e)), true)
            }
        }
    }
}
