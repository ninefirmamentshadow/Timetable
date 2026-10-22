# AGENTS.md — Timetable execution rules

These rules apply to automated coding agents working anywhere in this
repository. They are the single-app adaptation of the owner's execution
doctrine.

## What this app is

Timetable is a mobile-first, offline booking scheduler for an independent
outcall, transit-dependent operator. It manages appointments (alias, time,
duration, rate, status, screening/deposit flags, freeform location label),
recurring availability windows, and an earned-vs-projected earnings view. It
warns about overlaps and about bookings too close together to travel between.

It has **no network access** and only the three permissions the owner approved
(see "Owner decisions" below). All data is local Room storage; nothing leaves the
device except an encrypted backup file or a plain calendar export the operator
saves themselves through the system file picker.

## Repository map

- `app/src/main/java/.../core/` — the scheduling logic: `Money`, `model/`
  (`Booking`, `BookingStatus`, `AvailabilityWindow`), `schedule/`
  (`ConflictDetector`, `Agenda`), `earnings/`. **Pure Kotlin, no Android, fully
  unit-tested.** New rules go here with tests.
- `app/src/main/java/.../data/` — Room entities (primitive rows), DAOs, mappers,
  database, repository, local settings.
- `app/src/main/java/.../ui/` — single activity, bottom-nav fragments (Agenda,
  Availability, Earnings), the booking editor.
- `.github/workflows/build.yml` — CI: runs `scripts/verify.sh`, uploads the debug
  APK, and builds a signed release APK when keystore secrets are present.
- `scripts/` — `verify.sh` (the one verification entry point), `release.sh`,
  `check_third_party.py`, `bootstrap-android-sdk.sh`.
- `.agent/` — branch handoff files and their schema. `third_party.json` and
  `THIRD_PARTY_NOTICES.md` — the component inventory.

## Standing directives

- `SOVEREIGN_EXECUTION_PROTOCOL.md` is the owner's default working style for every
  agent and workflow in this repository: execution first, verification alongside
  the work, minimal friction, risk-proportionate intervention, deliverables over
  commentary. Read it in full before starting. It applies unless the owner
  overrides it for a specific task; the security posture and protected data
  boundary below are its §4 boundaries made concrete.
- `OPEN_SOURCE_COMPLIANCE_PROTOCOL.md` governs any third-party code, library or
  text that enters this repository: verify the license from the upstream LICENSE
  at a pinned version, copy only from permissive sources, record every component
  in `third_party.json` and `THIRD_PARTY_NOTICES.md`, and keep notices. It
  supplements the execution protocol and ends with an agent workflow section.

## Scope and authority

1. Follow the owner's current explicit instruction.
2. Read this file and `README.md` before editing.
3. Prefer the lowest-risk reversible implementation. Mark unresolved values
   `OWNER DECISION`, `LIVE VERIFICATION REQUIRED`, or `TODO` and continue.
4. Green CI is mechanical evidence, not semantic proof. Fix failures
   attributable to the current change without waiting for another prompt.

## Security posture — do not weaken without an owner call

- **No `INTERNET`, no location, no calendar or contacts access, no cloud
  backup.** Adding any of them, or any permission not recorded under "Owner
  decisions", is a posture change and an owner decision, not a convenience. After
  adding a dependency, check the merged manifest for permissions it brings.
- `allowBackup=false`; backup and device-transfer are explicitly excluded.
- Client identifiers are **aliases**, never legal names. Location is a freeform
  label the operator types; the app never resolves, geocodes, or transmits it.

## Owner decisions

- **2026-10-20 — permissions.** The owner approved exactly three, and nothing
  else: `POST_NOTIFICATIONS` (requested only when reminders are switched on) and
  `RECEIVE_BOOT_COMPLETED` for booking reminders, and `USE_BIOMETRIC` for the
  opt-in app lock, which arrives through the androidx.biometric manifest merge.
  Reminders use inexact `AlarmManager.setAndAllowWhileIdle`, so no exact-alarm
  permission. The lock-screen notification text carries no alias. Backup and
  export go through the system file picker and need no storage permission.
  (AndroidX core also merges one app-private signature permission,
  `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no other app can
  hold it and it grants nothing, so it is not an addition. `ReminderTest` asserts
  the full requested-permission set against this list.)
- **2026-10-20 — encrypted database.** Approved: SQLCipher
  (`net.zetetic:sqlcipher-android`) through Room's `openHelperFactory`. New
  installs are encrypted; existing installs migrate only when the owner taps
  **Encrypt database**, and only after an encrypted backup was made that session.

## Protected data boundary

Never add, infer, reconstruct, print, or commit the owner's or a client's legal
identity, real physical location, operational contact numbers, credentials, or
signing/recovery secrets. Keep them out of code, fixtures, seed data, logs,
commit messages, and build artifacts. Test fixtures use synthetic aliases and
placeholder locations only — never a real handle, number, or address.

## Code and data rules

- Scheduling and money logic stays deterministic and unit-testable without
  Android or a clock: pass `now` in, never read it inside the core.
- Earned money (`COMPLETED`) and projected money (`INQUIRY`/`CONFIRMED`) are
  never summed into one total — a confirmed future booking is not money in hand.
- Room migrations are append-only against the tracked schema (`app/schemas/`);
  validate before replacing live data and never report success after a failed
  write. Schema changes are additive and ship with a migration test.
- Code that does not depend on this app's types (the backup envelope, the app
  lock, the encrypted-database support) lives in its own package with no
  Timetable types, so it can be reused verbatim.
- Every confirmed, reproducible defect gets a regression test in the same commit.
- **Never publish a debug build as an operational release.** The signed release
  path exists for exactly this reason.
- CI scripts must not print secrets.

## Validation

```bash
scripts/verify.sh [--handoff]   # unit tests, lint, assembleDebug, third-party inventory, git diff --check
./gradlew recordRoborazziDebug  # re-record golden screenshots after an intended UI change
scripts/release.sh              # signed release; stops with "unsigned: not a release" without a keystore
```

`scripts/verify.sh` prints PASS, FAIL or NOT_RUN per step with the HEAD SHA; a
missing Android SDK is NOT_RUN, never a pass. Device and instrumented tests are
not run by it and stay NOT_RUN until the owner tests on the phone. A successful
build is not device validation; distinguish the two. One agent owns an active
branch; update `.agent/handoffs/<branch>.json` at every stop.

## Git discipline

- Terse commit messages describing the actual diff, without protected literals.
- Stage only files within the intended task scope unless a dependent file (a
  test, a migration, the tracked schema) must change to stay coherent.
- Do not force-push or rewrite published history unless the owner's current
  instruction explicitly requires it.

## Completion checklist

- The requested behavior is implemented, not merely described.
- `scripts/verify.sh` passes, or the exact FAIL and NOT_RUN steps are named.
- Room schema/migrations stay coherent when entities change.
- No permission or backup posture was silently added; the manifest permissions
  match "Owner decisions".
- No protected identity, location, or contact data was exposed.
