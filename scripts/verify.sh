#!/usr/bin/env bash
# Portable verification: the same command for a laptop, an agent and CI.
#
#   scripts/verify.sh [--handoff]
#
# Steps (each prints PASS, FAIL or NOT_RUN):
#   unit-tests, lint, assembleDebug   one Gradle run: test lintDebug assembleDebug
#   third-party                       scripts/check_third_party.py
#   diff-check                        git diff --check HEAD
#
# A missing Android SDK makes the Gradle steps NOT_RUN (with the bootstrap
# command), never PASS. Exit status: 0 all PASS, 1 any FAIL, 2 none FAILED but
# something was NOT_RUN. --handoff records the results in this branch's file
# under .agent/handoffs/ (the only writer of its `verified` list).
#
# Environment: VERIFY_GRADLE_ARGS adds Gradle arguments (e.g. "--max-workers=2").
set -uo pipefail

cd "$(dirname "$0")/.."
ROOT="$PWD"
WRITE_HANDOFF=0
for arg in "$@"; do
    case "$arg" in
        --handoff) WRITE_HANDOFF=1 ;;
        -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
        *) echo "unknown argument: $arg" >&2; exit 64 ;;
    esac
done

HEAD_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
DIRTY=""
if [ -n "$(git status --porcelain 2>/dev/null)" ]; then DIRTY=" (+uncommitted changes)"; fi
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

LOG_DIR="$(mktemp -d)"
trap 'rm -rf "$LOG_DIR"' EXIT
RESULTS_TSV="$LOG_DIR/results.tsv"
: > "$RESULTS_TSV"

record() { # check command result detail
    printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" >> "$RESULTS_TSV"
}

# ---------------------------------------------------------------- Android SDK
find_sdk() {
    local c
    for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/android-sdk" "$HOME/Android/Sdk"; do
        if [ -n "$c" ] && [ -d "$c/platforms" ]; then echo "$c"; return 0; fi
    done
    if [ -f local.properties ]; then
        c="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
        if [ -n "$c" ] && [ -d "$c/platforms" ]; then echo "$c"; return 0; fi
    fi
    return 1
}

if SDK="$(find_sdk)"; then
    export ANDROID_HOME="$SDK"
    echo "== gradle: test lintDebug assembleDebug (ANDROID_HOME=$SDK)"
    GRADLE_LOG="$LOG_DIR/gradle.log"
    # --continue so a failing test does not hide lint or assemble results.
    # Screenshot goldens are compared (never rewritten) by the test task.
    GRADLE_ARGS=(--no-daemon --console=plain --continue -Proborazzi.test.verify=true)
    # shellcheck disable=SC2206
    GRADLE_ARGS+=(${VERIFY_GRADLE_ARGS:-})
    if [ -n "${CI:-}" ] || [ -n "${VERIFY_VERBOSE:-}" ]; then
        ./gradlew "${GRADLE_ARGS[@]}" test lintDebug assembleDebug 2>&1 | tee "$GRADLE_LOG"
        gradle_rc=${PIPESTATUS[0]}
    else
        ./gradlew "${GRADLE_ARGS[@]}" test lintDebug assembleDebug > "$GRADLE_LOG" 2>&1
        gradle_rc=$?
        grep -E ' FAILED$|tests? completed|^BUILD|What went wrong|^e: |Lint found|errors?, [0-9]+ warnings?' "$GRADLE_LOG" | head -60 || true
    fi
    task_result() { # gradle task regex (matched against the task path) -> PASS|FAIL|NOT_RUN
        if [ "$gradle_rc" -eq 0 ]; then echo PASS
        elif grep -Eq "^> Task $1 FAILED" "$GRADLE_LOG"; then echo FAIL
        elif grep -Eq "^> Task $1( |$)" "$GRADLE_LOG"; then echo PASS
        else echo NOT_RUN; fi
    }
    detail=""
    [ "$gradle_rc" -ne 0 ] && detail="gradle exited $gradle_rc"
    r_test="$(task_result ':app:test[A-Za-z]*UnitTest')"
    r_lint="$(task_result ':app:lintDebug')"
    r_assemble="$(task_result ':app:assembleDebug')"
    # Any failed test task (debug or release variant) fails the step.
    if [ "$gradle_rc" -ne 0 ] && grep -Eq '^> Task :app:test[A-Za-z]*UnitTest FAILED' "$GRADLE_LOG"; then r_test=FAIL; fi
    record unit-tests "./gradlew test" "$r_test" "$detail"
    record lint "./gradlew lintDebug" "$r_lint" "$detail"
    record assembleDebug "./gradlew assembleDebug" "$r_assemble" "$detail"
    # Nonzero exit that no step above explains (configuration or resolution
    # failure, another task failing) must still fail the run.
    if [ "$gradle_rc" -ne 0 ] && [[ "$r_test $r_lint $r_assemble" != *FAIL* ]]; then
        record gradle "./gradlew test lintDebug assembleDebug" FAIL "gradle exited $gradle_rc; see the output above"
    fi
