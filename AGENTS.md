# AGENTS.md — Timetable execution rules

These rules apply to automated coding agents working anywhere in this
repository. They are the single-app adaptation of the Sovereign-Ops execution
doctrine; Sovereign-Ops remains the umbrella source of truth for doctrine; this file is canonical for agents in this repository.

## Canonical source

This file is the **canonical** instruction source for every coding agent (Claude Code, Codex/ChatGPT, Gemini/Jules, Cursor, Copilot, Grok, others). `CLAUDE.md`, `GEMINI.md`, `.cursor/rules/agents.mdc` and `.github/copilot-instructions.md` are thin pointers to it; edit rules here, never there. `sh scripts/check-agent-pointers.sh` enforces this.

## Owner principles (highest priority after an explicit owner instruction)

1. **Source-of-truth order.** Live GitHub for code, CI and deploys; the owner's file storage for source files and masters; any notes hub is only a sanitized index. When they disagree, trust them in that order and report the conflict.
2. **Fact, inference, speculation.** Label which is which. Never invent test results, build status, or device behaviour. Report the exact commit, tests run, artifact status and blockers (see "Verification report").
3. **Authorization.** No merging, releasing, or publishing without explicit owner authorization. This repository is public: work on a branch and open a PR; do not push directly to the default branch. Never force-push or rewrite pushed history without owner OK.
4. **Reversible, high return.** Prefer small reversible changes with high net return on time. Bug fixes are test-first (failing test, then fix). Never retune heuristics or classifiers against known fixtures to raise scores; document held-out misses honestly.
5. **Data invariants.** No user data is silently discarded. Every transformation is compared against its input, confirmed before it is destructive, and recoverable.
6. **Privacy.** Never search for, reconstruct, or commit legal identity, residence, contact handles, client info, medical, benefits, or financial amounts. Fixtures are synthetic only (e.g. `MERIDIAN`, `Ashford`, `919-555-0142`, `example.invalid`). Persona or voice material is imported by the user at runtime, never bundled. Identity layers stay distinct.
7. **No invented biography.** Use `[TK: …]` placeholders for any biographical detail not supplied by the owner.
8. **Credentials.** Never print or commit secrets, tokens, keystores, or signing passwords. If access is missing, name exactly which access (service, scope, secret name) rather than asking for pasted credentials.
9. **Phase through 2026-12-31: consolidation and conversion.** At most three active bets; no new lanes, features outside the request, or new projects without the owner's say.
10. **Advisory tools never block** the user's own writing, saving, or copying. Warn, don't gate.
11. **Escalate only real, consequential decisions** (irreversible, public, costly, privacy-affecting, posture-changing, or architectural). Proceed on ordinary reversible ones.

Where older text conflicts with these principles, these principles win.

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

## Verification report and definition of done

Every handoff or completion report states, as facts with evidence:

- **Commit:** full SHA and branch; whether pushed; PR link.
- **Tests:** exact commands and results. Never paraphrase a run that did not happen; say NOT_RUN and why (e.g. no Android SDK).
- **Build:** status per artifact; CI result or "CI not run (reason)". GitHub Actions may be unavailable (billing); then give local results.
- **Blockers / missing access:** precise, no credentials.

Status labels, each claimed only with its own evidence: `implemented` · `compiled` · `tested` · `device-tested` · `signed` · `delivered` · `merged` · `published`. A higher label never implies a lower one was checked.

**Done** means: requested change implemented; relevant tests added/updated and run; `sh scripts/check-agent-pointers.sh` passes; docs touched by the change updated; nothing private or secret added; report written with `docs/agents/HANDOFF_TEMPLATE.md`. Merge/release only if authorized.
