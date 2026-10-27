# Changelog

## 1.0.0-rc1 (versionCode 2) — 2026-10-27

First release candidate. Everything since v1 (`versionCode 1`, `main` at
`451a71e`).

### Bookings never lost

- **Drafts are kept on the device.** Every change in the booking editor is
  written to the (encrypted) database as a draft. A crash, a killed app, a swipe
  from recents or closing the editor leaves the draft in place; opening the
  editor again picks it up and says so. The Agenda shows the latest draft with
  **Resume** and **Discard**.
- **The editor does not close by accident.** A tap outside it does nothing.
  Back or Cancel with unsaved changes asks **Keep draft**, **Discard** or **Keep
  editing**.
- **Save waits for the write.** The booking is stored and its draft removed in
  one transaction, and the editor closes only after that succeeded. If the
  write fails, the editor stays open with everything still typed and says why.
- A draft of a booking that was deleted meanwhile is kept and saves as a new
  booking.
- The editor reads an existing booking by id and cannot save before it has
  loaded, so a blank form can never overwrite a stored booking. Rotation keeps
  every field.

### Crash fixes

- Leaving the Availability tab or Settings while a number field had focus
  crashed the app. Fixed, and the typed value is now kept.
- A storage error while saving, deleting, loading a list, reading a booking,
  making a backup, restoring or exporting now shows a message instead of
  closing the app.
- Reminder and reboot handling can no longer crash the app in the background.
- A double tap on **+** opened two editors.
- Unreadable stored rows are skipped and counted, never deleted, instead of
  crashing the list.

### New since v1

- Past bookings, a *Needs review* section, and recording an outcome
  (completed, no-show, cancelled). Projected earnings stop counting bookings
  whose time has passed.
- Deleting asks first and offers Undo. Availability windows can be edited, can
  end at midnight, and cannot overlap.
- Month overview.
- Encrypted backup and restore (one passphrase-protected file, format in
  `docs/BACKUP_FORMAT.md`) and a plain `.ics` calendar export.
- Booking reminders (off by default; inexact; no alias on the lock screen).
- Optional app lock (screen lock or strong biometric).
- Encrypted database (SQLCipher). New installs are encrypted; an existing
  database is converted only when you ask, after an encrypted backup.
- Screenshots blocked by default (`FLAG_SECURE`), switchable in Settings.
- Third-party notices in the app (Settings).

### Data and upgrade

- Database schema 1 → 2 adds the `booking_drafts` table only. Bookings,
  availability and settings are untouched (checked by `MigrationTest`).
- Drafts are not part of a backup; a backup holds saved bookings, availability
  and the two scheduling settings.

### Before this becomes 1.0.0 (owner, on the phone)

Each line is `NOT_RUN` until done on a real device with the signed APK:

1. Install over the current app (signed with the same key) and check every
   existing booking is still there.
2. Type a new booking, swipe the app away from recents, reopen: the Agenda
   offers the draft and **Resume** restores every field.
3. Back with changes asks; **Discard** removes the draft; **Save** closes and
   the booking appears.
4. Reminders on: a confirmed booking's reminder arrives; the lock-screen text
   names no alias.
5. App lock on and off; the prompt appears after the timeout.
6. Make an encrypted backup, restore it, and (on an older plaintext install)
   run **Encrypt database** after the backup.
7. `scripts/release.sh` with the release keystore, then pin the printed signer
   digest in `release/signing-cert.sha256`.
