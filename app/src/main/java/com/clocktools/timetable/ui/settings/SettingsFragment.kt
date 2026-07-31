package com.clocktools.timetable.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clocktools.timetable.R
import com.clocktools.timetable.TimetableApplication
import com.clocktools.timetable.data.AppConfig
import com.clocktools.timetable.databinding.FragmentSettingsBinding
import com.clocktools.timetable.ui.TimeFormat
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat as PickerTimeFormat
import kotlinx.coroutines.launch
import java.time.LocalTime

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = requireActivity().application as TimetableApplication
                SettingsViewModel(app.configRepository)
            }
        }
    }

    private var curfewTime: LocalTime = LocalTime.of(22, 0)
    private var fieldsPopulated = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateCurfewButton()
        binding.curfewTimeButton.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(PickerTimeFormat.CLOCK_12H)
                .setHour(curfewTime.hour)
                .setMinute(curfewTime.minute)
                .build()
            picker.addOnPositiveButtonClickListener {
                curfewTime = LocalTime.of(picker.hour, picker.minute)
                updateCurfewButton()
            }
            picker.show(childFragmentManager, "curfew_time_picker")
        }

        binding.settingsSaveButton.setOnClickListener { onSaveClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.config.collect { config ->
                    if (config != null && !fieldsPopulated) {
                        fieldsPopulated = true
                        curfewTime = config.curfewTime
                        updateCurfewButton()
                        binding.bufferInput.setText(config.bufferMinutes.toString())
                        binding.contingencyInput.setText(config.contingencyMinutes.toString())
                        binding.reverifyInput.setText(config.reverifyIntervalDays.toString())
                    }
                }
            }
        }
    }

    private fun updateCurfewButton() {
        binding.curfewTimeButton.text = TimeFormat.display(curfewTime)
    }

    private fun onSaveClicked() {
        val buffer = binding.bufferInput.text?.toString()?.toIntOrNull() ?: 20
        val contingency = binding.contingencyInput.text?.toString()?.toIntOrNull() ?: 15
        val reverify = binding.reverifyInput.text?.toString()?.toIntOrNull() ?: 90

        viewModel.save(
            AppConfig(
                curfewTime = curfewTime,
                bufferMinutes = buffer,
                contingencyMinutes = contingency,
                reverifyIntervalDays = reverify
            )
        )
        Toast.makeText(requireContext(), R.string.settings_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
