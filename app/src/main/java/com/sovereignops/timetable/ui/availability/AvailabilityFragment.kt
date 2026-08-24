package com.sovereignops.timetable.ui.availability

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.R
import com.sovereignops.timetable.core.model.AvailabilityWindow
import com.sovereignops.timetable.databinding.DialogAvailabilityBinding
import com.sovereignops.timetable.databinding.FragmentAvailabilityBinding
import com.sovereignops.timetable.ui.MainViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class AvailabilityFragment : Fragment() {

    private var _binding: FragmentAvailabilityBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: AvailabilityAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AvailabilityAdapter { window -> viewModel.deleteAvailability(window.id) }
        binding.list.layoutManager = LinearLayoutManager(requireContext())
        binding.list.adapter = adapter

        binding.bufferInput.setText(viewModel.travelBufferMinutes.toString())
        binding.bufferInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) persistBuffer()
        }
        binding.saveBufferButton.setOnClickListener { persistBuffer() }

        binding.addButton.setOnClickListener { showAddDialog() }

        viewModel.availability.observe(viewLifecycleOwner) { windows ->
            adapter.submit(windows)
            binding.empty.visibility = if (windows.isEmpty()) View.VISIBLE else View.GONE
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

    private fun showAddDialog() {
        val dialogBinding = DialogAvailabilityBinding.inflate(layoutInflater)
        val days = DayOfWeek.values().toList()
        dialogBinding.daySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            days.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) },
        )

        var start = LocalTime.of(18, 0)
        var end = LocalTime.of(23, 0)
        fun render() {
            dialogBinding.startButton.text = start.format(TIME)
            dialogBinding.endButton.text = end.format(TIME)
        }
        render()
        dialogBinding.startButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m -> start = LocalTime.of(h, m); render() },
                start.hour, start.minute, false).show()
        }
        dialogBinding.endButton.setOnClickListener {
            TimePickerDialog(requireContext(), { _, h, m -> end = LocalTime.of(h, m); render() },
                end.hour, end.minute, false).show()
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_window)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                if (end <= start) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(R.string.error_window_order)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@setPositiveButton
                }
                val day = days[dialogBinding.daySpinner.selectedItemPosition]
                viewModel.saveAvailability(AvailabilityWindow(0, day, start, end))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("h:mm a")
    }
}
