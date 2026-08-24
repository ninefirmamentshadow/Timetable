package com.sovereignops.timetable.ui.editor

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.Money
import com.sovereignops.timetable.core.model.Booking
import com.sovereignops.timetable.core.model.BookingStatus
import com.sovereignops.timetable.databinding.DialogBookingBinding
import com.sovereignops.timetable.ui.MainViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Add / edit one booking. Validates before dismissing, and when the candidate
 * raises scheduling conflicts it surfaces them and asks before saving rather
 * than silently overbooking.
 */
class BookingEditorFragment : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: DialogBookingBinding? = null
    private val binding get() = _binding!!

    private var editingId: Long = 0
    private var date: LocalDate = LocalDate.now()
    private var time: LocalTime = LocalTime.of(19, 0)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        editingId = requireArguments().getLong(ARG_ID, 0L)
        _binding = DialogBookingBinding.inflate(layoutInflater)

        val statuses = BookingStatus.entries
        binding.statusSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            statuses.map { it.label },
        )

        val existing = if (editingId != 0L) viewModel.currentBooking(editingId) else null
        if (existing != null) {
            date = existing.start.toLocalDate()
            time = existing.start.toLocalTime()
            binding.aliasInput.setText(existing.clientAlias)
            binding.durationInput.setText(existing.durationMinutes.toString())
            binding.rateInput.setText(existing.rate.format())
            binding.locationInput.setText(existing.locationLabel)
            binding.notesInput.setText(existing.notes)
            binding.screenedCheck.isChecked = existing.screened
            binding.depositCheck.isChecked = existing.depositReceived
            binding.statusSpinner.setSelection(statuses.indexOf(existing.status))
        } else {
            binding.durationInput.setText(viewModel.defaultDurationMinutes.toString())
        }

        renderDateTime()
        binding.dateButton.setOnClickListener { pickDate() }
        binding.timeButton.setOnClickListener { pickTime() }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (editingId == 0L) R.string.new_booking else R.string.edit_booking)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null) // overridden below to control dismiss
            .setNegativeButton(R.string.cancel, null)

        if (editingId != 0L) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                viewModel.deleteBooking(editingId)
            }
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (attemptSave()) dialog.dismiss()
            }
        }
        return dialog
    }

    private fun renderDateTime() {
        binding.dateButton.text = date.format(DATE_FORMAT)
        binding.timeButton.text = time.format(TIME_FORMAT)
    }

    private fun pickDate() {
        DatePickerDialog(
            requireContext(),
            { _, y, m, d -> date = LocalDate.of(y, m + 1, d); renderDateTime() },
            date.year, date.monthValue - 1, date.dayOfMonth,
        ).show()
    }

    private fun pickTime() {
        TimePickerDialog(
            requireContext(),
            { _, h, min -> time = LocalTime.of(h, min); renderDateTime() },
            time.hour, time.minute, false,
        ).show()
    }

    /** Returns true when the booking was saved (dialog may dismiss). */
    private fun attemptSave(): Boolean {
        val alias = binding.aliasInput.text?.toString()?.trim().orEmpty()
        if (alias.isEmpty()) {
            binding.aliasLayout.error = getString(R.string.error_alias_required)
            return false
        }
        binding.aliasLayout.error = null

        val duration = binding.durationInput.text?.toString()?.trim()?.toIntOrNull()
        if (duration == null || duration <= 0) {
            binding.durationLayout.error = getString(R.string.error_duration)
            return false
        }
        binding.durationLayout.error = null

        val rate = Money.parse(binding.rateInput.text?.toString().orEmpty())
        if (rate == null) {
            binding.rateLayout.error = getString(R.string.error_rate)
            return false
        }
        binding.rateLayout.error = null

        val status = BookingStatus.entries[binding.statusSpinner.selectedItemPosition]

        val candidate = Booking(
            id = editingId,
            start = LocalDateTime.of(date, time),
            durationMinutes = duration,
            clientAlias = alias,
            rate = rate,
            status = status,
            locationLabel = binding.locationInput.text?.toString()?.trim().orEmpty(),
            screened = binding.screenedCheck.isChecked,
            depositReceived = binding.depositCheck.isChecked,
            notes = binding.notesInput.text?.toString()?.trim().orEmpty(),
        )

        val conflicts = viewModel.conflictsFor(candidate)
        if (conflicts.isNotEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.conflicts_title)
                .setMessage(conflicts.joinToString("\n") { "• ${it.message}" })
                .setPositiveButton(R.string.save_anyway) { _, _ ->
                    viewModel.saveBooking(candidate)
                    dismiss()
                }
                .setNegativeButton(R.string.back, null)
                .show()
            return false // keep the editor open behind the warning
        }

        viewModel.saveBooking(candidate)
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "booking_id"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")

        fun newInstance(id: Long) = BookingEditorFragment().apply {
            arguments = Bundle().apply { putLong(ARG_ID, id) }
        }
    }
}
