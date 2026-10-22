package com.sovereignops.timetable.ui.settings

import com.sovereignops.timetable.ui.showSecure
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.R
import com.sovereignops.timetable.databinding.FragmentSettingsBinding
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.ui.AppClock
import com.sovereignops.timetable.ui.MainActivity
import java.time.format.DateTimeFormatter

/** Backup, export and about. Further sections (privacy, reminders) are added here. */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val backup: BackupViewModel by activityViewModels()

    private val createBackupFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            if (uri == null) backup.cancelPending() else backup.createBackup(uri)
        }
    private val openBackupFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) backup.openRestoreFile(uri)
        }
    private val createCalendarFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri: Uri? ->
            if (uri != null) backup.exportCalendar(uri)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backupButton.setOnClickListener { askPassphraseThenPickFile() }
        binding.restoreButton.setOnClickListener { openBackupFile.launch(arrayOf("application/json", "text/plain", "*/*")) }
        binding.exportButton.setOnClickListener { confirmExport() }
        binding.noticesButton.setOnClickListener { showNotices() }
        val prefs = Settings(requireContext())
        binding.secureSwitch.isChecked = prefs.secureWindow
        binding.secureSwitch.setOnCheckedChangeListener { _, on ->
            prefs.secureWindow = on
            (requireActivity() as MainActivity).applySecureWindow()
        }
        backup.state.observe(viewLifecycleOwner) { render(it) }
    }

    private fun render(state: BackupViewModel.State) {
        val working = state as? BackupViewModel.State.Working
        binding.workingLabel.visibility = if (working != null) View.VISIBLE else View.GONE
        if (working != null) binding.workingLabel.setText(working.label)
        listOf(binding.backupButton, binding.restoreButton, binding.exportButton).forEach { it.isEnabled = working == null }

        when (state) {
            is BackupViewModel.State.NeedPassphrase -> PassphraseDialog.show(
                requireContext(), R.string.backup_restore, confirmTwice = false,
                onCancel = { backup.cancelPending() },
                onPassphrase = { backup.decrypt(it) },
            )
            is BackupViewModel.State.ConfirmRestore -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.restore_confirm_title)
                .setMessage(getString(R.string.restore_confirm_message, state.bookings, state.windows, state.exportedAt))
                .setPositiveButton(R.string.restore_confirm_action) { _, _ -> backup.applyRestore() }
                .setNegativeButton(R.string.cancel) { _, _ -> backup.cancelPending() }
                .setOnCancelListener { backup.cancelPending() }
                .showSecure()
            is BackupViewModel.State.Done -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(state.message)
                    .setPositiveButton(android.R.string.ok, null)
                    .showSecure()
                backup.consume()
            }
            else -> Unit
        }
    }

    private fun askPassphraseThenPickFile() {
        PassphraseDialog.show(
            requireContext(), R.string.backup_create, confirmTwice = true,
            onCancel = { backup.cancelPending() },
            onPassphrase = { passphrase ->
                backup.holdBackupPassphrase(passphrase)
                createBackupFile.launch("timetable-backup-${AppClock.today().format(DateTimeFormatter.ISO_LOCAL_DATE)}.json")
            },
        )
    }

    private fun confirmExport() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.export_warning_title)
            .setMessage(R.string.export_warning_message)
            .setPositiveButton(R.string.export_continue) { _, _ ->
                createCalendarFile.launch("timetable-${AppClock.today().format(DateTimeFormatter.ISO_LOCAL_DATE)}.ics")
            }
            .setNegativeButton(R.string.cancel, null)
            .showSecure()
    }

    /** The bundled third-party notices (same text as THIRD_PARTY_NOTICES.md). */
    private fun showNotices() {
        val text = try {
            requireContext().assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() }
        } catch (e: java.io.IOException) {
            getString(R.string.notices_missing)
        }
        val body = TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(requireContext().getColor(R.color.on_surface))
            setPadding(48, 24, 48, 24)
            setTextIsSelectable(true)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.notices_title)
            .setView(ScrollView(requireContext()).apply { addView(body) })
            .setPositiveButton(R.string.close, null)
            .showSecure()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
