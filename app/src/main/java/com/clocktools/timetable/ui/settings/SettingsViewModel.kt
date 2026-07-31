package com.clocktools.timetable.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clocktools.timetable.data.AppConfig
import com.clocktools.timetable.data.ConfigRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val configRepository: ConfigRepository) : ViewModel() {

    val config: StateFlow<AppConfig?> = configRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(config: AppConfig) {
        viewModelScope.launch {
            configRepository.upsert(config)
        }
    }
}
