package com.sovereignops.timetable.ui.settings

import com.sovereignops.timetable.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PassphraseDialogTest {
    @Test fun shortPassphraseIsRejected() {
        assertEquals(R.string.passphrase_too_short, PassphraseDialog.problem("short", null))
        assertEquals(R.string.passphrase_too_short, PassphraseDialog.problem("", "x"))
    }

    @Test fun mismatchIsReportedOnlyWhenConfirming() {
        assertEquals(R.string.passphrase_mismatch, PassphraseDialog.problem("long enough", "long enougx"))
        assertNull(PassphraseDialog.problem("long enough", "long enough"))
        assertNull(PassphraseDialog.problem("long enough", null))
    }

    @Test fun loneSurrogateIsRejected() {
        assertEquals(R.string.passphrase_invalid, PassphraseDialog.problem("abcdefgh\uD800", null))
        assertNull(PassphraseDialog.problem("abcdefg🔑", null))
    }
}
