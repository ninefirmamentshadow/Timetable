package com.sovereignops.timetable.ui

import android.app.Dialog
import android.content.Context
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.data.Settings

/**
 * FLAG_SECURE for every window the app shows: it blocks screenshots and screen
 * recording and blanks the app in the recent-apps list. On by default; the
 * operator can switch it off in Settings. [forcedOn] lets another feature (the
 * app lock) keep it on regardless of the setting.
 */
object Privacy {

    /** Extra reasons to stay secure, evaluated each time; the app lock registers here. */
    @Volatile
    var forcedOn: (Context) -> Boolean = { false }

    fun isSecure(context: Context): Boolean = Settings(context).secureWindow || forcedOn(context)

    fun apply(window: Window?, context: Context) {
        window ?: return
        if (isSecure(context)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

/** Show a dialog with the privacy flag applied to its own window. */
fun Dialog.showSecure() {
    Privacy.apply(window, context)
    show()
}

fun MaterialAlertDialogBuilder.showSecure(): AlertDialog {
    val dialog = create()
    dialog.showSecure()
    return dialog
}
