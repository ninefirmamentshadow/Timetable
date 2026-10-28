package com.sovereignops.timetable.applock

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

enum class AuthResult { SUCCESS, FAILED }

/** Proves the person holds the device credential. Created by the activity that shows the prompt. */
interface Authenticator {
    /** False when the device has no screen lock (nothing to authenticate against). */
    fun isAvailable(): Boolean

    fun authenticate(title: String, subtitle: String, onResult: (AuthResult) -> Unit)
}

/** Builds an [Authenticator]; must be called before the activity is STARTED (it registers result launchers). */
fun interface AuthenticatorFactory {
    fun create(activity: AppCompatActivity): Authenticator
}

/**
 * The system authenticator.
 * - API 30 and later: BiometricPrompt with BIOMETRIC_STRONG or DEVICE_CREDENTIAL (a strong
 *   biometric, or the PIN, pattern or password).
 * - API 26 to 29: the combined prompt is not available, so it uses
 *   KeyguardManager.createConfirmDeviceCredentialIntent (the device credential).
 */
class SystemAuthenticator(private val activity: AppCompatActivity) : Authenticator {

    private var keyguardResult: ((AuthResult) -> Unit)? = null
    private val keyguardLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            keyguardResult?.invoke(if (result.resultCode == android.app.Activity.RESULT_OK) AuthResult.SUCCESS else AuthResult.FAILED)
            keyguardResult = null
        }

    override fun isAvailable(): Boolean = isDeviceAuthAvailable(activity)

    override fun authenticate(title: String, subtitle: String, onResult: (AuthResult) -> Unit) {
        if (Build.VERSION.SDK_INT >= 30) {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                        onResult(AuthResult.SUCCESS)

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                        onResult(AuthResult.FAILED)
                    // onAuthenticationFailed (a wrong finger) is not final: the prompt stays up.
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED)
                .build()
            prompt.authenticate(info)
        } else {
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent(title, subtitle)
            if (intent == null) {
                onResult(AuthResult.FAILED)
            } else {
                keyguardResult = onResult
                keyguardLauncher.launch(intent)
            }
        }
    }

    companion object {
        const val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        /** Is there a screen lock (and, on API 30+, an enrolled way) to authenticate with? */
        fun isDeviceAuthAvailable(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= 30) {
                BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS
            } else {
                context.getSystemService(KeyguardManager::class.java).isDeviceSecure
            }
    }
}
