package com.clocktools.timetable.ui.calculator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clocktools.timetable.R
import com.clocktools.timetable.TimetableApplication
import com.clocktools.timetable.calc.CalculationResult
import com.clocktools.timetable.data.Zone
import com.clocktools.timetable.databinding.FragmentCalculatorBinding
import com.clocktools.timetable.ui.TimeFormat
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class CalculatorFragment : Fragment() {

    private var _binding: FragmentCalculatorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalculatorViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = requireActivity().application as TimetableApplication
                CalculatorViewModel(app.zoneRepository, app.configRepository)
            }
        }
    }

    private var renderedZoneIds: List<Long> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalculatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpAppointmentChips()

        binding.zoneChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val zoneId = group.findViewById<Chip>(id)?.tag as? Long ?: return@setOnCheckedStateChangeListener
            viewModel.selectZone(zoneId)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    private fun setUpAppointmentChips() {
        val presetChips = listOf(
            binding.chip30 to 30,
            binding.chip60 to 60,
            binding.chip90 to 90,
            binding.chip120 to 120
        )
        presetChips.forEach { (chip, minutes) ->
            chip.setOnClickListener {
                binding.customMinutesLayout.visibility = View.GONE
                viewModel.setAppointmentMinutes(minutes)
            }
        }
        binding.chipCustom.setOnClickListener {
            binding.customMinutesLayout.visibility = View.VISIBLE
            binding.customMinutesInput.requestFocus()
        }
        binding.customMinutesInput.doAfterTextChanged { text ->
            val minutes = text?.toString()?.toIntOrNull()
            if (minutes != null && minutes > 0) {
                viewModel.setAppointmentMinutes(minutes)
            }
        }
    }

    private fun render(state: CalculatorUiState) {
        when (state) {
            is CalculatorUiState.Loading -> {
                binding.noZonesText.visibility = View.GONE
                binding.calculatorContent.visibility = View.GONE
            }
            is CalculatorUiState.NoZones -> {
                binding.noZonesText.visibility = View.VISIBLE
                binding.calculatorContent.visibility = View.GONE
                binding.staleBanner.visibility = View.GONE
            }
            is CalculatorUiState.Ready -> {
                binding.noZonesText.visibility = View.GONE
                binding.calculatorContent.visibility = View.VISIBLE
                renderZoneChips(state.zones, state.selectedZone)
                renderBanner(state.selectedZone, state.staleVerification)
                renderResult(state.result)
            }
        }
    }

    private fun renderZoneChips(zones: List<Zone>, selectedZone: Zone) {
        val ids = zones.map { it.id }
        if (ids != renderedZoneIds) {
            binding.zoneChipGroup.removeAllViews()
            zones.forEach { zone ->
                val chip = Chip(requireContext()).apply {
                    text = zone.name
                    tag = zone.id
                    isCheckable = true
                    isClickable = true
                }
                binding.zoneChipGroup.addView(chip)
            }
            renderedZoneIds = ids
        }
        for (i in 0 until binding.zoneChipGroup.childCount) {
            val chip = binding.zoneChipGroup.getChildAt(i) as Chip
            chip.isChecked = (chip.tag as? Long) == selectedZone.id
        }
    }

    private fun renderBanner(zone: Zone, stale: Boolean) {
        binding.staleBanner.visibility = if (stale) View.VISIBLE else View.GONE
        if (stale) {
            binding.staleBannerText.text = getString(
                R.string.stale_banner_format,
                zone.name,
                zone.lastVerified.toString()
            )
        }
    }

    private fun renderResult(result: CalculationResult) {
        val breakdown = result.breakdown
        binding.breakdownDeadline.text =
            getString(R.string.calculator_breakdown_deadline, TimeFormat.display(breakdown.deadline))
        binding.breakdownAppointment.text =
            getString(R.string.calculator_breakdown_appointment, breakdown.appointmentMinutes)
        binding.breakdownBuffer.text =
            getString(R.string.calculator_breakdown_buffer, breakdown.bufferMinutes)
        binding.breakdownWalk.text =
            getString(R.string.calculator_breakdown_walk, breakdown.walkToStopMinutes)
        binding.breakdownContingency.text =
            getString(R.string.calculator_breakdown_contingency, breakdown.contingencyMinutes)

        when (result) {
            is CalculationResult.Safe -> {
                val display = TimeFormat.display(result.lastSafeStart)
                binding.resultText.text = display
                binding.resultText.textSize = 56f
                binding.resultText.setTextColor(ContextCompat.getColor(requireContext(), R.color.safe_result))
                binding.breakdownResult.text =
                    getString(R.string.calculator_breakdown_result_safe, display)
            }
            is CalculationResult.NoSafeStart -> {
                binding.resultText.text = getString(R.string.calculator_result_no_safe_start)
                binding.resultText.textSize = 28f
                binding.resultText.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_result))
                binding.breakdownResult.text = getString(R.string.calculator_breakdown_result_no_safe)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
