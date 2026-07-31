package com.clocktools.timetable.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clocktools.timetable.calc.CalculationResult
import com.clocktools.timetable.calc.CurfewCalculator
import com.clocktools.timetable.data.ConfigRepository
import com.clocktools.timetable.data.Zone
import com.clocktools.timetable.data.ZoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

sealed interface CalculatorUiState {
    data object Loading : CalculatorUiState
    data object NoZones : CalculatorUiState
    data class Ready(
        val zones: List<Zone>,
        val selectedZone: Zone,
        val appointmentMinutes: Int,
        val result: CalculationResult,
        val staleVerification: Boolean
    ) : CalculatorUiState
}

class CalculatorViewModel(
    private val zoneRepository: ZoneRepository,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val zones: StateFlow<List<Zone>> = zoneRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val config = configRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selectedZoneId = MutableStateFlow<Long?>(null)
    private val appointmentMinutes = MutableStateFlow(30)

    fun selectZone(zoneId: Long) {
        selectedZoneId.value = zoneId
    }

    fun setAppointmentMinutes(minutes: Int) {
        appointmentMinutes.value = minutes
    }

    val uiState: StateFlow<CalculatorUiState> =
        combine(zones, config, selectedZoneId, appointmentMinutes) { zoneList, cfg, selectedId, minutes ->
            val zone = zoneList.firstOrNull { it.id == selectedId } ?: zoneList.firstOrNull()
            if (zone == null || cfg == null) {
                CalculatorUiState.NoZones
            } else {
                val result = CurfewCalculator.lastSafeBookingStart(
                    lastReturnTime = zone.lastReturnTime,
                    appointmentMinutes = minutes,
                    bufferMinutes = cfg.bufferMinutes,
                    walkToStopMinutes = zone.walkToStopMinutes,
                    contingencyMinutes = cfg.contingencyMinutes
                )
                val stale = zone.lastVerified
                    .plusDays(cfg.reverifyIntervalDays.toLong())
                    .isBefore(LocalDate.now())
                CalculatorUiState.Ready(zoneList, zone, minutes, result, stale)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalculatorUiState.Loading)
}
