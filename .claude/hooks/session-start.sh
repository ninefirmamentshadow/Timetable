#!/bin/bash
# SessionStart orientation.
#
# Adapted from Sovereign-Ops. Read-only: reports where the repo is and how to
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
Unit tests:   ./gradlew test          (the scheduling core — conflict, buffer, earnings)
Debug APK:    ./gradlew assembleDebug
Release APK:  ./gradlew assembleRelease   (signed only when keystore material is set)
CI:           .github/workflows/build.yml runs tests + both APK paths.
NOTE

printf '\n-- reminders --\n'
cat <<'NOTE'
Read AGENTS.md before editing. The scheduling logic lives in core/ (pure Kotlin,
unit-tested, no Android) — put new rules there with tests, not in the UI. This
app holds NO permissions (no INTERNET, no location): appointment data never
leaves the device. Client aliases are handles, never legal names. Never publish
a debug build as an operational release.
NOTE
