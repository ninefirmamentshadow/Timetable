# Timetable

A personal transit planning utility for a rider with a fixed evening deadline.

Private repository. Built and shipped entirely through GitHub Actions — there is no local build step.

## CI secrets

The release build is signed in CI using a keystore stored as a base64-encoded repository secret. Set these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | Your `.keystore`/`.jks` file, base64-encoded (`base64 -w0 release.keystore`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |

Every push runs unit tests, then builds a debug APK, then a signed release APK. Both are uploaded as workflow artifacts (`debug-apk`, `release-apk`) on the **Actions** tab for the run — download the release APK from there.

If the secrets aren't set, the release build still runs but produces an unsigned APK.

## Sideloading

The app is not distributed through any app store. To install it:

1. Download `release-apk` (or `debug-apk`) from the latest green Actions run.
2. Transfer the `.apk` to the device.
3. Enable "Install unknown apps" for the app used to open the file (Settings → Apps → Special app access → Install unknown apps).
4. Open the APK on-device to install.

The app requests no permissions, so there's nothing to grant post-install.
