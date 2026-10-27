# Changelog

## 1.0.0-rc.1 (versionCode 2) — 2026-10-27

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
- **Works like Drafts.** Typing is autosaved the way the Drafts app does it:
  coalesced 400 ms after you stop, and written at once when the editor goes to
  the background or closes.
- **Drafts are in the backup.** The encrypted backup (payload schema 2) carries
  unsaved drafts too, and a restore replaces them together with the bookings, so
  a kept draft can never attach to a different booking. Schema 1 backups still
  restore.
- **Safety copy before every restore.** As in Drafts, the current data
  (drafts included) is sealed with the backup's passphrase and kept on the phone
  before a restore replaces it; the last three are kept, and **Settings → Restore
  a safety copy** brings one back. If the copy cannot be written, nothing is
  restored.
- The editor reads an existing booking by id and cannot save before it has
  loaded, so a blank form can never overwrite a stored booking. Rotation keeps
  every field.

### Money that is true

- **Completed is not cash.** A booking gets an optional **Received** amount.
  Earnings shows **Completed** (booked value of completed bookings), **Cash
  recorded** (only amounts entered as received, on any booking, a no-show fee
  included) and **Projected**, never added together, and says how many
  completed bookings have no payment recorded. Existing bookings read as "not
  recorded"; nothing is assumed paid.

### Scheduling checks

- A cancelled or no-show booking no longer raises conflict warnings when you
  edit it (it holds no slot).
- Conflict warnings come out in a fixed order (worst first, then by time).
- New tests for touching and nested bookings, zero and exact buffers, buffers
  across midnight, overnight bookings, daylight-saving nights (wall-clock),
  changed availability and invalid input.

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

- Database schema 1 → 2 adds the `booking_drafts` table; 2 → 3 adds the
  nullable amount-received columns. Bookings, availability and settings are
  otherwise untouched (checked by `MigrationTest`, from every committed version).
- Backup payload schema 1 → 2 adds `bookingDrafts` and the optional amount
  received; schema 1 files are still read. A pre-restore safety copy is kept (last three, app-private).

### Before this becomes 1.0.0 (owner, on the phone)

Each line is `NOT_RUN` until done on a real device with the signed APK:

1. Install over the current app (signed with the same key) and check every
   existing booking is still there.
2. Type a new booking, swipe the app away from recents, reopen: the Agenda
   offers the draft and **Resume** restores every field.
3. Back with changes asks; **Discard** removes the draft; **Save** closes and
   the booking appears.
3a. Make a backup with a draft open, restore it: the confirm dialog names the
   draft; afterwards **Restore a safety copy** lists one copy and restores the
   data from before.
4. Reminders on: a confirmed booking's reminder arrives; the lock-screen text
   names no alias.
5. App lock on and off; the prompt appears after the timeout.
6. Make an encrypted backup, restore it, and (on an older plaintext install)
   run **Encrypt database** after the backup.
7. `scripts/release.sh` with the release keystore, then pin the printed signer
   digest in `release/signing-cert.sha256`.
