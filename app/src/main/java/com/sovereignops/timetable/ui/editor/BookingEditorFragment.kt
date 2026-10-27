package com.sovereignops.timetable.ui.editor

import com.sovereignops.timetable.ui.showSecure
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.model.BookingDraft
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.model.DraftError
import com.sovereignops.timetable.databinding.DialogBookingBinding
import com.sovereignops.timetable.ui.Formats
import com.sovereignops.timetable.ui.Privacy
import com.sovereignops.timetable.ui.MainViewModel
import com.sovereignops.timetable.ui.editor.BookingEditorViewModel.Restored
import com.sovereignops.timetable.ui.editor.BookingEditorViewModel.SaveState
import java.time.LocalDate
import java.time.LocalTime

/**
 * Add / edit one booking. The form state lives in [BookingEditorViewModel]
 * (saved state plus a draft kept on the device), so rotation, process death, a
 * crash or closing the dialog never loses what was typed. Save stays disabled
 * until the stored booking has been read, and the dialog closes only after the
 * write succeeded.
 *
 * The dialog does not close on a tap outside it. Back or Cancel with unsaved
 * changes asks whether to keep them as a draft or discard them. When the
 * candidate raises scheduling conflicts the editor surfaces them and asks before
 * saving rather than silently overbooking.
 */
class BookingEditorFragment : DialogFragment() {

    private val main: MainViewModel by activityViewModels()
    private val editor: BookingEditorViewModel by viewModels { BookingEditorViewModel.Factory }
    private var _binding: DialogBookingBinding? = null
    private val binding get() = _binding!!
    private val statuses = BookingStatus.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back and outside taps never close the editor silently; requestClose() decides.
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBookingBinding.inflate(layoutInflater)
        val editingId = editor.draftKey

