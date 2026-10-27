# Timetable

A mobile-first, **offline** booking scheduler for an independent mobile,
transit-dependent operator. Built on the Sovereign-Ops infrastructure base
(Kotlin / Android, Room, CI-built and sideloaded).

Public repository. Sideloaded. Built in CI; `scripts/verify.sh` runs the same
checks locally.

---

## What it does

**AGENDA** — *Needs review*, *Upcoming* and *Past and closed* sections, grouped
by day, each booking showing alias, time range, rate, status, and flags
(screened / deposit / location label). The header names the next booking. Tap
one to edit (or, if it ended without an outcome, to mark it completed, no-show
or cancelled); tap **+** to add. The editor warns before it lets you overbook,
and deleting asks first and offers Undo.

**Nothing typed is lost.** Every change in the editor is kept on the device as
a draft, so a crash, a killed app or closing the editor never drops it; the
editor picks it up next time and the Agenda offers **Resume** / **Discard**. A
tap outside the editor does nothing, Back with unsaved changes asks first, and
Save closes the editor only once the booking is actually written.
**Month** opens a month overview (days with bookings are marked); **Settings**
holds backup, export, reminders and privacy options.

**AVAILABILITY** — recurring weekly windows (the days and hours you take
bookings; a window can run to midnight, windows can be edited and cannot
overlap), plus a **travel buffer**: the minimum gap the app insists on between
two bookings so you can actually get across town. Both feed the conflict check.

**EARNINGS** — three figures that are never summed into one: **completed**
(the booked value of completed bookings), **cash recorded** (what you entered as
actually received, on any booking), and **projected** (open inquiries and
confirmations that have not happened yet). A completed booking is not cash: it
counts as cash only once its amount received is recorded, and the screen says how
many completed bookings have none. There is a completed-value-by-week breakdown. An
open booking whose time has passed is counted as *needs review* until you mark it
completed, no-show or cancelled on the Agenda.

## Conflict model

When you save a booking the app checks it against everything already on the
calendar and reports, worst first:

- **Overlap** — a hard clash with an existing booking.
- **Travel buffer** — a warning that two bookings are closer than your buffer
  allows; this is a travel-to-client workflow, and the calendar alone hides that.
- **Outside availability** — the slot falls outside every declared window.

A conflict does not block you — it surfaces, and you decide. The rules live in
`app/src/main/java/com/sovereignops/timetable/core/` as pure, unit-tested Kotlin.

## Privacy posture

- **No `INTERNET` permission and no network code.** Appointment data never
  leaves the device except in a file you choose to save (below).
- Only three permissions, each approved by the owner and recorded in
  `AGENTS.md`: `POST_NOTIFICATIONS` (asked only when you switch reminders on) and
  `RECEIVE_BOOT_COMPLETED` for booking reminders, and `USE_BIOMETRIC` for the
  optional app lock. No location, no contacts, no calendar, no storage.
- Android backup and device transfer are disabled.
- Clients are recorded as **aliases**, never legal names. The location field is
  a freeform label you type; the app never resolves, geocodes, or transmits it.
- **Reminders** (off by default) use inexact alarms, so they may arrive a few
  minutes late. The lock-screen version of a reminder says only "Booking at
  7:00 PM": no alias, no place. The full text shows once the phone is unlocked.
- **Screenshots are blocked** and the recent-apps thumbnail is blank
  (`FLAG_SECURE`), on by default, switchable under Settings.
- **App lock** (off by default): when on, the app asks for your screen lock (PIN,
  pattern, password or a strong biometric) when it opens and after it has been in
  the background for a time you set (30 seconds by default). It is a screen gate
  and uses the phone's own prompt; the lock screen is opaque and covers any open
  dialog. Screenshots stay blocked while it is on.
- **Encrypted database.** The database is encrypted with SQLCipher. The key is 32
  random bytes, wrapped by an Android Keystore key and kept in the app's no-backup
  storage; it is not tied to your screen lock, so reminders still work while the
  phone is locked. **Threat model:** this adds protection against file-copy
  extraction and backup leaks beyond Android's built-in file encryption. It does
  not protect against code running as the app on an unlocked device. New installs
  are encrypted from the start. An existing plaintext database stays plaintext
  until you tap **Encrypt database** in Settings, which is offered only after an
  encrypted backup was made in the same session; the conversion runs at the next
  start, into a copy that is checked row by row before it replaces the original,
  and the old copy is deleted only after that. If anything fails the original is
  left exactly as it was and the reason is shown.
- **Backup** is one passphrase-encrypted file (PBKDF2-SHA256 and AES-256-GCM)
  saved wherever you choose with the system file picker; the format is in
  `docs/BACKUP_FORMAT.md`. A wrong passphrase or a damaged file changes nothing.
  **Calendar export (.ics)** is separate and **not encrypted**; the app says so
  before writing it and leaves rates and notes out.
- **Drafts** of unsaved bookings live in the same database (encrypted with it)
  until you save or discard them, and are included in the encrypted backup.
- **Safety copy before restore.** A restore first seals the current data with
  the backup's passphrase and keeps it on the phone (last three); **Restore a
  safety copy** in Settings brings it back. Timetable and Drafts share this
  convention, the autosave timing and the backup envelope (see
  `docs/BACKUP_FORMAT.md`).

## Releases

`CHANGELOG.md` lists what each version changed and the on-device checks a
release candidate needs before it is called final. `scripts/release.sh` builds
and checks the signed release APK.

## Build

Phone-only workflow:

1. Push a branch or open a PR.
2. GitHub Actions installs JDK 17 and Gradle and runs `scripts/verify.sh` (unit
   and screen tests, lint, `assembleDebug`, the third-party inventory check).
3. Download the `timetable-debug` artifact and sideload it.

### Out of debug — signed release

The debug APK is the fresh-clone floor; the operational artifact is a **signed
release APK**, and a debug build is never published as the operational release.
The release path is gated on repository secrets so a secretless clone stays
green:

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64` | base64 of the release keystore; its presence enables the signed release job |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | signing key alias |
| `KEY_PASSWORD` | signing key password |

Locally the same signing is picked up from a gitignored `keystore.properties`
(`storeFile` / `storePassword` / `keyAlias` / `keyPassword`). Without any of
that the release variant still builds, unsigned.

## Agent execution

Automated agents: read `AGENTS.md` before editing. It carries the security
posture (no network, only the recorded permissions, alias-first, local-only) and the protected-data
boundary.
