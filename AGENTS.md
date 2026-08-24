# AGENTS.md — Timetable execution rules

These rules apply to automated coding agents working anywhere in this
repository. They are the single-app adaptation of the Sovereign-Ops execution
doctrine; Sovereign-Ops remains the umbrella source of truth.

## What this app is

Timetable is a mobile-first, offline booking scheduler for an independent
outcall, transit-dependent operator. It manages appointments (alias, time,
duration, rate, status, screening/deposit flags, freeform location label),
recurring availability windows, and an earned-vs-projected earnings view. It
warns about overlaps and about bookings too close together to travel between.

It holds **no permissions** — no `INTERNET`, no location, no notifications. All
data is local Room storage; nothing leaves the device.

## Repository map

- `app/src/main/java/.../core/` — the scheduling logic: `Money`, `model/`
  (`Booking`, `BookingStatus`, `AvailabilityWindow`), `schedule/`
  (`ConflictDetector`, `Agenda`), `earnings/`. **Pure Kotlin, no Android, fully
  unit-tested.** New rules go here with tests.
- `app/src/main/java/.../data/` — Room entities (primitive rows), DAOs, mappers,
  database, repository, local settings.
- `app/src/main/java/.../ui/` — single activity, bottom-nav fragments (Agenda,
  Availability, Earnings), the booking editor.
- `.github/workflows/build.yml` — CI: unit tests, debug APK, signed release APK
  when keystore secrets are present.

## Scope and authority

1. Follow the owner's current explicit instruction.
2. Read this file and `README.md` before editing.
3. Prefer the lowest-risk reversible implementation. Mark unresolved values
   `OWNER DECISION`, `LIVE VERIFICATION REQUIRED`, or `TODO` and continue.
4. Green CI is mechanical evidence, not semantic proof. Fix failures
   attributable to the current change without waiting for another prompt.

## Security posture — do not weaken without an owner call

- **No permissions.** Adding `INTERNET`, location, calendar/contacts access, or
  cloud backup is a posture change and an owner decision, not a convenience.
- `allowBackup=false`; backup and device-transfer are explicitly excluded.
- Client identifiers are **aliases**, never legal names. Location is a freeform
  label the operator types; the app never resolves, geocodes, or transmits it.

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
- Room migrations are append-only against the tracked schema; validate before
  replacing live data and never report success after a failed write.
- **Never publish a debug build as an operational release.** The signed release
  path exists for exactly this reason.
- CI scripts must not print secrets.

## Validation

```bash
./gradlew test            # the scheduling core — the part that must be correct
./gradlew assembleDebug   # the app compiles
./gradlew assembleRelease # the shipping artifact compiles (signed if keys present)
```

A successful build is not device validation; distinguish the two.

## Git discipline

- Terse commit messages describing the actual diff, without protected literals.
- Stage only files within the intended task scope unless a dependent file (a
  test, a migration, the tracked schema) must change to stay coherent.
- Do not force-push or rewrite published history unless the owner's current
  instruction explicitly requires it.

## Completion checklist

- The requested behavior is implemented, not merely described.
- `./gradlew test` and the relevant assemble task pass, or exact failures named.
- Room schema/migrations stay coherent when entities change.
- No permission or backup posture was silently added.
- No protected identity, location, or contact data was exposed.