        binding.statusSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            statuses.map { it.label },
        )
        wireInputs()

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (editingId == 0L) R.string.new_booking else R.string.edit_booking)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null) // overridden below to control dismiss
            .setNegativeButton(R.string.cancel, null) // overridden below: may ask first

        if (editingId != 0L) {
            builder.setNeutralButton(R.string.delete, null) // overridden below: ask first
        }

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                requestClose()
                true
            } else {
                keyCode == KeyEvent.KEYCODE_BACK
            }
        }
        Privacy.apply(dialog.window, requireContext())
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { attemptSave() }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { requestClose() }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { confirmDelete() }
            applyEnabledState(dialog)
        }

        editor.draft.observe(this) { draft ->
            if (draft != null) render(draft)
            applyEnabledState(dialog)
        }
        editor.restored.observe(this) { reason ->
            val b = _binding ?: return@observe
            b.restoredNote.visibility = if (reason == null) View.GONE else View.VISIBLE
            b.restoredNote.text = when (reason) {
                Restored.DRAFT -> getString(R.string.draft_restored)
                Restored.DRAFT_OF_DELETED_BOOKING -> getString(R.string.draft_restored_deleted)
                null -> ""
            }
        }
        editor.saveState.observe(this) { state ->
            when (state) {
                SaveState.Saved -> dismissAllowingStateLoss()
                is SaveState.Failed -> showError(getString(R.string.save_failed, state.reason))
                SaveState.Saving, SaveState.Idle -> Unit
            }
            applyEnabledState(dialog)
        }
        editor.draftWriteFailed.observe(this) { failed ->
            if (failed) showError(getString(R.string.draft_write_failed))
        }
        editor.notFound.observe(this) { gone ->
            if (gone) closeWithMessage(R.string.booking_not_found)
        }
        editor.loadFailed.observe(this) { failed ->
            if (failed) closeWithMessage(R.string.booking_load_failed)
        }
        return dialog
    }

    private fun closeWithMessage(message: Int) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        dismissAllowingStateLoss()
    }

    private fun showError(message: String) {
        val b = _binding ?: return
        b.saveError.text = message
        b.saveError.visibility = View.VISIBLE
    }

    /** Back or Cancel: close at once when nothing would be lost, otherwise ask. */
    private fun requestClose() {
        if (editor.saveState.value == SaveState.Saving) return
        if (!editor.isLoaded) {
            // Still reading: any draft kept from before is untouched and stays for next time.
            editor.keepForLater()
            dismissAllowingStateLoss()
            return
        }
        if (!editor.hasChanges) {
            editor.discard()
            dismissAllowingStateLoss()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_message)
            .setPositiveButton(R.string.keep_draft) { _, _ ->
                editor.keepForLater()
                dismissAllowingStateLoss()
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                editor.discard()
                dismissAllowingStateLoss()
            }
            .setNeutralButton(R.string.keep_editing, null)
            .showSecure()
    }

    /** Deleting asks first; the snackbar that follows offers Undo. */
    private fun confirmDelete() {
        val draft = editor.draft.value ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_booking_title)
            .setMessage(
                getString(
                    R.string.confirm_delete_booking_message,
                    draft.alias.ifBlank { getString(R.string.alias_hint) },
                    "${draft.date.format(Formats.dateShort())}, ${Formats.timeOf(requireContext(), draft.time)}",
                ),
            )
            .setPositiveButton(R.string.delete) { _, _ ->
                editor.discard()
                main.deleteBooking(editor.draftKey)
                dismissAllowingStateLoss()
            }
            .setNegativeButton(R.string.cancel, null)
            .showSecure()
    }

    /** Inputs and Save stay disabled until the stored booking has been read, and while a save is running. */
    private fun applyEnabledState(dialog: AlertDialog) {
        val b = _binding ?: return
        val loaded = editor.isLoaded
        val saving = editor.saveState.value == SaveState.Saving
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { save ->
            save.isEnabled = loaded && !saving
            save.setText(if (saving) R.string.saving else R.string.save)
        }
        // Deleting a booking that was already deleted (a kept draft of it) means nothing.
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = loaded && !saving && editor.bookingId != 0L
        listOf(
            b.aliasInput, b.durationInput, b.rateInput, b.locationInput,
            b.notesInput, b.dateButton, b.timeButton, b.statusSpinner,
            b.screenedCheck, b.depositCheck,
        ).forEach { it.isEnabled = loaded && !saving }
    }

    private fun wireInputs() {
        fun EditText.bind(change: (BookingDraft, String) -> BookingDraft) =
            doAfterTextChanged { text -> editor.update { change(it, text?.toString().orEmpty()) } }
        binding.aliasInput.bind { d, v -> d.copy(alias = v) }
        binding.durationInput.bind { d, v -> d.copy(durationText = v) }
        binding.rateInput.bind { d, v -> d.copy(rateText = v) }
        binding.locationInput.bind { d, v -> d.copy(locationLabel = v) }
        binding.notesInput.bind { d, v -> d.copy(notes = v) }
        binding.screenedCheck.setOnCheckedChangeListener { _, on -> editor.update { it.copy(screened = on) } }
        binding.depositCheck.setOnCheckedChangeListener { _, on -> editor.update { it.copy(depositReceived = on) } }
        binding.statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // The spinner reports its initial position too; only a real change reaches the draft.
                statuses.getOrNull(position)?.let { status -> editor.update { it.copy(status = status) } }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.dateButton.setOnClickListener { pickDate() }
        binding.timeButton.setOnClickListener { pickTime() }
    }

    private fun render(draft: BookingDraft) {
        if (_binding == null) return
        fun EditText.show(value: String) {
            if (text?.toString() != value) setText(value)
        }
        binding.aliasInput.show(draft.alias)
        binding.durationInput.show(draft.durationText)
        binding.rateInput.show(draft.rateText)
        binding.locationInput.show(draft.locationLabel)
        binding.notesInput.show(draft.notes)
        if (binding.screenedCheck.isChecked != draft.screened) binding.screenedCheck.isChecked = draft.screened
        if (binding.depositCheck.isChecked != draft.depositReceived) binding.depositCheck.isChecked = draft.depositReceived
        val index = statuses.indexOf(draft.status)
        if (binding.statusSpinner.selectedItemPosition != index) binding.statusSpinner.setSelection(index)
        binding.dateButton.text = draft.date.format(Formats.dateShort())
        binding.timeButton.text = Formats.timeOf(requireContext(), draft.time)
    }

    private fun pickDate() {
        val current = editor.draft.value?.date ?: return
        DatePickerDialog(
            requireContext(),
            { _, y, m, d -> editor.update { it.copy(date = LocalDate.of(y, m + 1, d)) } },
            current.year, current.monthValue - 1, current.dayOfMonth,
        ).showSecure()
    }

    private fun pickTime() {
        val current = editor.draft.value?.time ?: return
        TimePickerDialog(
            requireContext(),
            { _, h, min -> editor.update { it.copy(time = LocalTime.of(h, min)) } },
            current.hour, current.minute, DateFormat.is24HourFormat(requireContext()),
        ).showSecure()
    }

    /** Validate, warn about conflicts, then hand the write to the view model; the dialog closes on success. */
    private fun attemptSave() {
        val b = _binding ?: return
        when (val built = editor.build()) {
            is BookingEditorViewModel.Result.Blocked -> Unit
            is BookingEditorViewModel.Result.Errors -> {
                b.aliasLayout.error =
                    if (DraftError.ALIAS_REQUIRED in built.errors) getString(R.string.error_alias_required) else null
                b.durationLayout.error =
                    if (DraftError.DURATION_INVALID in built.errors) getString(R.string.error_duration) else null
                b.rateLayout.error =
                    if (DraftError.RATE_INVALID in built.errors) getString(R.string.error_rate) else null
            }
            is BookingEditorViewModel.Result.Ready -> {
                b.aliasLayout.error = null
                b.durationLayout.error = null
                b.rateLayout.error = null
                b.saveError.visibility = View.GONE
                val candidate = built.booking
                val conflicts = main.conflictsFor(candidate)
                if (conflicts.isEmpty()) {
                    editor.save(candidate)
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.conflicts_title)
                        .setMessage(conflicts.joinToString("\n") { "• ${it.message}" })
                        .setPositiveButton(R.string.save_anyway) { _, _ -> editor.save(candidate) }
                        .setNegativeButton(R.string.back, null)
                        .showSecure()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "editor"

        fun newInstance(id: Long) = BookingEditorFragment().apply {
            arguments = Bundle().apply { putLong(BookingEditorViewModel.ARG_ID, id) }
        }

        /**
         * Open the editor unless one is already open (a double tap) or the screen is
         * being saved (showing a dialog then would throw).
         */
        fun show(manager: FragmentManager, id: Long) {
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            newInstance(id).show(manager, TAG)
        }
    }
}
