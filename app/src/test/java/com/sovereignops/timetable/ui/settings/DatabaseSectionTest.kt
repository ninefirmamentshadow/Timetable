package com.sovereignops.timetable.ui.settings

import android.app.Application
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignops.timetable.R
import com.sovereignops.timetable.data.AppDatabase
import com.sovereignops.timetable.data.DatabaseEncryption
import com.sovereignops.timetable.data.backup.BackupSession
import com.sovereignops.timetable.ui.MainActivity
import com.sovereignops.timetable.ui.TestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DatabaseSectionTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        TestSupport.fixClock()
        db = TestSupport.installInMemoryDatabase()
        BackupSession.reset()
        dbFile = app.getDatabasePath(AppDatabase.NAME).also { it.parentFile?.mkdirs() }
        DatabaseEncryption.cancelRequest(app)
        DatabaseEncryption.clearResult(app)
    }

    @After
    fun tearDown() {
        dbFile.delete()
        DatabaseEncryption.cancelRequest(app)
        DatabaseEncryption.clearResult(app)
        BackupSession.reset()
        TestSupport.uninstallDatabase(db)
        TestSupport.restoreClock()
    }

    private fun plaintextOnDisk() = dbFile.writeBytes(("SQLite format 3\u0000\n" + "x").toByteArray(Charsets.ISO_8859_1))
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    private fun open(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.openSettings() }
        scenario.onActivity { idle() }
    }

    @Test fun anEncryptedDatabaseOffersNothingToDo() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario)
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TextView>(R.id.db_status).text.toString().startsWith("Database: encrypted"))
                assertEquals(View.GONE, activity.findViewById<View>(R.id.db_encrypt_button).visibility)
            }
        }
    }

    @Test fun encryptIsDisabledUntilAnEncryptedBackupWasMadeThisSession() {
        plaintextOnDisk()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario)
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<TextView>(R.id.db_status).text.toString().startsWith("Database: not encrypted"))
                val button = activity.findViewById<Button>(R.id.db_encrypt_button)
                assertEquals(View.VISIBLE, button.visibility)
                assertFalse("no backup yet", button.isEnabled)
                assertTrue(activity.findViewById<TextView>(R.id.db_help).text.toString().contains("Make an encrypted backup first"))
            }
        }
    }

    @Test fun afterABackupTheButtonEnablesAndConfirmingRecordsARequestWithoutTouchingTheFile() {
        plaintextOnDisk()
        val before = dbFile.readBytes()
        BackupSession.markBackedUp()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario)
            scenario.onActivity { activity ->
                val button = activity.findViewById<Button>(R.id.db_encrypt_button)
                assertTrue(button.isEnabled)
                button.performClick()
                idle()
                val confirm = ShadowDialog.getLatestDialog() as AlertDialog
                assertFalse("nothing is requested before confirming", DatabaseEncryption.status(app).requestPending)
                confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                idle()
                assertTrue(DatabaseEncryption.status(app).requestPending)
                assertTrue("the live file is not converted now", before.contentEquals(dbFile.readBytes()))
                assertTrue(activity.findViewById<TextView>(R.id.db_status).text.toString().startsWith("Encryption will run the next time"))
                assertFalse(activity.findViewById<Button>(R.id.db_encrypt_button).isEnabled)
            }
        }
    }

    @Test fun theLastResultIsShown() {
        plaintextOnDisk()
        java.io.File(app.noBackupFilesDir, "db.encrypt-result").writeText("failed at EXPORT: disk full; your data was not changed")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            open(scenario)
            scenario.onActivity { activity ->
                assertTrue(
                    activity.findViewById<TextView>(R.id.db_status).text.toString()
                        .contains("Last attempt: failed at EXPORT: disk full; your data was not changed"),
                )
            }
        }
    }
}
