package com.clocktools.timetable.ui.zones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clocktools.timetable.data.Zone
import com.clocktools.timetable.data.ZoneRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ZonesViewModel(private val zoneRepository: ZoneRepository) : ViewModel() {

    val zones: StateFlow<List<Zone>> = zoneRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(zone: Zone) {
        viewModelScope.launch {
            zoneRepository.delete(zone)
        }
    }
}
