package com.sovereignops.timetable.ui.settings

import com.sovereignops.timetable.ui.showSecure
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.R
import com.sovereignops.timetable.databinding.FragmentSettingsBinding
import com.sovereignops.timetable.applock.AppLock
import com.sovereignops.timetable.applock.AppLockSettings
import com.sovereignops.timetable.applock.AuthResult
import com.sovereignops.timetable.core.reminders.ReminderPlanner
import com.sovereignops.timetable.data.DatabaseEncryption
import com.sovereignops.timetable.data.Settings
import com.sovereignops.timetable.data.backup.BackupSession
import com.sovereignops.timetable.dbcrypto.DatabaseMode
import com.sovereignops.timetable.reminders.ReminderNotifications
import com.sovereignops.timetable.reminders.ReminderSettings
import com.sovereignops.timetable.ui.MainViewModel
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

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                setReminders(true)
            } else {
                _binding?.remindersSwitch?.isChecked = false
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.reminders_denied)
                    .setPositiveButton(android.R.string.ok, null)
                    .showSecure()
            }
        }
    private val main: MainViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.backupButton.setOnClickListener { askPassphraseThenPickFile() }
        binding.restoreButton.setOnClickListener { openBackupFile.launch(arrayOf("application/json", "text/plain", "*/*")) }
        binding.safetyRestoreButton.setOnClickListener { chooseSafetyCopy() }
        binding.exportButton.setOnClickListener { confirmExport() }
        binding.noticesButton.setOnClickListener { showNotices() }
        binding.appVersion.text = versionText()
        setUpReminders()
        setUpLock()
        setUpDatabase()
        val prefs = Settings(requireContext())
        binding.secureSwitch.isChecked = prefs.secureWindow
        binding.secureSwitch.setOnCheckedChangeListener { _, on ->
            prefs.secureWindow = on
            (requireActivity() as MainActivity).applySecureWindow()
        }
        backup.state.observe(viewLifecycleOwner) { render(it) }
    }

    private fun setUpDatabase() {
        binding.dbEncryptButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.db_encrypt_title)
                .setMessage(R.string.db_encrypt_message)
                .setPositiveButton(R.string.db_encrypt_confirm) { _, _ ->
                    DatabaseEncryption.requestEncryption(requireContext())
                    updateDatabaseSection()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.db_requested_title)
                        .setMessage(R.string.db_requested_message)
                        .setPositiveButton(R.string.db_close_now) { _, _ -> requireActivity().finishAffinity() }
                        .setNegativeButton(R.string.db_later, null)
                        .showSecure()
                }
                .setNegativeButton(R.string.cancel, null)
                .showSecure()
        }
        updateDatabaseSection()
    }

    /** Status, help text and whether Encrypt is offered (only for plaintext, with a backup made this session). */
    private fun updateDatabaseSection() {
        if (_binding == null) return
        val status = DatabaseEncryption.status(requireContext())
        val statusText = when {
            status.requestPending -> getString(R.string.db_status_pending)
            status.mode == DatabaseMode.PLAINTEXT -> getString(R.string.db_status_plaintext)
            else -> getString(R.string.db_status_encrypted)
        }
        binding.dbStatus.text = status.lastResult?.let { statusText + "\n" + getString(R.string.db_last_result, it) } ?: statusText
        val plaintext = status.mode == DatabaseMode.PLAINTEXT && !status.requestPending
        val backedUp = BackupSession.backedUpThisSession
        binding.dbEncryptButton.visibility = if (plaintext) View.VISIBLE else View.GONE
        binding.dbEncryptButton.isEnabled = plaintext && backedUp
        binding.dbHelp.visibility = if (plaintext) View.VISIBLE else View.GONE
        binding.dbHelp.setText(if (backedUp) R.string.db_help_ready else R.string.db_help_need_backup)
    }

    override fun onResume() {
        super.onResume()
        updateDatabaseSection()
    }

    private fun setUpLock() {
        val lock = AppLockSettings(requireContext())
        binding.lockSwitch.isChecked = lock.enabled
        binding.lockTimeoutInput.setText(lock.timeoutSeconds.toString())
        binding.lockSwitch.setOnCheckedChangeListener { _, on ->
            if (on == lock.enabled) return@setOnCheckedChangeListener
            if (!on) {
                setLock(false)
            } else {
                val activity = requireActivity() as MainActivity
                if (!activity.authenticator.isAvailable()) {
                    binding.lockSwitch.isChecked = false
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.lock_needs_screen_lock)
                        .setPositiveButton(android.R.string.ok, null)
                        .showSecure()
                } else {
                    // Prove it works before turning it on, so a broken setup cannot lock the person out.
                    activity.authenticator.authenticate(
                        getString(R.string.lock_prompt_title), getString(R.string.lock_prompt_subtitle),
                    ) { result ->
                        if (_binding == null) return@authenticate
                        if (result == AuthResult.SUCCESS) setLock(true) else binding.lockSwitch.isChecked = false
                    }
                }
            }
        }
        binding.lockTimeoutInput.doAfterTextChanged { saveLockTimeout(tidy = false) }
        binding.lockTimeoutInput.setOnFocusChangeListener { _, focused -> if (!focused) saveLockTimeout() }
        binding.lockTimeoutInput.setOnEditorActionListener { _, _, _ -> saveLockTimeout(); false }
    }

    private fun setLock(on: Boolean) {
        AppLockSettings(requireContext()).enabled = on
        _binding?.let { if (it.lockSwitch.isChecked != on) it.lockSwitch.isChecked = on }
        AppLock.settingsChanged(requireContext())
        (requireActivity() as MainActivity).applySecureWindow()
    }

    // Focus listeners can fire while the view is being torn down: never touch a cleared binding.
    private fun saveLockTimeout(tidy: Boolean = true) {
        val b = _binding ?: return
        val context = context ?: return
        val seconds = b.lockTimeoutInput.text?.toString()?.trim()?.toIntOrNull()
        if (seconds == null || seconds !in 0..AppLockSettings.MAX_TIMEOUT_SECONDS) {
            if (tidy) b.lockTimeoutLayout.error = getString(R.string.lock_error_timeout)
            return
        }
        b.lockTimeoutLayout.error = null
        val lock = AppLockSettings(context)
        if (lock.timeoutSeconds != seconds) {
            lock.timeoutSeconds = seconds
            AppLock.settingsChanged(context)
        }
    }

    private fun setUpReminders() {
        val reminders = ReminderSettings(requireContext())
        binding.remindersSwitch.isChecked = reminders.enabled
        binding.leadInput.setText(reminders.leadMinutes.toString())
        updateRemindersHelp()
        binding.remindersSwitch.setOnCheckedChangeListener { _, on ->
            if (on == reminders.enabled) return@setOnCheckedChangeListener
            if (!on) {
                setReminders(false)
            } else if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                // Asked only now, when the operator switches reminders on.
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                setReminders(true)
            }
        }
        binding.leadInput.doAfterTextChanged { saveLead(tidy = false) }
        binding.leadInput.setOnFocusChangeListener { _, focused -> if (!focused) saveLead() }
        binding.leadInput.setOnEditorActionListener { _, _, _ -> saveLead(); false }
    }

    private fun setReminders(on: Boolean) {
        ReminderSettings(requireContext()).enabled = on
        _binding?.let { if (it.remindersSwitch.isChecked != on) it.remindersSwitch.isChecked = on }
        if (on) ReminderNotifications.ensureChannel(requireContext())
        main.rescheduleReminders()
    }

    private fun saveLead(tidy: Boolean = true) {
        val b = _binding ?: return
        val context = context ?: return
        val minutes = b.leadInput.text?.toString()?.trim()?.toIntOrNull()
        if (minutes == null || minutes !in 0..ReminderPlanner.MAX_LEAD_MINUTES) {
            if (tidy) b.leadLayout.error = getString(R.string.reminders_error_lead)
            return
        }
        b.leadLayout.error = null
        val reminders = ReminderSettings(context)
        if (reminders.leadMinutes != minutes) {
            reminders.leadMinutes = minutes
            updateRemindersHelp()
            main.rescheduleReminders()
        }
    }

    private fun updateRemindersHelp() {
        val b = _binding ?: return
        val context = context ?: return
        val lead = ReminderSettings(context).leadMinutes
        val buffer = Settings(context).travelBufferMinutes
        b.remindersHelp.text = getString(R.string.reminders_help, lead, buffer, lead + buffer)
    }

    private fun render(state: BackupViewModel.State) {
        val working = state as? BackupViewModel.State.Working
        binding.workingLabel.visibility = if (working != null) View.VISIBLE else View.GONE
        if (working != null) binding.workingLabel.setText(working.label)
        listOf(binding.backupButton, binding.restoreButton, binding.safetyRestoreButton, binding.exportButton)
            .forEach { it.isEnabled = working == null }

        when (state) {
            is BackupViewModel.State.NeedPassphrase -> PassphraseDialog.show(
                requireContext(), R.string.backup_restore, confirmTwice = false,
                onCancel = { backup.cancelPending() },
                onPassphrase = { backup.decrypt(it) },
            )
            is BackupViewModel.State.ConfirmRestore -> MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.restore_confirm_title)
                .setMessage(
                    getString(R.string.restore_confirm_message, state.bookings, state.windows, state.exportedAt) +
                        (if (state.drafts > 0) "\n\n" + resources.getQuantityString(R.plurals.restore_confirm_drafts, state.drafts, state.drafts) else "") +
                        "\n\n" + getString(R.string.restore_confirm_safety),
                )
                .setPositiveButton(R.string.restore_confirm_action) { _, _ -> backup.applyRestore() }
                .setNegativeButton(R.string.cancel) { _, _ -> backup.cancelPending() }
                .setOnCancelListener { backup.cancelPending() }
                .showSecure()
            is BackupViewModel.State.Done -> {
                updateDatabaseSection() // a backup may just have unlocked the Encrypt action
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(state.message)
                    .setPositiveButton(android.R.string.ok, null)
                    .showSecure()
                backup.consume()
            }
            else -> Unit
        }
    }

    /** Pre-restore safety copies (newest first), restored through the normal passphrase and confirm steps. */
    private fun chooseSafetyCopy() {
        val copies = backup.safetyCopies()
        if (copies.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.safety_none)
                .setPositiveButton(android.R.string.ok, null)
                .showSecure()
            return
        }
        val stamp = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", java.util.Locale.getDefault())
        val labels = copies.map { f ->
            java.time.Instant.ofEpochMilli(f.lastModified()).atZone(java.time.ZoneId.systemDefault()).format(stamp)
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.safety_choose)
            .setItems(labels) { _, which -> backup.openSafetyCopy(copies[which]) }
            .setNegativeButton(R.string.cancel, null)
            .showSecure()
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
    /** Which build is installed, so a phone test can be matched to a commit and a changelog entry. */
    private fun versionText(): String {
        val info = runCatching {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        }.getOrNull() ?: return ""
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return getString(R.string.app_version, info.versionName ?: "", code)
    }

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
