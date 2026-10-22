# Timetable

A mobile-first, **offline** booking scheduler for an independent outcall,
transit-dependent operator. Built in Kotlin / Android with Room,
CI-built and sideloaded.

Public repository. Sideloaded. Built in CI; `scripts/verify.sh` runs the same
checks locally.

---

## What it does

**AGENDA** — *Needs review*, *Upcoming* and *Past and closed* sections, grouped
by day, each booking showing alias, time range, rate, status, and flags
(screened / deposit / location label). The header names the next booking. Tap
one to edit (or, if it ended without an outcome, to mark it completed, no-show
or cancelled); tap **+** to add. The editor warns before it lets you overbook,
keeps what you typed through rotation, and deleting asks first and offers Undo.
**Month** opens a month overview (days with bookings are marked); **Settings**
holds backup, export, reminders and privacy options.

**AVAILABILITY** — recurring weekly windows (the days and hours you take
bookings; a window can run to midnight, windows can be edited and cannot
overlap), plus a **travel buffer**: the minimum gap the app insists on between
two bookings so you can actually get across town. Both feed the conflict check.

**EARNINGS** — **earned** money (completed bookings) and **projected** money
(open inquiries and confirmations that have not happened yet) shown separately
— never summed into one misleading total — with an earned-by-week breakdown. An
open booking whose time has passed is counted as *needs review* until you mark it
completed, no-show or cancelled on the Agenda.

## Conflict model

When you save a booking the app checks it against everything already on the
calendar and reports, worst first:

- **Overlap** — a hard clash with an existing booking.
- **Travel buffer** — a warning that two bookings are closer than your buffer
  allows; this is an outcall workflow, and the calendar alone hides that.
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

## Build

Phone-only workflow:

1. Push a branch or open a PR.
2. GitHub Actions installs JDK 17 and Gradle, runs `./gradlew test`, and builds
   `assembleDebug`.
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
