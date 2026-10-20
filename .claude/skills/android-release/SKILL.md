---
name: android-release
description: Build and inspect a signed release APK with scripts/release.sh. Use when asked to cut, check or publish a release build of this app.
---

# android-release

Thin wrapper over `scripts/release.sh`.

- Agents never create, change, print or commit signing keys or keystore
  passwords. Signing material comes from the owner's gitignored
  `keystore.properties` or from CI secrets.
- With no signing material the script stops with `unsigned: not a release`. That
  is the correct outcome in an agent environment; report it, do not work around
  it, and never publish a debug or unsigned build as a release.
- With material present it runs `scripts/verify.sh`, `assembleRelease`, refuses a
  debuggable APK (`aapt2 dump badging`), checks the pinned applicationId, runs
  `apksigner verify --print-certs` on the APK itself, and compares the signer
  SHA-256 with `release/signing-cert.sha256`. If no pin exists it prints the
  digest for the owner to pin; the owner commits the pin, not the agent.
- It writes `release/release-manifest.json` and `release/CHANGELOG.md`
  (gitignored). Report the APK sha256 and cert digest from the manifest.
- `scripts/release.sh --inspect-only <apk>` runs just the APK checks on an
  existing file, for example to confirm a debug APK is refused.
- Real-device install is `NOT_RUN` until the owner tests on the phone.
