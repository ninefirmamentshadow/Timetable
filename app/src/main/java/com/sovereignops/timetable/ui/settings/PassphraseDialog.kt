package com.sovereignops.timetable.ui.settings

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sovereignops.timetable.R
import com.sovereignops.timetable.backup.BackupEnvelope
import com.sovereignops.timetable.databinding.DialogPassphraseBinding

/** Asks for a backup passphrase. The dialog stays open until the input is acceptable. */
object PassphraseDialog {

    /** Pure input check, shared by both modes. Returns a message id or null when fine. */
    fun problem(passphrase: String, confirm: String?): Int? = when {
        passphrase.length < BackupEnvelope.MIN_PASSPHRASE_LENGTH -> R.string.passphrase_too_short
        hasLoneSurrogate(passphrase) -> R.string.passphrase_invalid
        confirm != null && confirm != passphrase -> R.string.passphrase_mismatch
        else -> null
    }

    private fun hasLoneSurrogate(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length || !Character.isLowSurrogate(text[i + 1])) return true
                i += 2
            } else {
                if (Character.isLowSurrogate(c)) return true
                i++
            }
        }
        return false
    }

    /**
     * @param confirmTwice true when choosing a new passphrase (typed twice), false when
     *   entering the one a backup was made with.
     */
    fun show(
        context: Context,
        title: Int,
        confirmTwice: Boolean,
        onCancel: () -> Unit,
        onPassphrase: (String) -> Unit,
    ): AlertDialog {
        val binding = DialogPassphraseBinding.inflate(LayoutInflater.from(context))
        binding.passphraseHelp.setText(if (confirmTwice) R.string.passphrase_create_help else R.string.passphrase_restore_help)
        binding.passphraseConfirmLayout.visibility = if (confirmTwice) View.VISIBLE else View.GONE
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pass = binding.passphraseInput.text?.toString().orEmpty()
                val confirm = if (confirmTwice) binding.passphraseConfirmInput.text?.toString().orEmpty() else null
                val problem = problem(pass, confirm)
                binding.passphraseLayout.error = null
                binding.passphraseConfirmLayout.error = null
                when (problem) {
                    null -> {
                        dialog.dismiss()
                        onPassphrase(pass)
                    }
                    R.string.passphrase_mismatch -> binding.passphraseConfirmLayout.error = context.getString(problem)
                    else -> binding.passphraseLayout.error = context.getString(problem)
                }
            }
        }
        dialog.show()
        return dialog
    }
}
