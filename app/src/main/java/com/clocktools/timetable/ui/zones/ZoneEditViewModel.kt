package com.clocktools.timetable.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clocktools.timetable.data.Zone
import com.clocktools.timetable.data.ZoneRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class ZoneEditViewModel(
    private val zoneRepository: ZoneRepository,
    private val zoneId: Long
) : ViewModel() {

    val isNew: Boolean get() = zoneId == -1L

    private val _loadedZone = MutableStateFlow<Zone?>(null)
    val loadedZone: StateFlow<Zone?> = _loadedZone

    init {
        if (!isNew) {
            viewModelScope.launch {
                _loadedZone.value = zoneRepository.getById(zoneId)
            }
        }
    }

    fun save(
        name: String,
        routeLabel: String,
        outboundTime: LocalTime,
        returnTime: LocalTime,
        walkToStopMinutes: Int
    ) {
        val original = _loadedZone.value
        val timesChanged = original == null ||
            original.lastOutboundTime != outboundTime ||
            original.lastReturnTime != returnTime
        val lastVerified = if (timesChanged) LocalDate.now() else original.lastVerified

        val zone = Zone(
            id = original?.id ?: 0,
            name = name,
            routeLabel = routeLabel,
            lastOutboundTime = outboundTime,
            lastReturnTime = returnTime,
            walkToStopMinutes = walkToStopMinutes,
            lastVerified = lastVerified
        )

        viewModelScope.launch {
            if (isNew) zoneRepository.insert(zone) else zoneRepository.update(zone)
        }
    }

    fun delete() {
        val zone = _loadedZone.value ?: return
        viewModelScope.launch {
            zoneRepository.delete(zone)
        }
    }
}
