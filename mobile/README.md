# Streammore Mobile

Native Android mobile client for Streammore, adapted directly from the Android TV client.
It keeps the same authenticated Streammore API, profiles, catalogue, playback, subtitles,
progress/resume, My List, ratings, trailers and Live TV flows.

## Mobile behavior

- Portrait and landscape: compact responsive layout with bottom navigation.
- Landscape keeps the same bottom menu bar as portrait; it does not switch to a top menu.
- Playback controls are visible when playback starts and return when the video surface is tapped.
- Touch controls work on phones; the existing D-pad/focus behavior remains available
  for Android-compatible remote/keyboard input.
- Debug builds use the private LAN backend `http://192.168.3.91:3896`.
- Release builds use `https://streammore.mmcloud.co.za` and do not allow cleartext traffic.

## Build

From this directory:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The test APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected Android phone:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.marco.streammore.mobile/.MainActivity
```

The signed release build is produced with the dedicated mobile release keystore
stored outside the repository. The keystore must be preserved for future updates.
The current release artifact is:

```text
Streammore-Mobile-v1.0.5-release.apk
```

If the signing environment is not already loaded, source the private local signing
file used on this build machine before running `assembleRelease`:

```bash
set -a
. /home/hermes/.config/streammore-mobile/release.env
set +a
./gradlew assembleRelease lintRelease
```

## GitHub auto-update flow

The mobile client checks the published releases in `Marco0300/StreamMore`, selects
the newest non-draft release containing a `Streammore-Mobile-*.apk` asset, verifies
its published SHA-256 digest after download, and opens Android's package installer.
It ignores TV-only releases.

Future mobile releases use tags in this format:

```text
mobile-v1.0.6
```

The GitHub Actions workflow at `.github/workflows/mobile-release.yml` builds and
publishes a signed mobile release when a `mobile-v*` tag is pushed. Configure these
GitHub Actions secrets before pushing a release tag:

```text
STREAMMORE_MOBILE_KEYSTORE_BASE64
STREAMMORE_MOBILE_STORE_PASSWORD
STREAMMORE_MOBILE_KEY_PASSWORD
```

The release keystore is never committed to the repository. Android requires the
same signing identity and a higher version code for future updates.
