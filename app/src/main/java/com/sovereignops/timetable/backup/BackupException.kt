package com.sovereignops.timetable.backup

/**
 * Why a backup could not be written or opened. The messages are safe to show to
 * the user: they never contain the passphrase, key material or file contents.
 *
 * [AuthenticationFailed] covers both a wrong passphrase and a tampered file. They
 * are deliberately indistinguishable, so the error cannot be used as an oracle.
 */
sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The text is not a backup envelope at all (not JSON, wrong shape, unknown format marker). */
    class NotABackup(message: String = "This file is not a backup", cause: Throwable? = null) :
        BackupException(message, cause)

    /** A recognisable envelope that is structurally invalid: bad base64, wrong lengths, extra fields. */
    class Malformed(message: String, cause: Throwable? = null) : BackupException(message, cause)

    /** A newer or unknown envelope version, KDF or cipher. */
    class Unsupported(message: String) : BackupException(message)

    /** The envelope was written by a different application. Refused before any key derivation. */
    class WrongApplication : BackupException("This backup belongs to a different app and was refused")

    /** The passphrase is shorter than [BackupEnvelope.MIN_PASSPHRASE_LENGTH] or not valid text. */
    class WeakPassphrase(message: String) : BackupException(message)

    /** Wrong passphrase or modified file; the two cannot be told apart. */
    class AuthenticationFailed :
        BackupException("Could not unlock the backup. Check the passphrase and try again.")
}
