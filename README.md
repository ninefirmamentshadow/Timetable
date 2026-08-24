# Timetable

A mobile-first, **offline** booking scheduler for an independent outcall,
transit-dependent operator. Built on the Sovereign-Ops infrastructure base
(Kotlin / Android, Room, CI-built and sideloaded).

Private repo. Sideloaded. Built in CI — there is no local build step in the
normal workflow.

---

## What it does

**AGENDA** — upcoming bookings grouped by day, each showing alias, time range,
rate, status, and flags (screened / deposit / location label). The header names
the next booking. Tap one to edit; tap **+** to add. The editor warns before it
lets you overbook.

**AVAILABILITY** — recurring weekly windows (the days and hours you take
bookings), plus a **travel buffer**: the minimum gap the app insists on between
two bookings so you can actually get across town. Both feed the conflict check.

**EARNINGS** — **earned** money (completed bookings) and **projected** money
(open inquiries and confirmations) shown separately — never summed into one
misleading total — with an earned-by-week breakdown.

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

- **No permissions at all** — no `INTERNET`, no location, no notifications.
  Appointment data never leaves the device.
- Backups and device transfer are disabled.
- Clients are recorded as **aliases**, never legal names. The location field is
  a freeform label you type; the app never resolves, geocodes, or transmits it.

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
posture (no permissions, alias-first, local-only) and the protected-data
boundary. Sovereign-Ops is the umbrella source of truth.
