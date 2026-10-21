package com.sovereignops.timetable.ui.editor

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.model.BookingDraft
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.core.model.DraftError
import com.sovereignops.timetable.databinding.DialogBookingBinding
import com.sovereignops.timetable.ui.Formats
import com.sovereignops.timetable.ui.MainViewModel
import java.time.LocalDate
import java.time.LocalTime

/**
 * Add / edit one booking. The form state lives in [BookingEditorViewModel]
 * (saved state, read by id), so rotation and process death keep what was typed
 * and Save stays disabled until the stored booking has been read. Validates
 * before dismissing, and when the candidate raises scheduling conflicts it
 * surfaces them and asks before saving rather than silently overbooking.
 */
class BookingEditorFragment : DialogFragment() {

    private val main: MainViewModel by activityViewModels()
    private val editor: BookingEditorViewModel by viewModels { BookingEditorViewModel.Factory }
    private var _binding: DialogBookingBinding? = null
    private val binding get() = _binding!!
    private val statuses = BookingStatus.entries

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogBookingBinding.inflate(layoutInflater)
        val editingId = editor.bookingId

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
            .setNegativeButton(R.string.cancel, null)

        if (editingId != 0L) {
            builder.setNeutralButton(R.string.delete, null) // overridden below: ask first
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (attemptSave()) dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { confirmDelete() }
            applyLoadedState(dialog)
        }

        editor.draft.observe(this) { draft ->
            if (draft != null) render(draft)
            applyLoadedState(dialog)
        }
        editor.notFound.observe(this) { gone ->
            if (gone) {
                Toast.makeText(requireContext(), R.string.booking_not_found, Toast.LENGTH_SHORT).show()
                dismissAllowingStateLoss()
            }
        }
        return dialog
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
                main.deleteBooking(editor.bookingId)
                dismissAllowingStateLoss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Inputs and Save stay disabled until the stored booking has been read. */
    private fun applyLoadedState(dialog: AlertDialog) {
        val b = _binding ?: return
        val loaded = editor.isLoaded
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = loaded
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = loaded
        listOf(
            b.aliasInput, b.durationInput, b.rateInput, b.locationInput,
            b.notesInput, b.dateButton, b.timeButton, b.statusSpinner,
            b.screenedCheck, b.depositCheck,
        ).forEach { it.isEnabled = loaded }
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
                editor.update { it.copy(status = statuses[position]) }
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
        ).show()
    }

    private fun pickTime() {
        val current = editor.draft.value?.time ?: return
        TimePickerDialog(
            requireContext(),
            { _, h, min -> editor.update { it.copy(time = LocalTime.of(h, min)) } },
            current.hour, current.minute, DateFormat.is24HourFormat(requireContext()),
        ).show()
    }

    /** Returns true when the booking was saved (dialog may dismiss). */
    private fun attemptSave(): Boolean {
        when (val built = editor.build()) {
            is BookingEditorViewModel.Result.Blocked -> return false
            is BookingEditorViewModel.Result.Errors -> {
                binding.aliasLayout.error =
                    if (DraftError.ALIAS_REQUIRED in built.errors) getString(R.string.error_alias_required) else null
                binding.durationLayout.error =
                    if (DraftError.DURATION_INVALID in built.errors) getString(R.string.error_duration) else null
                binding.rateLayout.error =
                    if (DraftError.RATE_INVALID in built.errors) getString(R.string.error_rate) else null
                return false
            }
            is BookingEditorViewModel.Result.Ready -> {
                binding.aliasLayout.error = null
                binding.durationLayout.error = null
                binding.rateLayout.error = null
                val candidate = built.booking
                val conflicts = main.conflictsFor(candidate)
                if (conflicts.isNotEmpty()) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.conflicts_title)
                        .setMessage(conflicts.joinToString("\n") { "• ${it.message}" })
                        .setPositiveButton(R.string.save_anyway) { _, _ ->
                            main.saveBooking(candidate)
                            dismiss()
                        }
                        .setNegativeButton(R.string.back, null)
                        .show()
                    return false // keep the editor open behind the warning
                }
                main.saveBooking(candidate)
                return true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(id: Long) = BookingEditorFragment().apply {
            arguments = Bundle().apply { putLong(BookingEditorViewModel.ARG_ID, id) }
        }
    }
}
