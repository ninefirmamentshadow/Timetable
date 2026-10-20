#!/bin/bash
# SessionStart orientation.
#
# Read-only: reports where the repo is and how to
# build and test. Never fails the session.
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

printf '%s\n' '=== Timetable session state ==='

printf '\n-- git --\n'
branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?')"
printf 'branch: %s\n' "$branch"
dirty="$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
printf 'uncommitted paths: %s\n' "$dirty"
git log -1 --format='last commit: %h %s (%cr)' 2>/dev/null || printf 'last commit: (none)\n'

printf '\n-- build & test --\n'
cat <<'NOTE'
Verify:       scripts/verify.sh [--handoff]   (tests, lint, assembleDebug, inventory, diff check)
Unit tests:   ./gradlew test          (scheduling core + Robolectric screenshot harness)
Release APK:  scripts/release.sh      (signed only when keystore material is set)
CI:           .github/workflows/build.yml runs scripts/verify.sh.
NOTE

printf '\n-- reminders --\n'
cat <<'NOTE'
Read AGENTS.md before editing. The scheduling logic lives in core/ (pure Kotlin,
unit-tested, no Android) — put new rules there with tests, not in the UI. This
app has NO network and only the owner-approved permissions recorded in AGENTS.md
(reminders, app lock): appointment data never leaves the device. Client aliases
are handles, never legal names. Never publish a debug build as an operational
release.
NOTE

printf '\n-- standing directives --\n'
cat <<'NOTE'
Read SOVEREIGN_EXECUTION_PROTOCOL.md (execute, verify alongside, minimal friction)
and OPEN_SOURCE_COMPLIANCE_PROTOCOL.md (license-verify and record third-party
components in third_party.json). Run scripts/verify.sh before every push and
report NOT_RUN honestly. Update .agent/handoffs/<branch>.json at every stop.
NOTE