else
    msg="no Android SDK found; run scripts/bootstrap-android-sdk.sh and export ANDROID_HOME"
    echo "NOT_RUN: $msg"
    record unit-tests "./gradlew test" NOT_RUN "$msg"
    record lint "./gradlew lintDebug" NOT_RUN "$msg"
    record assembleDebug "./gradlew assembleDebug" NOT_RUN "$msg"
fi

# --------------------------------------------------------------- third-party
echo "== third-party inventory"
if command -v python3 >/dev/null 2>&1; then
    if python3 scripts/check_third_party.py; then
        record third-party "python3 scripts/check_third_party.py" PASS ""
    else
        record third-party "python3 scripts/check_third_party.py" FAIL "inventory does not match libs.versions.toml"
    fi
else
    record third-party "python3 scripts/check_third_party.py" NOT_RUN "python3 not installed"
fi

# ---------------------------------------------------------------- diff check
echo "== git diff --check"
if git diff --check HEAD; then
    record diff-check "git diff --check HEAD" PASS ""
else
    record diff-check "git diff --check HEAD" FAIL "whitespace errors or conflict markers"
fi

# ------------------------------------------------------------------- report
echo
echo "verify.sh @ $HEAD_SHA$DIRTY on $BRANCH"
printf '%-14s %-8s %s\n' CHECK RESULT DETAIL
fails=0; notrun=0
while IFS=$'\t' read -r check command result detail; do
    printf '%-14s %-8s %s\n' "$check" "$result" "$detail"
    [ "$result" = FAIL ] && fails=$((fails + 1))
    [ "$result" = NOT_RUN ] && notrun=$((notrun + 1))
done < "$RESULTS_TSV"

if [ "$WRITE_HANDOFF" -eq 1 ]; then
    python3 - "$RESULTS_TSV" "$HEAD_SHA" "$BRANCH" "$DIRTY" <<'PY'
import json, sys, pathlib
tsv, head, branch, dirty = sys.argv[1:5]
path = pathlib.Path(".agent/handoffs") / (branch.replace("/", "-") + ".json")
if not path.exists():
    print(f"handoff: {path} does not exist; create it first (see .agent/handoff.schema.json)")
    sys.exit(0)
data = json.loads(path.read_text())
verified = []
for line in pathlib.Path(tsv).read_text().splitlines():
    check, command, result, detail = (line.split("\t") + ["", "", "", ""])[:4]
    entry = {"check": check, "command": command, "result": result, "commit": head}
    note = (detail + (" " + dirty.strip() if dirty else "")).strip()
    if note:
        entry["detail"] = note
    verified.append(entry)
data["verified"] = verified
data["head_commit"] = head
path.write_text(json.dumps(data, indent=2) + "\n")
print(f"handoff: wrote {len(verified)} results to {path}")
PY
fi

if [ "$fails" -gt 0 ]; then exit 1; fi
if [ "$notrun" -gt 0 ]; then exit 2; fi
exit 0
