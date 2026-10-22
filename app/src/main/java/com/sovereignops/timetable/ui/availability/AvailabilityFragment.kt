package com.sovereignops.timetable.ui.availability

import com.sovereignops.timetable.ui.showSecure
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.core.schedule.AvailabilityRules
import com.sovereignops.timetable.databinding.DialogAvailabilityBinding
import com.sovereignops.timetable.databinding.FragmentAvailabilityBinding
import com.sovereignops.timetable.ui.Formats
import com.sovereignops.timetable.ui.MainViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

class AvailabilityFragment : Fragment() {

    private var _binding: FragmentAvailabilityBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: AvailabilityAdapter
    private var windows: List<AvailabilityWindow> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AvailabilityAdapter(
            onEdit = { showWindowDialog(it) },
            onDelete = { confirmDelete(it) },
        )
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.bufferInput.setText(viewModel.travelBufferMinutes.toString())
        binding.bufferInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) persistBuffer()
        }
        binding.saveBufferButton.setOnClickListener { persistBuffer() }

        binding.addButton.setOnClickListener { showWindowDialog(null) }

        viewModel.unreadableWindows.observe(viewLifecycleOwner) { count ->
            binding.unreadableBanner.visibility = if (count > 0) View.VISIBLE else View.GONE
            if (count > 0) {
                binding.unreadableBanner.text = resources.getQuantityString(R.plurals.unreadable_records, count, count)
            }
        }
        viewModel.availability.observe(viewLifecycleOwner) { list ->
            windows = list
            adapter.submitList(list)
            binding.empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun persistBuffer() {
        val minutes = binding.bufferInput.text?.toString()?.trim()?.toIntOrNull()
        if (minutes != null && minutes >= 0) {
            viewModel.travelBufferMinutes = minutes
            binding.bufferInput.setText(viewModel.travelBufferMinutes.toString())
            binding.bufferLayout.error = null
        } else {
            binding.bufferLayout.error = getString(R.string.error_buffer)
        }
    }

    private fun describe(window: AvailabilityWindow): String =
        "${Formats.timeOf(requireContext(), window.start)}–${Formats.minuteOfDay(requireContext(), window.endMinute)}"

    private fun dayName(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

    /** Deleting asks first; the snackbar that follows offers Undo. */
    private fun confirmDelete(window: AvailabilityWindow) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete_window_title)
            .setMessage(getString(R.string.confirm_delete_window_message, dayName(window.dayOfWeek), describe(window)))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteAvailability(window.id) }
            .setNegativeButton(R.string.cancel, null)
            .showSecure()
    }

    /** Add a window ([existing] null) or edit one; overlapping another window is refused. */
    private fun showWindowDialog(existing: AvailabilityWindow?) {
        val dialogBinding = DialogAvailabilityBinding.inflate(layoutInflater)
        val days = DayOfWeek.values().toList()
        dialogBinding.daySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            days.map { dayName(it) },
        )

        var startMinute = existing?.startMinute ?: (18 * 60)
        var endMinute = existing?.endMinute ?: (23 * 60)
        existing?.let { dialogBinding.daySpinner.setSelection(days.indexOf(it.dayOfWeek)) }
        fun render() {
            dialogBinding.startButton.text = Formats.minuteOfDay(requireContext(), startMinute)
            dialogBinding.endButton.text = Formats.minuteOfDay(requireContext(), endMinute)
        }
        render()
        val is24 = DateFormat.is24HourFormat(requireContext())
        dialogBinding.startButton.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, h, m -> startMinute = h * 60 + m; render() },
                startMinute / 60, startMinute % 60, is24,
            ).showSecure()
        }
        dialogBinding.endButton.setOnClickListener {
            // Picking 12:00 AM as the end means midnight, the end of the day.
            TimePickerDialog(
                requireContext(),
                { _, h, m -> endMinute = AvailabilityWindow.endMinuteOf(LocalTime.of(h, m)); render() },
                (endMinute % 1440) / 60, endMinute % 60, is24,
            ).showSecure()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.add_window else R.string.edit_window)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save, null) // overridden below so errors keep the dialog open
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = trySave(existing, days[dialogBinding.daySpinner.selectedItemPosition], startMinute, endMinute)
                if (error == null) {
                    dialog.dismiss()
                } else {
                    dialogBinding.errorText.text = error
                    dialogBinding.errorText.visibility = View.VISIBLE
                }
            }
        }
        dialog.showSecure()
    }

    /** Saves and returns null, or returns the message explaining why it was refused. */
    private fun trySave(existing: AvailabilityWindow?, day: DayOfWeek, startMinute: Int, endMinute: Int): String? {
        if (endMinute <= startMinute) return getString(R.string.error_window_order)
        val candidate = AvailabilityWindow(existing?.id ?: 0, day, startMinute, endMinute)
        AvailabilityRules.overlapping(candidate, windows)?.let {
            return getString(R.string.error_window_overlap, "${dayName(it.dayOfWeek)} ${describe(it)}")
        }
        viewModel.saveAvailability(candidate)
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
