#!/usr/bin/env bash
# Install the Android SDK pieces the Gradle builds need, without Android Studio.
#
# Downloads Google's command-line tools, verifies the archive against the
# checksum published in Google's own repository manifest (the same manifest
# sdkmanager trusts), then installs a pinned platform and build-tools set.
# Idempotent: an existing install is reused. Prints the ANDROID_HOME to export.
#
# Running this accepts the Android SDK license terms on the operator's behalf,
# exactly as `sdkmanager --licenses` does interactively.
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
PLATFORM="${ANDROID_PLATFORM:-android-35}"
BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-35.0.0}"
REPO_URL="https://dl.google.com/android/repository"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    work="$(mktemp -d)"
    trap 'rm -rf "$work"' EXIT
    curl -fsSL "$REPO_URL/repository2-3.xml" -o "$work/repo.xml"
    # Pick the linux archive of the newest stable cmdline-tools package.
    read -r archive algo sum < <(python3 - "$work/repo.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
best = None
for pkg in root.iter("remotePackage"):
    path = pkg.get("path", "")
    if not path.startswith("cmdline-tools;") or path.endswith("latest"):
        continue
    channel = pkg.find("channelRef")
    if channel is not None and channel.get("ref") != "channel-0":
        continue
    rev = pkg.find("revision")
    version = tuple(int(rev.findtext(k) or 0) for k in ("major", "minor", "micro"))
    for arc in pkg.iter("archive"):
        if (arc.findtext("host-os") or "linux") != "linux":
            continue
        comp = arc.find("complete")
        url = comp.findtext("url")
        cs = comp.find("checksum")
        if best is None or version > best[0]:
            best = (version, url, cs.get("type", "sha1"), cs.text.strip())
if best is None:
    sys.exit("no stable linux cmdline-tools archive in manifest")
print(best[1], best[2], best[3])
PY
)
    curl -fsSL "$REPO_URL/$archive" -o "$work/tools.zip"
    case "$algo" in
        sha1) actual="$(sha1sum "$work/tools.zip" | cut -d' ' -f1)" ;;
        sha-256|sha256) actual="$(sha256sum "$work/tools.zip" | cut -d' ' -f1)" ;;
        *) echo "unknown checksum type: $algo" >&2; exit 1 ;;
    esac
    if [ "$actual" != "$sum" ]; then
        echo "checksum mismatch for $archive: expected $sum, got $actual" >&2
        exit 1
    fi
    echo "verified $archive ($algo $sum)"
    mkdir -p "$SDK/cmdline-tools"
    python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$work/tools.zip" "$work/x"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$work/x/cmdline-tools" "$SDK/cmdline-tools/latest"
    chmod +x "$SDK/cmdline-tools/latest/bin/"*
fi

yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" \
    "platforms;$PLATFORM" "build-tools;$BUILD_TOOLS" "platform-tools"
echo "export ANDROID_HOME=$SDK"
