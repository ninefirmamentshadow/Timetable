package com.clocktools.timetable.ui.zones

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import com.clocktools.timetable.R
import com.clocktools.timetable.TimetableApplication
import com.clocktools.timetable.calc.ZoneTimeValidation
import com.clocktools.timetable.databinding.FragmentZoneEditBinding
import com.clocktools.timetable.ui.TimeFormat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat as PickerTimeFormat
import kotlinx.coroutines.launch
import java.time.LocalTime

class ZoneEditFragment : Fragment() {

    private var _binding: FragmentZoneEditBinding? = null
    private val binding get() = _binding!!

    private val zoneId: Long by lazy { requireArguments().getLong("zoneId", -1L) }

    private val viewModel: ZoneEditViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = requireActivity().application as TimetableApplication
                ZoneEditViewModel(app.zoneRepository, zoneId)
            }
        }
    }

    private var outboundTime: LocalTime = LocalTime.of(8, 0)
    private var returnTime: LocalTime = LocalTime.of(21, 0)
    private var fieldsPopulated = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentZoneEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateTimeButtons()
        binding.walkInput.setText("15")

        binding.outboundTimeButton.setOnClickListener { pickTime(outboundTime) { outboundTime = it; updateTimeButtons() } }
        binding.returnTimeButton.setOnClickListener { pickTime(returnTime) { returnTime = it; updateTimeButtons() } }

        binding.saveButton.setOnClickListener { onSaveClicked() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
        binding.deleteButton.visibility = if (viewModel.isNew) View.GONE else View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loadedZone.collect { zone ->
                    if (zone != null && !fieldsPopulated) {
                        fieldsPopulated = true
                        binding.nameInput.setText(zone.name)
                        binding.routeInput.setText(zone.routeLabel)
                        outboundTime = zone.lastOutboundTime
                        returnTime = zone.lastReturnTime
                        binding.walkInput.setText(zone.walkToStopMinutes.toString())
                        binding.verifiedText.text =
                            getString(R.string.zone_edit_verified_label, zone.lastVerified.toString())
                        updateTimeButtons()
                    }
                }
            }
        }
    }

    private fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(PickerTimeFormat.CLOCK_12H)
            .setHour(initial.hour)
            .setMinute(initial.minute)
            .build()
        picker.addOnPositiveButtonClickListener {
            onPicked(LocalTime.of(picker.hour, picker.minute))
        }
        picker.show(childFragmentManager, "time_picker")
    }

    private fun updateTimeButtons() {
        binding.outboundTimeButton.text = TimeFormat.display(outboundTime)
        binding.returnTimeButton.text = TimeFormat.display(returnTime)
    }

    private fun onSaveClicked() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val route = binding.routeInput.text?.toString()?.trim().orEmpty()
        val walk = binding.walkInput.text?.toString()?.toIntOrNull() ?: 0

        var valid = true
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.zone_edit_error_name_required)
            valid = false
        } else {
            binding.nameLayout.error = null
        }
        if (route.isEmpty()) {
            binding.routeLayout.error = getString(R.string.zone_edit_error_route_required)
            valid = false
        } else {
            binding.routeLayout.error = null
        }
        val timesValid = ZoneTimeValidation.isValidZoneTime(outboundTime) &&
            ZoneTimeValidation.isValidZoneTime(returnTime)
        binding.timeErrorText.visibility = if (timesValid) View.GONE else View.VISIBLE
        if (!timesValid) valid = false

        if (!valid) return

        viewModel.save(name, route, outboundTime, returnTime, walk)
        findNavController().popBackStack()
    }

    private fun confirmDelete() {
        val zoneName = viewModel.loadedZone.value?.name.orEmpty()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zones_delete_confirm_title)
            .setMessage(getString(R.string.zones_delete_confirm_message, zoneName))
            .setNegativeButton(R.string.zones_cancel, null)
            .setPositiveButton(R.string.zones_delete) { _, _ ->
                viewModel.delete()
                findNavController().popBackStack()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
