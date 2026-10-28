#!/usr/bin/env bash
# Build and inspect a signed release APK. Never produces a "release" from a
# debug or unsigned build, and never creates, changes or prints signing keys.
#
#   scripts/release.sh                  full path (needs release signing material)
#   scripts/release.sh --inspect-only <apk>
#                                       run only the APK checks on an existing file
#
# Signing material comes from a gitignored keystore.properties or from the
# KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD environment
# (the same inputs app/build.gradle.kts and CI use). Passwords are never echoed.
#
# Order:
#   1. signing material present, else stop: "unsigned: not a release"
#   2. scripts/verify.sh passes
#   3. ./gradlew assembleRelease
#   4. APK is not debuggable                  (aapt2 dump badging)
#   5. applicationId equals the pinned value
#   6. apksigner verify --print-certs on the APK itself; compare the signer
#      SHA-256 with release/signing-cert.sha256. No pin yet: the digest is
#      printed for the owner to pin; the script does not create the pin.
#   7. release/release-manifest.json (commit, version, APK sha256, cert digest,
#      tool versions) and release/CHANGELOG.md (git log since the last tag)
set -euo pipefail

cd "$(dirname "$0")/.."

EXPECTED_APPLICATION_ID="com.sovereignops.timetable"
PIN_FILE="release/signing-cert.sha256"
INSPECT_ONLY=""

if [ "${1:-}" = "--inspect-only" ]; then
    INSPECT_ONLY="${2:?usage: release.sh --inspect-only <apk>}"
fi

die() { echo "release.sh: $*" >&2; exit "${2:-1}"; }

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
[ -d "$SDK/build-tools" ] || die "no Android SDK build-tools under $SDK; run scripts/bootstrap-android-sdk.sh" 2
export ANDROID_HOME="$SDK"
BT_DIR="$(find "$SDK/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)"
AAPT2="$BT_DIR/aapt2"
APKSIGNER="$BT_DIR/apksigner"
[ -x "$AAPT2" ] && [ -x "$APKSIGNER" ] || die "aapt2/apksigner missing in $BT_DIR" 2

has_signing_material() {
    if [ -f keystore.properties ] && grep -q '^storeFile=' keystore.properties; then return 0; fi
    [ -n "${KEYSTORE_FILE:-}" ] && [ -f "${KEYSTORE_FILE}" ] && return 0
    return 1
}

if [ -z "$INSPECT_ONLY" ]; then
    if ! has_signing_material; then
        die "unsigned: not a release (no keystore.properties and no KEYSTORE_FILE; agents never create signing keys)" 3
    fi
    scripts/verify.sh || die "verify.sh did not pass; not building a release" 4
    ./gradlew --no-daemon --console=plain assembleRelease
    shopt -s nullglob
    apks=(app/build/outputs/apk/release/*.apk)
    shopt -u nullglob
    [ "${#apks[@]}" -eq 1 ] || die "expected exactly one release APK, found ${#apks[@]}" 5
    APK="${apks[0]}"
else
    APK="$INSPECT_ONLY"
    [ -f "$APK" ] || die "no such file: $APK" 5
fi

# 4. Refuse a debuggable APK.
badging="$("$AAPT2" dump badging "$APK")"
if printf '%s\n' "$badging" | grep -q "application-debuggable"; then
    die "refusing: $APK is debuggable. A debug build is never a release." 6
fi

# 5. Pinned applicationId.
app_id="$(printf '%s\n' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
[ "$app_id" = "$EXPECTED_APPLICATION_ID" ] ||
    die "refusing: applicationId is '$app_id', pinned value is '$EXPECTED_APPLICATION_ID'" 7
version_name="$(printf '%s\n' "$badging" | sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p")"
version_code="$(printf '%s\n' "$badging" | sed -n "s/^package:.* versionCode='\([^']*\)'.*/\1/p")"

# 6. Signature check on the APK itself.
cert_out="$("$APKSIGNER" verify --print-certs "$APK")" ||
    die "refusing: apksigner could not verify $APK (unsigned or tampered)" 8
cert_sha256="$(printf '%s\n' "$cert_out" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1)"
[ -n "$cert_sha256" ] || die "could not read the signer certificate digest" 8
pin_state="unpinned"
if [ -f "$PIN_FILE" ]; then
    pinned="$(tr -d ' \n\r' < "$PIN_FILE" | tr 'A-F' 'a-f')"
    if [ "$pinned" = "$cert_sha256" ]; then
        pin_state="matches-pin"
    else
        die "refusing: signer digest $cert_sha256 does not match $PIN_FILE ($pinned)" 9
    fi
else
    echo "NOTE: $PIN_FILE does not exist. Signer SHA-256 is:"
    echo "  $cert_sha256"
    echo "The owner pins it with: echo $cert_sha256 > $PIN_FILE"
fi

if [ -n "$INSPECT_ONLY" ]; then
    echo "inspect-only OK: $APK applicationId=$app_id version=$version_name ($version_code) cert=$cert_sha256 [$pin_state]"
    exit 0
fi

# 7. Manifest and changelog.
mkdir -p release
apk_sha256="$(sha256sum "$APK" | cut -d' ' -f1)"
commit="$(git rev-parse HEAD)"
last_tag="$(git describe --tags --abbrev=0 2>/dev/null || true)"
gradle_version="$(./gradlew --no-daemon --console=plain --version 2>/dev/null | sed -n 's/^Gradle //p' | head -1)"
python3 - "$APK" "$commit" "$app_id" "$version_name" "$version_code" "$apk_sha256" "$cert_sha256" \
    "$pin_state" "$(basename "$BT_DIR")" "$gradle_version" "$(java -version 2>&1 | head -1)" <<'PY'
import json, sys
apk, commit, app_id, vname, vcode, apk_sha, cert, pin_state, bt, gradle, java = sys.argv[1:12]
manifest = {
    "commit": commit,
    "applicationId": app_id,
    "versionName": vname,
    "versionCode": int(vcode),
    "apk": apk,
    "apk_sha256": apk_sha,
    "signer_cert_sha256": cert,
    "signer_pin": pin_state,
    "tools": {"build_tools": bt, "gradle": gradle, "java": java},
}
with open("release/release-manifest.json", "w") as fh:
    json.dump(manifest, fh, indent=2)
    fh.write("\n")
PY
{
    echo "# Changes since ${last_tag:-the first commit}"
    echo
    if [ -n "$last_tag" ]; then git log --format='- %s (%h)' "$last_tag..HEAD"; else git log --format='- %s (%h)'; fi
} > release/CHANGELOG.md
echo "release OK: $APK sha256=$apk_sha256 cert=$cert_sha256 [$pin_state]"
echo "wrote release/release-manifest.json and release/CHANGELOG.md"
