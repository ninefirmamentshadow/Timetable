package com.sovereignops.timetable.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-wide scope for writes that must finish even when the screen that
 * started them goes away (closing the editor, leaving the app). One failed write
 * never cancels the others.
 */
object AppScope {
    val writes: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
