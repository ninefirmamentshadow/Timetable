package com.sovereignops.timetable

import android.app.Application
import com.sovereignops.timetable.applock.AppLockSettings
import com.sovereignops.timetable.ui.Privacy

class TimetableApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // While the app lock is on, screenshots stay blocked whatever the privacy switch says.
        Privacy.forcedOn = { context -> AppLockSettings(context).enabled }
    }
}
