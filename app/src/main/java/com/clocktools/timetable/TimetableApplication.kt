package com.clocktools.timetable

import android.app.Application
import com.clocktools.timetable.data.AppConfig
import com.clocktools.timetable.data.ConfigRepository
import com.clocktools.timetable.data.TimetableDatabase
import com.clocktools.timetable.data.ZoneRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimetableApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { TimetableDatabase.getInstance(this) }
    val zoneRepository by lazy { ZoneRepository(database.zoneDao()) }
    val configRepository by lazy { ConfigRepository(database.appConfigDao()) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch(Dispatchers.IO) {
            if (configRepository.getOnce() == null) {
                configRepository.upsert(AppConfig.default())
            }
        }
    }
}
